---
name: code-review
description: kanuchi-kmp リポジトリのプルリクエストをレビューするときの観点（意図的に対応しないと決めているため指摘しない事項と、重点的に確認すべき事項）。Pull Request のコードレビュー、変更差分のレビューを行うときに使う。
---

# kanuchi-kmp のコードレビュー観点

このスキルは、本リポジトリのPRをレビューするときに使う観点の一覧です。
規約の詳細（理由・具体的な書き方）はここに重複して書かず、各項目に示した根拠ファイルを正とします。
変更ファイルのパスに対応する領域別ファイル（`.github/instructions/*.instructions.md`）の索引は
[copilot-construction.md](../../../copilot-construction.md) の「領域別の規約ファイル（索引）」にあります。

## 指摘しない事項

以下は本プロジェクトの方針として**意図的に対応しないと決めている**ため、レビューで指摘・提案しないこと。

| 事項 | 指摘しない内容 | 根拠 |
|---|---|---|
| アクセシビリティ | スクリーンリーダー向けの `Modifier.semantics { ... }`、`liveRegion` 等のアクセシビリティ対応の追加・不足 | `docs/requirements.md`「2. アプリ概要」UI/UX方針（アクセシビリティ）、`.github/instructions/presentation.instructions.md`「アクセシビリティ・対象ブラウザ」 |
| 対象ブラウザ | Google Chrome 以外のブラウザでの見た目・挙動の差異、ブラウザ互換性への対応 | `docs/requirements.md`「2. アプリ概要」（対象ブラウザ）、`.github/instructions/presentation.instructions.md`「アクセシビリティ・対象ブラウザ」 |
| 招待済みかどうかが分かるエラー表示 | ログイン画面のエラーから招待済みメールアドレスを推測できる（メール列挙）こと。意図的に許容しているトレードオフ | `docs/requirements.md`「5. 認証・権限設計」、`.github/instructions/data.instructions.md`「招待制アカウント作成（クライアント側）」 |
| `OTP.Config.createUser = true` | 未招待ユーザーのアカウント作成を許してしまう、という指摘（未招待の拒否はサーバー側のHookで強制している） | `.github/instructions/data.instructions.md`「招待制アカウント作成（クライアント側）」 |
| Edge Function 方式 | 招待を Edge Function + Admin API（`inviteUserByEmail`）方式やサーバー側中継エンドポイントに変える提案 | `docs/requirements.md`「5. 認証・権限設計」、`.github/instructions/supabase.instructions.md`「招待制アカウント作成（Before User Created Hook）」 |
| Compose 関数の命名 | `@Composable` 関数の PascalCase 命名（関数名は camelCase にすべき、という指摘） | `copilot-construction.md`「3. コード規約（共通）」、`.editorconfig`、`config/detekt/detekt.yml` |
| 多言語対応 | 文言の多言語化（i18n）や、日本語以外のフォント・フォールバックの追加 | `docs/requirements.md`「2. アプリ概要」UI/UX方針（言語・フォント） |
| 外部システムのエラー説明文 | `AuthRestException.errorDescription` 等、外部システム（Supabase）が返すユーザー向け説明文をそのまま表示していることを「`strings.xml` への集約漏れ」とする指摘 | `.github/instructions/presentation.instructions.md`「文言・テキスト定数」 |
| 公開可能な Supabase の値 | Supabase の URL・publishable key（anon key）がクライアントコードに含まれていること（RLSで保護される前提の公開可能な値） | `docs/requirements.md`「7. 開発環境・規約」（Secrets管理）、`.github/instructions/data.instructions.md`「認証・RLS・鍵の扱い」 |
| UI描画テスト | UIの見た目や操作（レイアウト・文言・入力・画面遷移）の自動テストが無いこと、スモークテスト（`e2e/`）に画面ごとの検証・操作シナリオ・スナップショット比較を足すべきという提案（テスト範囲は domain + data + ViewModel のロジックまでで、スモークテストは起動して描画されるかだけを見る） | `docs/requirements.md`「7. 開発環境・規約」（テスト方針）、`copilot-construction.md`「4. テスト・検証（共通）」、`.github/instructions/e2e.instructions.md`「位置付け」 |
| DBテストと `verify` / 適用済みマイグレーション | DBテスト（`supabase/tests/`）を `./gradlew verify` に含めるべき・`build-lint-test` ジョブに統合すべきという提案（Docker が必要なため独立ジョブ `db-test` にしている）、DBテストで見つかった既知の問題（`todo` で記録）を直すために適用済みのマイグレーションファイルを書き換える提案（修正は新しいマイグレーションで行う） | `copilot-construction.md`「4. テスト・検証（共通）」、`.github/instructions/ci.instructions.md`「`ci.yml`」、`.github/instructions/supabase.instructions.md`「マイグレーション運用」 |

これらに該当する指摘をしてしまった場合、PR作成者は根拠ファイルの該当箇所を示して「採用しない」と返信する
（手順は `AGENTS.md` の「3. レビューフィードバックに対応する」）。

## 重点的に確認すべき事項

以下は過去の事故や、1つ漏れるとセキュリティ・動作に直結する事項です。変更差分に該当箇所があれば重点的に確認すること。
確認の基準・理由は根拠ファイルを参照する。

