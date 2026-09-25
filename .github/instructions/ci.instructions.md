---
applyTo: ".github/workflows/**,.github/github-app.yml"
---

# CI（GitHub Actions ワークフロー）・Copilot の作業環境の規約

`.github/workflows/` 配下（`ci.yml`・`supabase-deploy.yml`・`copilot-setup-steps.yml`・`copilot-code-review.yml`）と、
GitHub Copilot app のリポジトリ設定 `.github/github-app.yml` を変更するときの規約・ハマりどころです。
ローカルでのテスト・検証の実行方法は [copilot-construction.md](../../copilot-construction.md) の
「4. テスト・検証（共通）」を参照してください。

## 検証環境の3箇所をそろえる

検証（`./gradlew verify` と `./supabase/tests/run.sh`）は、次の3箇所で実行できるようにしている。
**検証に新しいツール（CLI・ブラウザ・Docker イメージ等）や手順を追加・変更したら、3箇所すべてを同じPRで更新すること**
（どれかを忘れると、CI では通るのに Copilot の環境では実行できない、といった差異が生まれる）。

| 環境 | ファイル | 役割 |
|---|---|---|
| CI | `.github/workflows/ci.yml` | 必須チェック（`build-lint-test`・`db-test`）で検証を実行する |
| Copilot cloud agent | `.github/workflows/copilot-setup-steps.yml` | エージェントの作業開始前に、検証に必要なツールと実行時にダウンロードされるものを事前に取得する |
| GitHub Copilot app（ローカル） | `.github/github-app.yml` | 新しいセッション（worktree）作成時の依存取得と、検証・開発サーバー等を実行する手動スクリプト |

- 検証の中身（何を実行するか）は `ci.yml` 等ではなく、ルート `build.gradle.kts` の `verify` タスクと
  `supabase/tests/run.sh` に置く（3箇所は「ツールを用意してそれを呼ぶ」だけにする）。
- JDK のバージョン（17）、Supabase CLI のバージョン（`SUPABASE_CLI_VERSION`）、Chrome のセットアップ方法は3箇所（ローカルは
  `gradle/gradle-daemon-jvm.properties`）で同じものを使う。

## `copilot-setup-steps.yml`（Copilot cloud agent の環境）

- **ジョブ名は `copilot-setup-steps` 固定**（それ以外の名前だと Copilot に認識されない）。ジョブで設定できるのは
  `steps` / `permissions` / `runs-on` / `services` / `snapshot` / `timeout-minutes`（最大59）だけで、それ以外（ジョブの `env` 等）は
  無視される。そのため Supabase CLI のバージョンはステップの `env` に書いている。
- `permissions` は `contents: read` だけにする（Copilot には別途トークンが渡される）。
- cloud agent のファイアウォールは、エージェントが起動したプロセスにだけ適用され、セットアップの手順には適用されない。
  **エージェントの実行中に外部から取得が必要になるもの（Gradle 依存、Kotlin Gradle プラグインが取得する Node.js・Yarn・npm 依存・
  binaryen、Playwright の Chromium、DBテストの Docker イメージ等）は、ここで漏れなく取得しておく。** 現状は `./supabase/tests/run.sh`
  と `./gradlew verify --continue` を1回ずつ実行して取得している（ビルド結果も残るので、エージェントの `verify` も速くなる）。
  - いずれかのステップが失敗すると、残りのステップは飛ばされてエージェントが作業を始める。失敗しうる `verify` は最後に置く。
  - Docker はランナー（GitHub ホストの `ubuntu-latest`）に入っているものを使う。DBテストのコンテナは検証後に `supabase stop` で止める
    （イメージは残るので、エージェントは取得なしで起動できる）。
- トリガーは `workflow_dispatch` と、このファイル自身を変更したときの `push` / `pull_request`（`paths` 指定）。このファイルを変更した
  PRでは、ワークフローが成功することを確認する。**`paths` 指定のため、`main` のルールセットの必須チェックには追加しないこと**
  （変更の無いPRでは実行されず、必須チェックが pending のままになりマージできなくなる）。
- cloud agent の作業開始前に毎回実行されるため、重い手順を足すときは所要時間（PR 上の実行時間）も確認する。

## `copilot-code-review.yml`（Copilot code review の環境）

- Copilot code review は、このファイルが無いと `copilot-setup-steps.yml` を実行環境のセットアップに使う（`verify` と DBテストを
  実行するため、レビューのたびに数分待たされ、Actions の実行時間も増える）。本プロジェクトではレビュー時に検証の実行を求めていないため、
  code review 用はチェックアウトだけの `copilot-code-review.yml` にしている。ジョブ名は同じく `copilot-setup-steps` 固定。
- 検証環境の3箇所には含めない（検証のためのツールを足さない）。

## `.github/github-app.yml`（GitHub Copilot app のリポジトリ設定）

- 仕様は [Repository configuration for the GitHub Copilot app](https://docs.github.com/copilot/reference/github-copilot-app-reference/repository-configuration)。
  **ファイルを変更しても、各開発者がアプリ上でレビュー・承認するまで適用されない**（空白・コメントの変更でも再承認が必要）。
- **スクリプトには `GH_TOKEN`・`COPILOT_GH_ACCOUNT_*` 等の資格情報が環境変数で渡される。** `env` / `printenv` / `set -x` など、
  環境変数をログに出力・ファイルに保存するコマンドをスクリプトに入れないこと（依存先のスクリプトも同様）。
- `instructions` は設定しない（エージェント向けの指示は `AGENTS.md` に一元化し、二重管理しない）。`automation` も既定のままにしている。
- `session.create` のスクリプト（Setup）は、新しい worktree で作業・検証を始められるよう、ダウンロードを伴う準備だけを行う
  （ビルド・テストはしない。worktree 作成のたびに実行されるため）。`~/.gradle` 等のキャッシュは worktree 間で共有されるので、2回目以降は数秒で終わる。
- `server_ready_pattern` は Rust の `regex` クレートの構文で、1つ目のキャプチャを URL として使う。開発サーバー
  （webpack-dev-server の `Loopback: http://localhost:8080/, http://[::1]:8080/`）と `serveDistribution`
  （`e2e/serve.js` の `serving <dir> at http://127.0.0.1:8081/`）の両方の出力に一致させている。どちらかの出力が変わったら
  パターンも直し、実際に起動した出力で一致することを確認する。
- `session.archive` のスクリプトは設けていない。DBテストの Supabase コンテナは `supabase/config.toml` の `project_id` 単位で
  worktree 間で共有されるため、アーカイブ時に止めると他のセッションのコンテナまで止めてしまう。

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
    `supabase-deploy.yml`・`copilot-setup-steps.yml` も同じバージョンにそろえ、テストしたCLIで本番に適用する。
    更新は Renovate が `renovate.json` の `customManagers`（regex）で3ファイルを同じPRで行う。
    手で変更する場合も3ファイルを同じ値にすること。
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
