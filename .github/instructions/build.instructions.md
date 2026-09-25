---
applyTo: "**/*.gradle.kts,gradle/**,gradle.properties,config/detekt/**,.editorconfig,renovate.json"
---

# Gradle・依存関係・wasmJs ビルド設定の規約

Gradle ビルドスクリプト（`**/*.gradle.kts`）、Version Catalog（`gradle/`）、lint 設定
（`config/detekt/`、`.editorconfig`）、Renovate 設定（`renovate.json`）を変更するときの規約・ハマりどころです。
領域共通の原則（アーキテクチャ・モジュール依存関係・共通コード規約・テスト実行）は
[copilot-construction.md](../../copilot-construction.md) を参照してください。

## モジュールごとの wasmJs ターゲット設定

モジュール間の依存関係は `copilot-construction.md` の「2. モジュールと依存関係」を参照。

- `domain` / `data`：ブラウザAPIに依存しないので `wasmJs { nodejs() }` でテストする（軽量・Chrome不要）。
- `presentation`：Compose UIを含むため `wasmJs { browser() }` + `binaries.executable()` が必要
  （Compose UIテストの仕組み上、実行可能バイナリが要求される。外すとビルドエラーになる）。
- `app-wasmjs`：最終成果物。`binaries.executable()` を持つのはこのモジュールが主目的だが、
  `presentation` も上記の理由で保持している。

## lint（ktlint / detekt）のビルド設定

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
- **生成コードの扱い**：Compose Multiplatformのリソースジェネレータ等、`build/` 配下に生成される
  Kotlinコードは lint 対象外にしている（`.editorconfig` の `[**/build/**/*.{kt,kts}]` セクションで
  `ktlint_standard = disabled`）。Gradle側の `ktlint { filter { exclude(...) } }` はKMPの
  ソースセット単位タスクには効かなかったため、EditorConfigベースの無効化を採用している。
  同種の問題に遭遇したら、まずここを疑うこと。
- 新規モジュールを追加する場合も、`build.gradle.kts` で `alias(libs.plugins.ktlint)` /
  `alias(libs.plugins.detekt)` が `subprojects` ブロック経由で自動適用されるので個別追加は不要。
- ルール違反時の方針（コード側を直す／`@Suppress` の限定利用／baseline 不使用）と
  `@Composable` 関数の PascalCase 命名の許可は `copilot-construction.md` の「3. コード規約（共通）」を参照。

## 集約タスク `verify`

- `verify` はCIと同じ検証を1コマンドで行うルートの集約タスク。中身は
  「全プロジェクトの `check`（= `ktlintCheck` + `detekt` + `allTests`）」＋ `:app-wasmjs:wasmJsBrowserDistribution`
  ＋ UI 描画スモークテスト `:app-wasmjs:smokeTest`。
- CIの `build-lint-test` ジョブも `./gradlew verify --continue` を実行しているので、検証内容を変えたい場合は
  `ci.yml` ではなくルート `build.gradle.kts` の `verify` タスクを変更する（ローカルとCIの差異を作らないため）。
- `check` がすでに lint とテストを含むので、`verify` に `ktlintCheck` 等を個別に足して重複させないこと。
- `:app-wasmjs:smokeTest`（`app-wasmjs/build.gradle.kts` で定義）は `check` には含めず、`verify` から明示的に依存している
  （本番ビルドとブラウザのダウンロードを伴い重いため、`./gradlew build` / `check` には含めない）。
  `smokeTestNpmCi`（`e2e/` で `npm ci`）→ `smokeTestInstallBrowser`（Playwright 同梱 Chromium の headless shell を取得）→
  `smokeTest`（`playwright test`）の順に実行する。
  - Node.js は Kotlin Gradle プラグインがダウンロードするもの（`kotlinWasmNodeJsSetup`、`WasmNodeJsEnvSpec.executable`）を使い、
    npm も同梱の `npm-cli.js` を node から直接起動する。開発端末に Node.js が無くても、PATH に node が無くても動く。
  - `smokeTest` は本番ビルドの成果物と `e2e/` のソースを入力に宣言しているので、変更が無ければ `UP-TO-DATE` になる。
    再実行したい場合は `--rerun` を付ける。
  - スモークテストだけ飛ばしたい場合は `./gradlew verify -x :app-wasmjs:smokeTest`（PR 作成前は飛ばさないこと）。

## Node.js のバージョン

- Kotlin Gradle プラグインがダウンロードする Node.js のバージョンは `gradle/libs.versions.toml` の `nodejs` で固定し、
  ルート `build.gradle.kts` の `allprojects { plugins.withType<WasmNodeJsPlugin> { ... } }` で全プロジェクトの
  `WasmNodeJsEnvSpec.version` に適用している（`domain` / `data` の Node テストとスモークテストで同じ Node.js を使う）。
- `[versions]` の `nodejs` はライブラリ・プラグインから参照されないため Renovate の Gradle マネージャーでは検知されない。
  `renovate.json` の `customManagers`（regex、datasource `node-version`）で更新を検知させている。キー名や書式を変えたら
  `matchStrings` も合わせて直すこと。

## 依存関係・バージョン管理

- すべてのバージョンは `gradle/libs.versions.toml`（Version Catalog）で一元管理する。
  `build.gradle.kts` にバージョン文字列を直接書かない。
- BOM（Bill of Materials）を使う依存（例: Supabase）は、KMPの `sourceSets.xxx.dependencies` ブロック内では
  `implementation(platform(libs.xxx.bom))` が使えない（`platform()` はDependencyHandler拡張関数で
  KotlinDependencyHandlerには無いため）。代わりに `implementation(project.dependencies.platform(libs.xxx.bom))`
  と書くこと。

## wasmJs ターゲットのビルド設定の注意点

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
- ブラウザ実行時の注意点（`index.html` の `type="module"`、Canvas 描画での UI 検証、
  `wasmJsBrowserDevelopmentRun` のキャッシュ）は [presentation.instructions.md](presentation.instructions.md) を参照。
