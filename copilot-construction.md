# Copilot向け開発ガイド (copilot-construction.md)

このドキュメントは、本リポジトリでコーディングエージェント（GitHub Copilot等）が作業する際に
一貫した設計方針・規約を守るためのガイドです。人間の開発者にとっても規約書として機能します。

背景・要件の全体像は [docs/requirements.md](docs/requirements.md) を参照してください。
このファイルはそれを踏まえた「実装時の具体的な決め事・注意点」を記録します。
PR作成〜レビュー対応の手順は [AGENTS.md](AGENTS.md) を参照してください。

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
  - **KMPでは detekt の既定 `source` がソースセットを拾わない（ハマりどころ）。** detekt Gradle プラグインの
    既定 `source` は `src/main/java` / `src/main/kotlin` / `src/test/java` / `src/test/kotlin` のJVMレイアウトで、
    KMPの `src/commonMain/kotlin` / `src/commonTest/kotlin` / `src/wasmJsMain/kotlin` 等は対象外。そのため
    `check` から呼ばれる `detekt` タスクが `NO-SOURCE` になり、CIが成功していても何も解析されていなかった。
  - 対処として、ルート `build.gradle.kts` の `subprojects` ブロックで `detekt { source.setFrom(fileTree("src") { include("*/kotlin/**/*.kt", ...) }) }`
    を設定し、全モジュールの `src/<ソースセット名>/kotlin` を一括で解析対象にしている（main系・test系とも。
    ソースセットを追加しても自動で追従する）。
  - `build/` 配下の生成コード（Compose Resources の `Res.kt` 等）は解析対象外。`detektMetadataMain` /
    `detektWasmJsMain` 等のソースセット別タスクは生成ディレクトリそのものを source ルートに持つため
    `"**/build/**"` のような相対パターンでは除外できず、`Detekt` タスクに絶対パス（`layout.buildDirectory` 配下か）
    で判定する `exclude { ... }` を設定している。
  - detekt 関連の設定を変えたら `./gradlew detekt --rerun-tasks` を実行し、各モジュールの `detekt` タスクが
    **`NO-SOURCE` になっていないこと**を必ず確認する（ルートプロジェクトの `:detekt` はソースを持たないので `NO-SOURCE` で正常）。
  - `@Composable` 関数の PascalCase 命名は detekt 側でも `FunctionNaming.ignoreAnnotated: ["Composable"]` で許可している。
  - ルール違反は原則コード側を直す。ルールがプロジェクトの設計と衝突する場合のみ、理由をコメントに残して
    `detekt.yml` を調整するか、該当箇所に限定して `@Suppress` する（例: `SupabaseAuthRepository` は
    「失敗は必ず `Result` で返す」契約のため `Exception` を一括捕捉しており、`TooGenericExceptionCaught` のみ抑制）。
    baseline ファイルでの一括抑制は使わない。
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
- ローカル開発でChromeがない環境では `presentation` モジュールのブラウザテストは実行できない
  （Karmaが `ChromeHeadless` を起動できず `Errors occurred during launch of browser for testing.` で失敗する）。
  Chromeを標準の場所に入れていない場合は、環境変数 `CHROME_BIN` にChrome（Chrome for Testing等でも可）の
  実行ファイルのパスを指定すれば実行できる。
  `domain` / `data` の変更検証には `./gradlew :domain:wasmJsNodeTest` のようにNode.jsテストを使うこと。
  なお、テストファイルが1つもないモジュールのテストタスクは `SKIPPED` になるため、その場合はChromeが無くても失敗しない。
- CI（`.github/workflows/ci.yml`）では `browser-actions/setup-chrome` でChromeをセットアップしてから
  テストを実行する。ローカルで再現できない場合はこのステップの有無を疑う。
- **CIと同じ検証はルートの集約タスク `./gradlew verify` で1コマンドで実行できる。** PR作成前に必ず実行して成功させること。
  中身は「全プロジェクトの `check`（= `ktlintCheck` + `detekt` + `allTests`）」＋ `:app-wasmjs:wasmJsBrowserDistribution`。
  CIの `build-lint-test` ジョブも `./gradlew verify --continue` を実行しているので、検証内容を変えたい場合は
  `ci.yml` ではなくルート `build.gradle.kts` の `verify` タスクを変更する（ローカルとCIの差異を作らないため）。
  `check` がすでに lint とテストを含むので、`verify` に `ktlintCheck` 等を個別に足して重複させないこと。
