# AGENTS.md

## Language

- Think and reason in English.
- Use Japanese when communicating with the user.

このリポジトリで作業するコーディングエージェント（Claude Code・GitHub Copilot 等）向けの指示です。
Claude Code 固有の読み替え（規約ファイルの自動読み込み・PR レビュー対応のコマンド等）は [CLAUDE.md](CLAUDE.md) にある。

## 必読ドキュメント

作業を始める前に、必ず以下を読むこと。

- [copilot-construction.md](copilot-construction.md): 領域を問わず常に適用される実装時の規約（how / 注意点）と、
  領域別ファイルの索引
- [.github/instructions/](.github/instructions/): 領域別の規約・ハマりどころ（`<領域>.instructions.md`）
- [docs/requirements.md](docs/requirements.md): 要件・設計（why）

`.github/instructions/*.instructions.md` は、フロントマターの `applyTo` に一致するパスを扱うときに
GitHub Copilot が自動で読み込む。`applyTo` を解釈しないエージェント（Copilot 以外のエージェント等）も、
作業で触るパスに対応する領域別ファイルを `copilot-construction.md` の「領域別の規約ファイル（索引）」で確認し、
必ず読んでから作業すること（複数の領域にまたがる変更では、該当するすべてのファイルを読む）。

## Pull Request 作成手順

エージェントが PR を作成する場合は、必ず以下の手順に従うこと。

### 0. PR 作成前の準備

- PR を作成する前に、CI と同じ検証を行う集約タスクをローカルで実行し、成功させる。

  ```sh
  ./gradlew verify
  ```

  （Chrome が無い環境の扱いは `copilot-construction.md` の「4. テスト・検証（共通）」を参照）
- `supabase/` を変更した場合は、DBテストもローカルで実行し、成功させる（Docker が必要。CI では `db-test` ジョブで実行される）。

  ```sh
  ./supabase/tests/run.sh
  ```

- PR の本文は `.github/pull_request_template.md` の構成に従って書く
  （該当しないチェック項目は削除してよい）。

### 1. Draft 状態で PR を作成する

- PR は **必ず Draft で作成する**。いきなり Open（Ready for review）で作成しない。
  - GitHub Copilot app の `create_pull_request` ツールを使う場合: `draft: true` を指定する。
  - `gh` CLI を使う場合: `gh pr create --draft ...`

### 2. CI の結果を確認する

- Draft PR の作成後、CI（`.github/workflows/ci.yml`）の結果を確認する。失敗していれば原因を直してコミット・push する。

### 3. レビューフィードバックに対応する

- エージェントからレビュアーへのレビュー依頼は行わない（レビュアーの指定は人間が行う）。
- PR にレビューコメントが付いている場合、未解決のレビュースレッドは、1件ずつ以下のいずれかで対応する。
  - **採用する**: 修正をコミット・push し、スレッドに「何をどう直したか」を修正コミットの SHA とあわせて返信する
    （後から PR を見た人・再レビューで、どのコミットで直したかを追えるようにするため）。
  - **採用しない**: 理由を返信する。`copilot-construction.md`・領域別ファイル（`.github/instructions/`）・
    `docs/requirements.md` 等で方針として決まっている事項（例: アクセシビリティ対応を行わない方針）は、
    該当箇所を根拠として示す。意図的に対応しないと決めている事項の一覧と根拠は
    `.github/skills/code-review/SKILL.md` の「指摘しない事項」にある。
- 返信後、スレッドを Resolve する。GitHub Copilot app では `reply_and_resolve_review_thread`
  ツールを使う（スレッドへの返信と Resolve を1回で行える）。Claude Code では `gh api graphql` で行う（コマンドは [CLAUDE.md](CLAUDE.md)）。
  トップレベルの PR コメントで返信しないこと。
- 対応中に新しく分かった規約・ハマりどころは、該当する領域別ファイル（`.github/instructions/<領域>.instructions.md`）に
  追記する（領域を問わないものだけ `copilot-construction.md` に追記する。詳細は同ファイルの「6. このドキュメントの更新方針」）。

### 4. 対応完了後に PR を Open にする

- CI が成功し、レビュースレッドがある場合はすべて対応済み（Resolve 済み）であることを確認してから
  Draft を解除する。

  ```sh
  gh pr ready <PR番号>
  ```

- マージは人間が行う。エージェントはマージしない。
