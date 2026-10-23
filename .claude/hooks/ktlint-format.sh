#!/usr/bin/env bash
# PostToolUse（Write|Edit）hook: 編集した .kt / .kts ファイルのモジュールに ktlintFormat をかける。
# 自動で直せない違反が残った場合は exit 2 で内容を Claude に返し、その場で直させる（内側ループ）。
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 0

file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty')
case "$file" in
  *.kt | *.kts) ;;
  *) exit 0 ;;
esac

# 絶対パスをリポジトリ相対にし、先頭のディレクトリからモジュールを決める
rel=${file#"$PWD"/}
case "$rel" in
  domain/* | data/* | presentation/* | app-wasmjs/*) task=":${rel%%/*}:ktlintFormat" ;;
  */*) exit 0 ;; # モジュール外（e2e/ 等）は ktlint の対象外
  *) task=":ktlintFormat" ;; # ルートの *.gradle.kts
esac

# build/ 配下の生成コードは lint 対象外（.editorconfig で無効化している）
case "$rel" in */build/*) exit 0 ;; esac

if ! output=$(./gradlew "$task" --quiet 2>&1); then
  {
    echo "ktlintFormat（$task）で自動修正できない違反が残っています。コード側を直してください。"
    # --quiet では ktlint の違反がコンソールに出ないため、出力に載るレポートファイルから読む（ANSI の色は除く）
    echo "$output" | sed -nE 's/^ *- (.*ktlint.*\.txt)$/\1/p' | xargs -I{} cat {} |
      sed -E $'s/\x1b\\[[0-9;]*m//g' | grep -E '\.kts?:[0-9]+:[0-9]+' | head -30
  } >&2
  exit 2
fi
exit 0