- `main` へのマージ時は追加でGitHub Pagesへの自動デプロイが走る。
- **`main` はルールセット（main-protect）で保護されており、直接pushできない。** 変更は必ずPR経由でマージする
  （承認数は0件でよいが、ステータスチェック `build-lint-test` の成功が必須。最新 `main` との同期は不要）。
  必須チェックはジョブ名で照合されるため、**`ci.yml` のジョブ名 `build-lint-test` を変更すると必須チェックが
  永久に満たされずPRがマージできなくなる。** ジョブ名を変える必要がある場合は、先にルールセット側の
  必須チェック名を変更すること。

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
- **アカウント作成は招待制（招待リスト＋Before User Created Hook）。**
  adminが `public.invitations` にメールアドレスを登録し、本人の初回マジックリンク要求時に
  アカウントが作成される。未招待メールアドレスの拒否はSupabase Authの Before User Created Hook
  （`public.hook_before_user_created`、`supabase/migrations/*_invitations_and_signup_hook.sql`）で
  **サーバー側に強制している**。クライアントの `OTP.Config.createUser` はこの方式では `true` にする
  （`false` だと招待済みユーザーが初回ログインできない）。「未招待の拒否をクライアント設定で
  実現しようとしない」こと。クライアントは改ざん可能であり、防御にならない。
  - Hookは拒否時に `{"error": {"http_code": 403, "message": "email_not_invited"}}` を返す。
    Supabase Authはこれを `error_code: "unknown"`・`msg: "email_not_invited"` のHTTP 403として
    クライアントに返し、supabase-ktでは `AuthRestException.errorDescription == "email_not_invited"` になる。
    data層（`SupabaseAuthRepository`）はこれを `EmailNotInvitedException` に変換する。
    **SQL側のメッセージとKotlin側の定数 `EMAIL_NOT_INVITED_HOOK_MESSAGE` は必ず一致させること。**
    Hookのmessageはそのままクライアントに返るため、内部情報を含めないこと。
  - あわせて `AuthErrorCode.SignupDisabled`（Supabase側で新規登録自体がOFFの場合）と
    `AuthErrorCode.OtpDisabled`（`createUser = false` 時の未登録）も同じ例外に変換している。
  - **Hookの有効化はマイグレーションでは行えず、Supabaseダッシュボードでの手動設定が必要**
    （Authentication → Hooks）。`supabase/config.toml` の `[auth.hook.before_user_created]` は
    ローカル環境（`supabase start`）専用。本番の手順は `supabase/README.md` を参照。
    Hookが無効なまま「Allow new users to sign up」をONにすると、誰でもアカウントを作れてしまう。
  - HookはSupabase Studioの「Invite user」やAdmin API経由のユーザー作成でも実行される。
    Studioから手動でユーザーを追加する場合も、先に `invitations` への登録が必要。
  - Hook関数は `SECURITY DEFINER` で `invitations` を参照し（`supabase_auth_admin` 用のRLSポリシーを
    追加しない）、`anon` / `authenticated` からの `EXECUTE` 権限は必ず剥奪する（剥奪しないと
    Data APIの `rpc` 経由で呼び出せてしまう）。
  - ログイン画面は「入力したメールアドレスが招待済みかどうか」を区別できるエラーを返す。
    これは**意図的に許容しているトレードオフ**である（原理的に避けられないわけではない）。
    社内チーム向けツールであり、入力ミス・未招待をその場で本人に伝えるUXを優先した。
    なお、UIの文言を統一しても、Supabase Auth APIのレスポンス自体（HTTP 200 / 403）で区別できるため、
    完全に防ぐにはAuth APIの前にサーバー側の中継エンドポイントを置いてレスポンスを正規化する必要がある
    （Edge Function等が必要になるため採用していない）。
  - Edge Function + Admin API（`inviteUserByEmail`）方式は、TypeScript/Deno・secret key管理・
    デプロイ経路の追加が必要になるため採用していない（`docs/requirements.md` 5節）。

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

