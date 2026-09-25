# AGENTS.md

このリポジトリで作業するコーディングエージェント（GitHub Copilot 等）向けの指示です。

## 必読ドキュメント

作業を始める前に、必ず以下を読むこと。

- [copilot-construction.md](copilot-construction.md): 実装時の規約・ハマりどころ（how / 注意点）
- [docs/requirements.md](docs/requirements.md): 要件・設計（why）

## Pull Request 作成手順

エージェントが PR を作成する場合は、必ず以下の手順に従うこと。

### 1. Draft 状態で PR を作成する

- PR は **必ず Draft で作成する**。いきなり Open（Ready for review）で作成しない。
  - GitHub Copilot app の `create_pull_request` ツールを使う場合: `draft: true` を指定する。
  - `gh` CLI を使う場合: `gh pr create --draft ...`

### 2. GitHub Copilot Review を Balanced で開始する

- Draft PR の作成後、Copilot にレビューを依頼する。

  ```sh
  gh pr edit <PR番号> --add-reviewer @copilot
  ```

- Review effort level は **Balanced** を使う。
  - CLI / API からは effort level を指定できない。effort level は
    「PRで過去に使われたeffort」→「依頼者の個人設定」→「リポジトリ設定」→「Organization設定」
    の順に決まるため、リポジトリ設定（Settings → Copilot → Code review → Review effort level）
    または依頼者の個人設定で **Balanced** を設定しておくことを前提とする。
  - レビュー完了後、Copilot のレビュー概要コメントに表示される effort level が Balanced であることを確認する。
    **Balanced 以外（Lite 等）で実行されていた場合は、フィードバック対応に進まずユーザーに報告する**
    （GitHub の PR 画面の Reviewers から Balanced を選んで再依頼してもらう）。

### 3. レビューフィードバックに対応する

- Copilot（`copilot-pull-request-reviewer[bot]`）のレビューが投稿されるまで待つ。
  CI（`.github/workflows/ci.yml`）の結果もあわせて確認する。
- 未解決のレビュースレッドは、1件ずつ以下のいずれかで対応する。
  - **採用する**: 修正をコミット・push し、スレッドに「何をどう直したか」を返信する。
  - **採用しない**: 理由を返信する。`copilot-construction.md` 等で方針として決まっている事項
    （例: アクセシビリティ対応を行わない方針）は、該当箇所を根拠として示す。
- 返信後、スレッドを Resolve する。GitHub Copilot app では `reply_and_resolve_review_thread`
  ツールを使う（スレッドへの返信と Resolve を1回で行える）。
  トップレベルの PR コメントで返信しないこと。
- 修正内容が大きい場合は `gh pr edit <PR番号> --add-reviewer @copilot` で再レビューを依頼し、
  新たな指摘があれば同様に対応する。
- 対応中に新しく分かった規約・ハマりどころは `copilot-construction.md` に追記する。

### 4. 対応完了後に PR を Open にする

- すべてのレビュースレッドが対応済み（Resolve 済み）で、CI が成功していることを確認してから
  Draft を解除する。

  ```sh
  gh pr ready <PR番号>
  ```

- マージは人間が行う。エージェントはマージしない。
