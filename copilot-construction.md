# Copilot向け開発ガイド (copilot-construction.md)

このドキュメントは、本リポジトリでコーディングエージェント（GitHub Copilot等）が作業する際に
一貫した設計方針・規約を守るためのガイドです。人間の開発者にとっても規約書として機能します。

背景・要件の全体像は [docs/requirements.md](docs/requirements.md) を参照してください。
このファイルはそれを踏まえた「実装時の具体的な決め事・注意点」のうち、**領域を問わず常に適用されるもの**
（アーキテクチャ原則・モジュール依存関係・共通のコード規約・テスト実行・更新方針）を記録します。
領域ごとの規約・ハマりどころは、下の索引にある領域別ファイルに分けています。
PR作成〜レビュー対応の手順は [AGENTS.md](AGENTS.md) を参照してください。

## 領域別の規約ファイル（索引）

触るファイルのパスに対応する領域別ファイルを、作業前に必ず読むこと。
GitHub Copilot（cloud agent / code review）はフロントマターの `applyTo` に一致するファイルを扱うときに
自動で読み込むが、`applyTo` を解釈しないエージェント・人間は下表を見て該当ファイルを読むこと。

| 触るパス | 読むファイル | 主な内容 |
|---|---|---|
| `**/*.gradle.kts`, `gradle/**`, `gradle.properties`, `config/detekt/**`, `.editorconfig`, `renovate.json` | [.github/instructions/build.instructions.md](.github/instructions/build.instructions.md) | モジュールごとの wasmJs ターゲット設定、detekt/ktlint のビルド設定（KMPのソースセット・生成コード除外）、集約タスク `verify`（スモークテストのタスク構成）、Node.js のバージョン固定と Renovate、Version Catalog・BOM、wasmJs の Gradle DSL の注意点 |
| `presentation/**`, `app-wasmjs/**` | [.github/instructions/presentation.instructions.md](.github/instructions/presentation.instructions.md) | 文言の `strings.xml` 集約、エラー表示、フォント（Noto Sans JP）、アクセシビリティ対応を行わない方針、Chrome基準、`index.html` の `type="module"` などブラウザ実行時の注意点 |
| `data/**` | [.github/instructions/data.instructions.md](.github/instructions/data.instructions.md) | 認証方式・RLS準拠クエリ・鍵の扱い、supabase-kt 例外の変換、招待制のクライアント側（`createUser`・例外変換・Hookメッセージ定数・意図的なトレードオフ） |
| `supabase/**` | [.github/instructions/supabase.instructions.md](.github/instructions/supabase.instructions.md) | マイグレーション運用、新規テーブルの RLS/grant/policy の3点セット、ロール変更トリガー・`is_admin()`・ローカル検証、Before User Created Hook のサーバー側規約 |
| `.github/workflows/**` | [.github/instructions/ci.instructions.md](.github/instructions/ci.instructions.md) | `ci.yml`（Chromeセットアップ・`verify`・スモークテストの artifact・Pagesデプロイ・必須チェックのジョブ名）、`supabase-deploy.yml`（Secrets・トークン有効期限） |
| `e2e/**` | [.github/instructions/e2e.instructions.md](.github/instructions/e2e.instructions.md) | UI 描画スモークテスト（Playwright）の位置付け（起動して描画されるかだけを見る）、実行方法、外部通信の遮断と許容する `console.error`、Canvas 描画の判定方法 |
| `domain/**` | [.github/instructions/domain.instructions.md](.github/instructions/domain.instructions.md) | domain 層に関わるアーキテクチャ原則（本ファイル1節）への参照、UI文言を持たない・マーカー例外、`AuthRepository` の契約 |

コードレビュー時の観点（指摘しない事項・重点的に確認すべき事項）は
[.github/skills/code-review/SKILL.md](.github/skills/code-review/SKILL.md) にまとめています。

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

- 各モジュールの wasmJs ターゲット設定（`domain` / `data` は `nodejs()`、`presentation` は `browser()` +
  `binaries.executable()` が必要な理由、`app-wasmjs` の位置づけ）は
  [build.instructions.md](.github/instructions/build.instructions.md) の「モジュールごとの wasmJs ターゲット設定」を参照。

## 3. コード規約（共通）

- **命名・フォーマット**：ktlint。@Composable関数のPascalCase命名は `.editorconfig` で許可済み
  （`ktlint_function_naming_ignore_when_annotated_with = Composable`）。
- **静的解析**：detekt。設定は `config/detekt/detekt.yml`。
  - `@Composable` 関数の PascalCase 命名は detekt 側でも `FunctionNaming.ignoreAnnotated: ["Composable"]` で許可している。
  - ルール違反は原則コード側を直す。ルールがプロジェクトの設計と衝突する場合のみ、理由をコメントに残して
    `detekt.yml` を調整するか、該当箇所に限定して `@Suppress` する（例: `SupabaseAuthRepository` は
    「失敗は必ず `Result` で返す」契約のため `Exception` を一括捕捉しており、`TooGenericExceptionCaught` のみ抑制）。
    baseline ファイルでの一括抑制は使わない。
  - KMPで detekt がソースセットを拾わない問題への対処・生成コードの除外・detekt 設定変更時の
    `NO-SOURCE` 確認手順など、lint のビルド設定に関するハマりどころは
    [build.instructions.md](.github/instructions/build.instructions.md) の「lint（ktlint / detekt）のビルド設定」を参照。