| 観点 | 確認すること | 根拠 |
|---|---|---|
| RLS 違反クエリ | `data` 層の Repository 実装のクエリが RLS 方針（本人データのみ／adminは全件、等）に違反していないか | `.github/instructions/data.instructions.md`「認証・RLS・鍵の扱い」、`docs/requirements.md`「5. 認証・権限設計」 |
| 新規テーブルの3点セット | 新しいテーブルに RLS 有効化・`authenticated` への grant（`anon` には付与しない）・policy がそろっているか | `.github/instructions/supabase.instructions.md`「マイグレーション運用」 |
| マイグレーションとDBテスト | マイグレーションを追加・変更したPRで、対応する DBテスト（`supabase/tests/database/`）が追加・更新されているか（新しいテーブルなら本人/他人/admin/anon × 参照/追加/変更/削除、`00_schema_security.test.sql` の `tables_are` の一覧と `authenticated` の権限の一覧）、`plan(N)` の件数が合っているか、RLS の対象テーブルをサブクエリで引いて「他人の行」を特定していないか（RLS で空になりテストが素通りする）、適用済みのマイグレーションファイルを変更していないか | `.github/instructions/supabase.instructions.md`「マイグレーション運用」「DBテスト（`supabase/tests/`）」 |
| RLS・トリガー・Hook の SQL | ロール変更トリガーが実行ロール `authenticated` の場合のみチェックしているか、`profiles` の RLS で自己参照による無限再帰を起こしていないか、Hook 関数の `EXECUTE` を `PUBLIC` / `anon` / `authenticated` から剥奪しているか（`PUBLIC` を含めないと既定の付与経由で呼び出せる）、Hook の message に内部情報を含めていないか | `.github/instructions/supabase.instructions.md`「RLS・トリガー」「招待制アカウント作成（Before User Created Hook）」 |
| Hook メッセージと Kotlin 定数の一致 | Hook の SQL が返す message と `SupabaseAuthRepository` の `EMAIL_NOT_INVITED_HOOK_MESSAGE` が一致しているか（片方だけの変更を見逃さない） | `.github/instructions/supabase.instructions.md`・`.github/instructions/data.instructions.md` の招待制の節 |
| 例外の生メッセージの UI 表示 | supabase-kt 等の例外の `message` / `toString()` を UI にそのまま出していないか（Authorization ヘッダー等の内部情報が漏れる） | `.github/instructions/data.instructions.md`「例外の扱い」、`.github/instructions/presentation.instructions.md`「エラー表示」 |
| `CancellationException` | コルーチン内の `catch (e: Exception)` の前に `CancellationException` を捕捉して再送出しているか | `copilot-construction.md`「3. コード規約（共通）」 |
| 秘密鍵の埋め込み | service_role key / secret key（`sb_secret_...`）や GitHub Actions Secrets の値がコード・ログに含まれていないか | `.github/instructions/data.instructions.md`「認証・RLS・鍵の扱い」、`.github/instructions/ci.instructions.md`「`supabase-deploy.yml`」 |
| 文言の `strings.xml` 集約 | Composable / ViewModel に日本語文言をハードコードしていないか、`data` / `domain` 層がUI表示用の文言を持っていないか | `.github/instructions/presentation.instructions.md`「文言・テキスト定数」 |
| domain 層の純粋性 | `domain` に UI 都合・Supabase 都合のロジックや他モジュールへの依存が入っていないか | `copilot-construction.md`「1. アーキテクチャ原則」、`.github/instructions/domain.instructions.md` |
| `index.html` の `type="module"` | `app-wasmjs/src/wasmJsMain/resources/index.html` の `<script>` タグから `type="module"` が外れていないか | `.github/instructions/presentation.instructions.md`「ブラウザ実行時の注意点（wasmJs）」 |
| フォント | 画面のルートが `MaterialTheme` ではなく `KanuchiTheme` でラップされているか、`composeResources/font/` にフォント以外のファイルを置いていないか | `.github/instructions/presentation.instructions.md`「フォント」 |
| バージョン管理 | `build.gradle.kts` にバージョン文字列を直書きしていないか（`gradle/libs.versions.toml` で管理） | `.github/instructions/build.instructions.md`「依存関係・バージョン管理」 |
| lint 設定の変更 | detekt の設定変更で解析対象が `NO-SOURCE` になっていないか、baseline での一括抑制を使っていないか | `.github/instructions/build.instructions.md`「lint（ktlint / detekt）のビルド設定」、`copilot-construction.md`「3. コード規約（共通）」 |
| スモークテストの外部通信遮断・許容エラー | `e2e/` のテストが localhost 以外への通信を遮断したままか（本番 Supabase にリクエストを投げない）、`console.error` の許容条件が理由付きで限定されているか（広すぎる許容で実行時エラーを見逃さないか） | `.github/instructions/e2e.instructions.md`「外部通信の遮断と console.error の扱い」 |
| CI の必須チェック・検証内容 | `ci.yml` のジョブ名 `build-lint-test` / `db-test` を変更していないか、`db-test` を paths フィルタ等でスキップしていないか、検証内容を `ci.yml` 側で変えていないか（`verify` タスク・`supabase/tests/run.sh` 側で変える）、`SUPABASE_CLI_VERSION` が `ci.yml` と `supabase-deploy.yml` で一致しているか | `.github/instructions/ci.instructions.md`「`ci.yml`」 |
