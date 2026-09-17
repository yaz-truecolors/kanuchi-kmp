# Copilot向け開発ガイド (copilot-construction.md)

このドキュメントは、本リポジトリでコーディングエージェント（GitHub Copilot等）が作業する際に
一貫した設計方針・規約を守るためのガイドです。人間の開発者にとっても規約書として機能します。

背景・要件の全体像は [docs/requirements.md](docs/requirements.md) を参照してください。
このファイルはそれを踏まえた「実装時の具体的な決め事・注意点」を記録します。

## 1. アーキテクチャ原則

Clean Architecture + DDD（戦術パターン）を採用しています。**依存の方向は常に外側→内側**
（`app-wasmjs` → `presentation` / `data` → `domain`）。`domain` は他のどのモジュールにも依存しません。

- 新しいビジネスロジックは必ず `domain` に置く。UI都合やSupabase都合のロジックを `domain` に混ぜない。
- `domain` の Repository はインターフェースのみ。実装は `data` に置く。
- Excelの数式（稼働時間計算・過不足計算・按分計算など）に相当するロジックは `domain` の
  Domain Service（例: `WorkingHoursCalculator`）として実装し、DBには保存せず都度計算する。
- Value Objectは不変・自己検証（invalid stateを作れないコンストラクタ/ファクトリ）にする。

## 2. モジュールと依存関係

```
app-wasmjs -> presentation -> domain
app-wasmjs -> data -> domain
app-wasmjs -> domain
```

- `domain` / `data`：ブラウザAPIに依存しないので `wasmJs { nodejs() }` でテストする（軽量・Chrome不要）。
- `presentation`：Compose UIを含むため `wasmJs { browser() }` + `binaries.executable()` が必要
  （Compose UIテストの仕組み上、実行可能バイナリが要求される。外すとビルドエラーになる）。
- `app-wasmjs`：最終成果物。`binaries.executable()` を持つのはこのモジュールが主目的だが、
  `presentation` も上記の理由で保持している。

## 3. コード規約

- **命名・フォーマット**：ktlint。@Composable関数のPascalCase命名は `.editorconfig` で許可済み
  （`ktlint_function_naming_ignore_when_annotated_with = Composable`）。
- **静的解析**：detekt。設定は `config/detekt/detekt.yml`。
- **生成コードの扱い**：Compose Multiplatformのリソースジェネレータ等、`build/` 配下に生成される
  Kotlinコードは lint 対象外にしている（`.editorconfig` の `[**/build/**/*.{kt,kts}]` セクションで
  `ktlint_standard = disabled`）。Gradle側の `ktlint { filter { exclude(...) } }` はKMPの
  ソースセット単位タスクには効かなかったため、EditorConfigベースの無効化を採用している。
  同種の問題に遭遇したら、まずここを疑うこと。
- 新規モジュールを追加する場合も、`build.gradle.kts` で `alias(libs.plugins.ktlint)` /
  `alias(libs.plugins.detekt)` が `subprojects` ブロック経由で自動適用されるので個別追加は不要。

## 4. 依存関係・バージョン管理

- すべてのバージョンは `gradle/libs.versions.toml`（Version Catalog）で一元管理する。
  `build.gradle.kts` にバージョン文字列を直接書かない。
- BOM（Bill of Materials）を使う依存（例: Supabase）は、KMPの `sourceSets.xxx.dependencies` ブロック内では
  `implementation(platform(libs.xxx.bom))` が使えない（`platform()` はDependencyHandler拡張関数で
  KotlinDependencyHandlerには無いため）。代わりに `implementation(project.dependencies.platform(libs.xxx.bom))`
  と書くこと。

## 5. wasmJsターゲット特有の注意点

- Kotlin 2.4.20時点で `ExperimentalWasmDsl` は `org.jetbrains.kotlin.gradle.ExperimentalWasmDsl`
  （`org.jetbrains.kotlin.gradle.targets.js.dsl` 配下ではない）。パッケージ変更に注意。
- `wasmJs` ターゲットのモジュール名は `moduleName = "..."` ではなく
  `outputModuleName.set("...")`（`Property<String>`）で設定する。
- `compose.runtime` / `compose.foundation` / `compose.material3` / `compose.ui` などの
  アクセサはdeprecated警告が出るが、ビルド自体は失敗しない（Compose Multiplatform 1.12時点）。
  警告を消したい場合は個別GAV座標に置き換える。
