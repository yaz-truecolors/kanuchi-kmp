@AGENTS.md

## Claude Code 固有の補足

上の `AGENTS.md`（およびそこから参照される `copilot-construction.md`・`.github/instructions/`・`docs/requirements.md`）が
本リポジトリの規約の正本です。このファイルには Claude Code で作業するときの読み替えだけを書きます（規約を重複して書かない）。

### 規約ファイルの読み込み

- 作業開始時に `copilot-construction.md` を必ず読む（ファイル名は Copilot 向けだが、全エージェント共通の規約）。
- 領域別ファイル `.github/instructions/<領域>.instructions.md` は、`.claude/rules/<領域>.md`（シンボリックリンク）経由で、
  フロントマターの `paths` に一致するファイルを読み書きしたときに自動で読み込まれる。
  - `paths` は Copilot 用の `applyTo` と同じ値にそろえる。領域別ファイルを追加・`applyTo` を変更したときは、`paths` と
    `.claude/rules/` のリンクもあわせて追加・更新する。
  - 自動読み込みは対象ファイルを開いたときに働くため、新規ファイルの作成だけを行う場合などは、索引
    （`copilot-construction.md`「領域別の規約ファイル（索引）」）を見て該当ファイルを自分で読むこと。
- コードレビュー（`/code-review` 等でPR・差分をレビューするとき）は、`.github/skills/code-review/SKILL.md` の
  「指摘しない事項」「重点的に確認すべき事項」を観点として使う。
  - PR をレビューして指摘を PR 上に残す（行ごとのレビュースレッド＋概要コメント）・再レビューで対応状況を確認する場合は、
    skill `pr-review`（`.claude/skills/pr-review/`）の手順で行う。

### PR 作成〜レビュー対応（`AGENTS.md` の手順の Claude Code での実施方法）

- PR は `gh pr create --draft` で作成し、本文末尾に Claude Code の署名行を入れる。
- Draft PR の作成後、CI の確認（`AGENTS.md` の手順2）とあわせて skill `pr-review` でセルフレビューし、指摘を PR に残す
  （Copilot のレビューが使えなくなったため。指摘への対応は `AGENTS.md` の手順3と同じ）。
- レビュースレッドへの返信と Resolve は `gh api graphql` で行う（トップレベルの PR コメントで返信しない）。

  ```sh
  # 未解決スレッドの一覧（thread id・コメント本文・ファイル位置）
  gh api graphql -f query='query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){pullRequest(number:$n){reviewThreads(first:100){nodes{id isResolved path line comments(first:20){nodes{author{login} body}}}}}}}' -f o=yaz-truecolors -f r=kanuchi-kmp -F n=<PR番号>
  # スレッドへ返信
  gh api graphql -f query='mutation($t:ID!,$b:String!){addPullRequestReviewThreadReply(input:{pullRequestReviewThreadId:$t,body:$b}){comment{id}}}' -f t=<thread id> -f b='<返信本文>'
  # スレッドを Resolve
  gh api graphql -f query='mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{isResolved}}}' -f t=<thread id>
  ```

### ローカル環境

- `./gradlew` の実行には JDK（`JAVA_HOME` か PATH の `java`）が、`presentation` のテスト（`verify` に含まれる）には
  Chrome か `CHROME_BIN` が、DBテスト（`./supabase/tests/run.sh`）には起動済みの Docker と Supabase CLI が必要
  （詳細は `copilot-construction.md`「4. テスト・検証（共通）」）。不足していて検証できなかった場合は、そのことをユーザーに報告する。
  - JDK・Chrome が PATH に無い端末では、各自の `.claude/settings.local.json`（コミットしない）の `env` で `JAVA_HOME`・`CHROME_BIN` を設定する。
  - Docker が起動していなければ `open -a Docker`（Docker Desktop）で起動してよい。
- `verify` は数分かかるため、Bash のバックグラウンド実行で走らせる。
- UI に関わる変更の実ブラウザでの確認（ログインした状態の画面の操作・スクリーンショット）は、skill `local-ui-check`
  （`.claude/skills/local-ui-check/`）の手順で、ローカル Supabase に接続したビルドを使って行う。

### hooks（編集・作業終了時の自動 lint）

`.claude/settings.json` の hooks で、`verify` を待たずに lint の違反をその場で直させている（スクリプトは `.claude/hooks/`）。
スクリプトは入力の JSON を `jq` で読むため、`jq` が必要（無い端末では hooks が何も表示されずに動かない）。

- `PostToolUse`（Write|Edit）: `.kt` / `.kts` を編集すると、そのモジュールの `ktlintFormat` を実行する（約7秒）。
  モジュール単位なので、同じモジュールの他のファイルも整形される。自動で直せない違反が残ると、違反箇所を Claude に返す。
- `Stop`: そのセッションで Claude が Write・Edit で編集した `.kt` / `.kts` のうち、未コミットのものがあるモジュールに
  `ktlintCheck`・`detekt` を実行し、失敗したら作業を終えずに直させる（利用者が自分で編集中のファイルだけなら実行しない）。
  同じセッションで3回差し戻しても直らなければ止めて、ユーザーに判断を任せる（`lint-on-stop.sh` の `MAX_ATTEMPTS`）。
- テストや `verify` の代わりではない（PR 作成前の `verify` は引き続き必須）。一時的に止めたいときは、各自の
  `.claude/settings.local.json` に `"disableAllHooks": true` を書く。
