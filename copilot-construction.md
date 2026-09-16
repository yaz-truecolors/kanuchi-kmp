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

## 8. このドキュメントの更新方針

実装を進める中で新しく分かった「ハマりどころ」「決めた規約」は、都度このファイルに追記すること。
`docs/requirements.md` は要件・設計の「why」を記録する場所、このファイルは実装者向けの
「how / 注意点」を記録する場所、という役割分担で運用する。