## 10. UI実装規約（フォント・文言・アクセシビリティ）

- **文言・テキスト定数は必ず `presentation/src/commonMain/composeResources/values/strings.xml` に
  集約する。** Composable/ViewModel内にJapanese文言をハードコードしないこと。
  - Composable内: `stringResource(Res.string.xxx)` （プレースホルダーを含む場合は
    `stringResource(Res.string.xxx, arg1, arg2, ...)`。`strings.xml`側は `%1$s` 形式で定義する）
  - Composable以外（ViewModel等のsuspend関数内）: `getString(Res.string.xxx)`
    （`org.jetbrains.compose.resources.getString`、非Composable向けのsuspend版）
  - 新しい画面を追加する際は、その画面用のキーを `strings.xml` に追記してから実装すること。
    キー命名は `<画面名>_<用途>` 形式（例: `login_email_label`）で統一する。
  - `data`/`domain`層はUI表示用の文言を一切持たない。エラー等で「具体的な理由を提示できない」場合は、
    ドメイン層に無メッセージのマーカー例外（例: `GenericAuthFailureException`）を定義し、
    `presentation`層がそれを`strings.xml`管理下の汎用メッセージにマッピングする
    （実例: `LoginViewModel.onSendMagicLinkClicked()`）。外部システム（Supabase等）が返す
    エラー説明文（例: `AuthRestException.errorDescription`）は当該システムの言語でそのまま
    表示してよく、centralization対象の「自前の文言」には含めない。
- **フォントは Noto Sans JP に統一する。** `presentation/src/commonMain/composeResources/font/noto_sans_jp.ttf`
  （SIL Open Font License 1.1。ライセンス全文は `presentation/licenses/NotoSansJP-OFL.txt`）を
  `presentation/src/commonMain/kotlin/.../presentation/theme/KanuchiTheme.kt` の `KanuchiTheme` 経由で
  `MaterialTheme` の `Typography` 全スタイルに適用している。画面のルートは必ず
  `MaterialTheme { ... }` ではなく `KanuchiTheme { ... }` でラップすること
  （現状は `app-wasmjs/.../Main.kt` の1箇所のみ）。
  - 元ファイルは Google Fonts (`google/fonts` リポジトリ) の可変フォント
    (`ofl/notosansjp/NotoSansJP[wght].ttf`, 約9.5MB) から `fonttools` の
    `varLib.instancer.instantiateVariableFont(font, {"wght": 400}, updateFontNames=True)` で
    Regularウェイトの静的インスタンスを切り出したもの（約5.5MB）。可変軸を持たせても
    本アプリでは複数ウェイトを使い分けていないため、静的インスタンス化してファイルサイズを削減した。
  - フォントファイルは `composeResources/font/` 直下にはフォントファイル (ttf/otf/ttc) 以外を
    置かないこと。ライセンステキスト等の付随ファイルはリソース生成の対象外である
    `presentation/licenses/` に置く。
- **アクセシビリティ対応（スクリーンリーダー向け `Modifier.semantics { ... }`、
  `liveRegion` 等）は行わない方針。** 実装しないだけでなく、既存コードにも追加しないこと。
  過去のPRレビュー（Copilotコードレビュー）でこの種の指摘を受けることがあるが、
  本プロジェクトの方針として意図的に対応しないと決めているため、その指摘は採用しない
  （必要であれば本ファイルの当該記述を根拠として説明する）。
- **動作確認はGoogle Chromeを基準とする。** ローカルでの手動ブラウザ確認、CIの
  `browser-actions/setup-chrome`、いずれもChromeを使う。他ブラウザでの見た目・挙動の
  差異は許容し、個別対応しない。

## 11. このドキュメントの更新方針

実装を進める中で新しく分かった「ハマりどころ」「決めた規約」は、都度このファイルに追記すること。
`docs/requirements.md` は要件・設計の「why」を記録する場所、このファイルは実装者向けの
「how / 注意点」を記録する場所、という役割分担で運用する。