- Compose Resources（`compose.components.resources` / `Res.kt` 自動生成）は、実際にリソースファイル
  （画像・文字列等）を使うタイミングで追加すること。未使用のまま依存を足すと空の `Res.kt` が
  生成され続け、意味のない生成物が増える。
- `kotlinx.browser`（`document` など）を使う場合は `kotlinx-browser` ライブラリを明示的に追加する
  必要がある（wasmJsでは標準ライブラリに同梱されていない）。
- `app-wasmjs/src/wasmJsMain/resources/index.html` の `<script>` タグには
  **`type="module"` が必須**。付け忘れると、ブラウザで実行した際に
  `SyntaxError: Cannot use 'import.meta' outside a module` が発生し、アプリが
  真っ白のまま何も描画されない（Kotlin/Wasmの出力がESモジュール前提のため）。
  `./gradlew build` や `ktlintCheck`/`detekt` はこの種のランタイムエラーを検知できないため、
  UIに関わる変更をしたら必ず実ブラウザ（またはPuppeteer等のヘッドレスブラウザ）で
  「実際に描画されるか」を確認すること。
- Compose for Web/Wasmは `<canvas>` に直接描画するため、`document.querySelectorAll('input')`
  等のDOM検査では要素を検出できない。UI検証はスクリーンショット比較や、要素の推定座標への
  クリック/キー入力シミュレーションで行う。
- `wasmJsBrowserDevelopmentRun`（webpack-dev-server経由）はコンテンツキャッシュや
  ライブリロードの都合でリソース変更が反映されないことがある。挙動を疑ったら
  `wasmJsBrowserDistribution` の成果物 (`build/dist/wasmJs/productionExecutable`) を
  `python3 -m http.server` 等で直接静的配信して確認する方が確実。

## 6. テスト・CI

- テスト範囲は domain + data + ViewModel（presentation）のロジックまで。UI描画テストはスコープ外。
- ローカル開発でChromeがない環境では `presentation` モジュールのブラウザテストは実行できない。
  `domain` / `data` の変更検証には `./gradlew :domain:wasmJsNodeTest` のようにNode.jsテストを使うこと。
- CI（`.github/workflows/ci.yml`）では `browser-actions/setup-chrome` でChromeをセットアップしてから
  テストを実行する。ローカルで再現できない場合はこのステップの有無を疑う。
- PRでは `ktlintCheck` → `detekt` → `allTests` → `wasmJsBrowserDistribution` の順にCIが実行される。
  `main` マージ時は追加でGitHub Pagesへの自動デプロイが走る。

## 7. Supabase連携時の注意（今後の実装で参照）

- 認証はマジックリンクのみ。パスワード認証・OAuthは実装しない。
- RLS（Row Level Security）前提の設計。`data` 層のRepository実装はRLSに違反しないクエリになっているか
  必ず確認する（本人データのみ／adminは全件、等）。
- SupabaseのURL・anonキーはRLSで保護される前提の公開可能な値として扱う。秘匿すべき鍵
  （service_role key等）は絶対にクライアントコードに埋め込まない。
- Supabaseは2026年末までに `anon`/`service_role` キーを廃止予定。新規実装では
  publishable key（`sb_publishable_...`）/ secret key（`sb_secret_...`）方式を使う。
  `supabase-kt` の `createSupabaseClient()` は単純に文字列キーを渡すだけなので、
  どちらの方式でも実装コードの変更は不要。
- `profiles.role` の変更を「adminのみ許可」にするトリガー（`enforce_role_change_permission`）は、
  **Postgresの実行ロールが `authenticated` の場合のみ**チェックするように実装すること。
  無条件でチェックすると、運用開始時に「最初のadminをSupabase管理画面から手動設定する」
  という初期セットアップ手順自体がブロックされてしまう（誰もadminがいない状態では
  `is_admin()` が常に false になるため、直接DB接続からの昇格も拒否されてしまう）。
  `current_setting('role', true) = 'authenticated'` で判定し、SQL Editor 等の直接DB接続
  （実行ロールが `postgres` 等）はチェック対象外にする。