- **コルーチンと例外**：コルーチン内で例外を`catch (e: Exception)`する際は、`kotlinx.coroutines.CancellationException`
  を先に`catch`して再送出すること。握りつぶすと構造化された並行処理のキャンセルが正しく伝播しない。

## 4. テスト・検証（共通）

- テスト範囲は domain + data + ViewModel（presentation）のロジックまで。UIの見た目や操作（レイアウト・文言・入力・画面遷移）の
  テストはスコープ外。
  - 例外として、本番ビルドがブラウザで**起動して `<canvas>` に描画されるかだけ**を確認するスモークテスト
    （`e2e/`、`./gradlew :app-wasmjs:smokeTest`）がある。`index.html` の不備や起動時の例外で画面が真っ白になる事故を
    検知するためのもので、UIの見た目や振る舞いは検証しない（規約は [e2e.instructions.md](.github/instructions/e2e.instructions.md)）。
    Node.js は Kotlin Gradle プラグインがダウンロードするもの、ブラウザは Playwright 同梱の Chromium を使うため、
    Node.js や Chrome を入れていない端末でも実行できる（初回はブラウザのダウンロードのためネットワークが必要）。
- ローカル開発でChromeがない環境では `presentation` モジュールのブラウザテストは実行できない
  （Karmaが `ChromeHeadless` を起動できず `Errors occurred during launch of browser for testing.` で失敗する）。
  Chromeを標準の場所に入れていない場合は、環境変数 `CHROME_BIN` にChrome（Chrome for Testing等でも可）の
  実行ファイルのパスを指定すれば実行できる。
  `domain` / `data` の変更検証には `./gradlew :domain:wasmJsNodeTest` のようにNode.jsテストを使うこと。
  なお、テストファイルが1つもないモジュールのテストタスクは `SKIPPED` になるため、その場合はChromeが無くても失敗しない。
- **CIと同じ検証はルートの集約タスク `./gradlew verify` で1コマンドで実行できる。** PR作成前に必ず実行して成功させること。
  中身は「全プロジェクトの `check`（= `ktlintCheck` + `detekt` + `allTests`）」＋ `:app-wasmjs:wasmJsBrowserDistribution`
  ＋ UI 描画スモークテスト `:app-wasmjs:smokeTest`。
  `verify` タスク自体を変更する際の注意（`ci.yml` ではなく `build.gradle.kts` 側を変える、`check` と重複させない）は
  [build.instructions.md](.github/instructions/build.instructions.md) の「集約タスク `verify`」を参照。
- CI（Chromeのセットアップ、Pagesデプロイ、`main` のルールセットと必須チェック名）については
  [ci.instructions.md](.github/instructions/ci.instructions.md) を参照。
- 「起動して描画されるか」はスモークテスト（`verify` に含まれる）で自動確認される。UIに関わる変更をしたら、それに加えて
  変更した画面・操作が意図どおりに表示・動作するかを実ブラウザ（Chrome）で確認すること（スモークテストは起動直後の画面が
  描画されるかしか見ないため。理由と確認方法は [presentation.instructions.md](.github/instructions/presentation.instructions.md) の
  「ブラウザ実行時の注意点（wasmJs）」）。

## 5. コードレビューで指摘しない事項

過去のPRレビュー（Copilotコードレビュー）で、本プロジェクトが意図的に対応しないと決めている事項
（アクセシビリティ対応、Chrome以外のブラウザ差異 等）を指摘されることがあるが、その指摘は採用しない。
対象の一覧と根拠は [.github/skills/code-review/SKILL.md](.github/skills/code-review/SKILL.md) の
「指摘しない事項」を参照し、レビューに返信する際はそこに挙げた根拠ファイルの該当箇所を示すこと。

## 6. このドキュメントの更新方針

実装を進める中で新しく分かった「ハマりどころ」「決めた規約」は、都度**該当する領域別ファイル**
（`.github/instructions/<領域>.instructions.md`。索引は上表）に追記すること。
領域を問わず常に適用されるもの（アーキテクチャ原則・モジュール依存関係・共通のコード規約・テスト実行）だけを
このファイルに追記する。どの領域にも当てはまらないパスの規約が出てきた場合は、新しい領域別ファイルを作り
（フロントマターの `applyTo` で対象パスを指定する）、上の索引表に追加する。
意図的に対応しないと決めた事項が増えた場合は、根拠とあわせて
[.github/skills/code-review/SKILL.md](.github/skills/code-review/SKILL.md) の「指摘しない事項」にも追加する。
`docs/requirements.md` は要件・設計の「why」を記録する場所、このファイルと領域別ファイルは実装者向けの
「how / 注意点」を記録する場所、という役割分担で運用する。
