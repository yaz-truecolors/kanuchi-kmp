---
applyTo: "presentation/**,app-wasmjs/**"
---

# UI / presentation 層（`presentation/`・`app-wasmjs/`）の規約

Compose Multiplatform の UI・ViewModel・リソース（`presentation/`）と、wasmJs エントリポイント
（`app-wasmjs/`）を変更するときの規約・ハマりどころです。
領域共通の原則（アーキテクチャ・モジュール依存関係・共通コード規約・テスト実行）は
[copilot-construction.md](../../copilot-construction.md) を参照してください。

## 文言・テキスト定数

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

## エラー表示

- **外部ライブラリ（supabase-kt 等）の例外の `message`/`toString()` をそのままUIに表示しない。**
  実際に `LoginViewModel` が `error.message` をそのまま表示していたところ、画面に
  `Headers: {Authorization=[******` が表示されてしまった事故がある。`AuthRepository` の契約では、
  `EmailNotInvitedException` / `GenericAuthFailureException` は presentation 層が `strings.xml` 管理下の文言に
  変換して表示し、それ以外の例外で `message` が非nullの場合は「UIにそのまま表示してよい（実装側が安全性を保証する）」。
  Repository 実装側の変換ルールは [data.instructions.md](data.instructions.md) を参照。
- ログイン画面が「入力したメールアドレスが招待済みかどうか」を区別できるエラーを表示するのは
  **意図的に許容しているトレードオフ**である。理由は [data.instructions.md](data.instructions.md) の
  「招待制アカウント作成（クライアント側）」を参照。

## フォント

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

## アクセシビリティ・対象ブラウザ

- **アクセシビリティ対応（スクリーンリーダー向け `Modifier.semantics { ... }`、
  `liveRegion` 等）は行わない方針。** 実装しないだけでなく、既存コードにも追加しないこと。
  過去のPRレビュー（Copilotコードレビュー）でこの種の指摘を受けることがあるが、
  本プロジェクトの方針として意図的に対応しないと決めているため、その指摘は採用しない
  （必要であれば本ファイルの当該記述と `docs/requirements.md` の「2. アプリ概要」UI/UX方針を根拠として説明する）。
  レビューで指摘しない事項の一覧は [.github/skills/code-review/SKILL.md](../skills/code-review/SKILL.md) を参照。
- **動作確認はGoogle Chromeを基準とする。** ローカルでの手動ブラウザ確認、CIの
  `browser-actions/setup-chrome`、いずれもChromeを使う。他ブラウザでの見た目・挙動の
  差異は許容し、個別対応しない。

## ブラウザ実行時の注意点（wasmJs）

- `app-wasmjs/src/wasmJsMain/resources/index.html` の `<script>` タグには
  **`type="module"` を付けておく（外さない）**。過去に付け忘れで、ブラウザで実行した際に
  `SyntaxError: Cannot use 'import.meta' outside a module` が発生し、アプリが
  真っ白のまま何も描画されない事故があった（Kotlin/Wasm のコンパイラ出力 `kanuchi.mjs` は
  `import.meta` を使う ES モジュール）。
  - 2026年9月時点の構成（Kotlin 2.4.20）では webpack が UMD 形式にバンドルし `import.meta` が残らないため、
    外しても描画されることをスモークテストで確認している。ただしバンドル形式や設定が変われば再発しうるので付けたままにする。
- `./gradlew build` や `ktlintCheck`/`detekt`、単体テストはこの種のランタイムエラーを検知できない。
  **本番ビルドが起動して `<canvas>` に描画されるかは、UI 描画スモークテスト（`./gradlew :app-wasmjs:smokeTest`。
  `verify` に含まれる）が自動で確認する**（規約は [e2e.instructions.md](e2e.instructions.md)）。
  スモークテストは起動直後の画面が描画されるかしか見ないので、UIに関わる変更をしたら、変更した画面・操作が
  意図どおりに表示・動作するかを実ブラウザ（Chrome）で確認すること。
- Compose for Web/Wasmは `<canvas>` に直接描画するため、`document.querySelectorAll('input')`
  等のDOM検査では要素を検出できない（`<canvas>` 自体も Shadow DOM 内にある）。手動・ヘッドレスブラウザでの
  UI検証はスクリーンショットの目視や、要素の推定座標へのクリック/キー入力シミュレーションで行う
  （自動テストとしての見た目・操作の検証はスコープ外。`copilot-construction.md` の「4. テスト・検証（共通）」）。
- `wasmJsBrowserDevelopmentRun`（webpack-dev-server経由）はコンテンツキャッシュや
  ライブリロードの都合でリソース変更が反映されないことがある。挙動を疑ったら
  `wasmJsBrowserDistribution` の成果物 (`build/dist/wasmJs/productionExecutable`) を
  `python3 -m http.server` 等で直接静的配信して確認する方が確実。
- Gradle 側の wasmJs 設定（`outputModuleName`、Compose Resources 依存の追加タイミング、`kotlinx-browser` 等）は
  [build.instructions.md](build.instructions.md) を参照。
