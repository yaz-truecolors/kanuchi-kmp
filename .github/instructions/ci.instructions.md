---
applyTo: ".github/workflows/**"
---

# CI（GitHub Actions ワークフロー）の規約

`.github/workflows/` 配下（`ci.yml`・`supabase-deploy.yml`）を変更するときの規約・ハマりどころです。
ローカルでのテスト・検証の実行方法は [copilot-construction.md](../../copilot-construction.md) の
「4. テスト・検証（共通）」を参照してください。

## `ci.yml`

- CI（`.github/workflows/ci.yml`）では `browser-actions/setup-chrome` でChromeをセットアップしてから
  テストを実行する。ローカルで再現できない場合はこのステップの有無を疑う。
- CIの `build-lint-test` ジョブは `./gradlew verify --continue` を実行している。検証内容を変えたい場合は
  `ci.yml` ではなくルート `build.gradle.kts` の `verify` タスクを変更する（ローカルとCIの差異を作らないため。
  詳細は [build.instructions.md](build.instructions.md) の「集約タスク `verify`」）。
- UI 描画スモークテスト（`:app-wasmjs:smokeTest`）も `verify` 経由で `build-lint-test` ジョブ内で実行されるため、
  失敗すると必須チェックが通らずマージできない。Node.js（Kotlin Gradle プラグインがダウンロード）・`npm ci`・
  Playwright 同梱 Chromium のダウンロードはすべて Gradle タスク側で行うので、`ci.yml` に `actions/setup-node` や
  `npm ci` のステップを足さないこと（`browser-actions/setup-chrome` の Chrome は `presentation` の Karma テスト用で、
  スモークテストは使わない）。
- スモークテスト失敗時は、`e2e/test-results/`（スクリーンショット・コンソールログ・Playwright トレース）を
  `actions/upload-artifact` で artifact `smoke-test-results` としてアップロードする（`if: failure()`）。
  CI で失敗したらまずこの artifact を確認する（読み方は [e2e.instructions.md](e2e.instructions.md)）。
- `db-test` ジョブは DBテスト（`supabase/tests/database/` の pgTAP テスト）を実行する。中身はローカルと同じ
  スクリプト `./supabase/tests/run.sh`（Postgres のみ起動 → マイグレーション適用 → `supabase test db`）で、
  テストの内容・手順を変えたい場合は `ci.yml` ではなくスクリプトやテスト側を変更する（ローカルとCIの差異を作らないため）。
  テストの規約は [supabase.instructions.md](supabase.instructions.md) の「DBテスト（`supabase/tests/`）」。
  - `db-test` は `main` のルールセットの必須チェックに追加する予定のジョブのため、**paths フィルタや `if:` で
    スキップしないこと**（スキップされた必須チェックは pending のままになり、PRがマージできなくなる）。
    Supabase と無関係なPRでも実行される。必須チェックに追加した後は、`build-lint-test` と同様にジョブ名を変更しないこと。
  - Supabase CLI のバージョンは再現性のため `SUPABASE_CLI_VERSION` で固定している（`latest` にしない）。
    `supabase-deploy.yml` も同じバージョンにそろえ、テストしたCLIで本番に適用する。
    更新は Renovate が `renovate.json` の `customManagers`（regex）で両ファイルを同じPRで行う。
    手で変更する場合も両ファイルを同じ値にすること。
  - `build-lint-test`（Gradle）とは独立したジョブにしている。Docker が必要な DBテストを `./gradlew verify` に
    含めないため（`verify` は Docker の無い環境でも実行できるようにしている）。
- `main` へのマージ時は追加でGitHub Pagesへの自動デプロイが走る。
- **`main` はルールセット（main-protect）で保護されており、直接pushできない。** 変更は必ずPR経由でマージする
  （承認数は0件でよいが、ステータスチェック `build-lint-test` の成功が必須。最新 `main` との同期は不要）。
  （`db-test` はマージ後に必須チェックへ追加する予定。追加されたら上記の必須チェックに含まれる）
  必須チェックはジョブ名で照合されるため、**`ci.yml` のジョブ名 `build-lint-test` を変更すると必須チェックが
  永久に満たされずPRがマージできなくなる。** ジョブ名を変える必要がある場合は、先にルールセット側の
  必須チェック名を変更すること。

## `supabase-deploy.yml`

- Supabase CLI のバージョンは `ci.yml` の `db-test` ジョブと同じ `SUPABASE_CLI_VERSION` に固定している（上記）。
- `main` へのマージ時、`supabase-deploy.yml` が `supabase db push` を実行し、未適用のマイグレーションを
  自動的に本番へ反映する（マイグレーションの運用ルールは [supabase.instructions.md](supabase.instructions.md) を参照）。
- このCIには `SUPABASE_ACCESS_TOKEN` / `SUPABASE_DB_PASSWORD` / `SUPABASE_PROJECT_ID` の
  3つのGitHub Actions Secretsが必要（リポジトリ管理者が事前に登録する。詳細は
  `supabase/README.md`）。いずれも強い権限を持つ秘密情報のため、コード・チャット・
  ログに絶対に出力しないこと。
- `SUPABASE_ACCESS_TOKEN` には有効期限（Expiration）を設定する運用にしている。
  期限切れになるとCIが認証エラーで失敗するため、更新手順を `supabase/README.md`
  （「`SUPABASE_ACCESS_TOKEN` の有効期限切れ・更新手順」節）に記載している。
  CIが原因不明で失敗した場合は、まずこのトークンの期限切れを疑うこと。