- `profiles` テーブルのRLSポリシー内で「自分がadminか」を判定する際は、素朴に
  `exists (select 1 from profiles where id = auth.uid() and role = 'admin')` と書くと
  無限再帰（自分自身のテーブルのRLSを評価するために自分自身のRLSを再評価する）になり得る。
  `SECURITY DEFINER` な `is_admin()` 関数として切り出し、テーブル所有者権限で実行することで
  再帰を回避している。
- RLSポリシー・トリガーはSupabase実機に適用する前に、ローカルのDocker上のPostgresで
  検証すること。`auth.users` / `auth.uid()` / `auth.role()` を模した最小限のシムを用意し、
  `SET LOCAL ROLE authenticated; SET LOCAL "request.jwt.claim.sub" = '<uuid>';` で
  「特定ユーザーとしてログインした状態」を再現できる（PostgRESTが実際のリクエストごとに
  行っているセッション変数の設定を模している）。詳細は `supabase/README.md` を参照。
- **supabase-ktの例外をそのままUIに表示しない。** `AuthRestException` 等の `message`/`toString()`
  にはAuthorizationヘッダーを含む生のHTTPレスポンス詳細が含まれており、そのまま
  `errorMessage`としてUIに出すと内部情報が漏れる（実際に発生した事故: `LoginViewModel`が
  `error.message`をそのまま表示していたところ、画面に`Headers: {Authorization=[Bearer sb...`
  が表示されてしまった）。data層のRepository実装で例外を捕捉し、`AuthRestException.errorDescription`
  （Supabase Authが提供するユーザー向け説明文）か、汎用的な日本語メッセージのどちらかに
  変換してから`Result.failure`に包むこと。`AuthRepository`インターフェース側のKDocに
  「失敗時のmessageはUIにそのまま表示してよい（実装側が安全性を保証する）」という契約を明記している。
- コルーチン内で例外を`catch (e: Exception)`する際は、`kotlinx.coroutines.CancellationException`
  を先に`catch`して再送出すること。握りつぶすと構造化された並行処理のキャンセルが正しく伝播しない。

## 9. Supabase マイグレーション運用

- マイグレーションSQLは `supabase/migrations/` に配置し、ファイル名は
  `<タイムスタンプ>_<内容>.sql` の昇順で適用順を表す（Supabase CLI の規約に準拠）。
- `main` へのマージ時、GitHub Actions (`.github/workflows/supabase-deploy.yml`) が
  `supabase db push` を実行し、未適用のマイグレーションを自動的に本番へ反映する。
  手動でSupabase StudioのSQL Editorに貼り付ける必要はない（緊急時・CI障害時の
  フォールバックとしては引き続き可能）。
- このCIには `SUPABASE_ACCESS_TOKEN` / `SUPABASE_DB_PASSWORD` / `SUPABASE_PROJECT_ID` の
  3つのGitHub Actions Secretsが必要（リポジトリ管理者が事前に登録する。詳細は
  `supabase/README.md`）。いずれも強い権限を持つ秘密情報のため、コード・チャット・
  ログに絶対に出力しないこと。
- `SUPABASE_ACCESS_TOKEN` には有効期限（Expiration）を設定する運用にしている。
  期限切れになるとCIが認証エラーで失敗するため、更新手順を `supabase/README.md`
  （「`SUPABASE_ACCESS_TOKEN` の有効期限切れ・更新手順」節）に記載している。
  CIが原因不明で失敗した場合は、まずこのトークンの期限切れを疑うこと。
- `supabase/config.toml` はローカル開発 (`supabase start`) 用の設定。ダッシュボード側の
  Data API設定（`Automatically expose new tables` 等）と値を一致させておくこと
  （`auto_expose_new_tables = false` に設定済み）。
- 新しいテーブルを追加する際は、以下を必ずセットで行うこと（1つでも欠けると
  「テーブルはあるがAPI経由で一切アクセスできない」「RLSが無効なまま公開される」等の
  事故につながる）：
  1. `alter table ... enable row level security;`
  2. `grant select/insert/update/delete on ... to authenticated;`
     （`anon`には付与しない。本プロジェクトはマジックリンク認証必須のため）
  3. 用途に応じた `create policy ...`

## 10. このドキュメントの更新方針

実装を進める中で新しく分かった「ハマりどころ」「決めた規約」は、都度このファイルに追記すること。
`docs/requirements.md` は要件・設計の「why」を記録する場所、このファイルは実装者向けの
「how / 注意点」を記録する場所、という役割分担で運用する。
