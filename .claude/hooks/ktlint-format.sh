#!/usr/bin/env bash
# PostToolUse（Write|Edit）hook: 編集した .kt / .kts ファイルのモジュールに ktlintFormat をかける。
# 自動で直せない違反が残った場合は exit 2 で内容を Claude に返し、その場で直させる（内側ループ）。
# 編集したファイルはセッションごとに記録し、Stop hook（lint-on-stop.sh）の対象にする。
set -uo pipefail

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 0

input=$(cat)
file=$(jq -r '.tool_input.file_path // .tool_response.filePath // empty' <<<"$input")
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

session_id=$(jq -r '.session_id // "unknown"' <<<"$input")
echo "$rel" >>"${TMPDIR:-/tmp}/kanuchi-lint-on-stop-${session_id}.files"

if ! output=$(./gradlew "$task" --quiet 2>&1); then
  # --quiet では ktlint の違反がコンソールに出ないため、出力に載るレポートファイルから読む（ANSI の色は除く）
  violations=$(echo "$output" | sed -nE 's/^ *- (.*ktlint.*\.txt)$/\1/p' | xargs -I{} cat {} |
    sed -E $'s/\x1b\\[[0-9;]*m//g' | grep -E '\.kts?:[0-9]+:[0-9]+' | head -30)
  if [ -n "$violations" ]; then
    printf '%s\n%s\n' "ktlintFormat（$task）で自動修正できない違反が残っています。コード側を直してください。" "$violations" >&2
  else
    # JDK が見つからない等、lint 以外の理由で失敗した場合は Gradle の出力をそのまま返す
    printf '%s\n%s\n' "ktlintFormat（$task）が lint 以外の理由で失敗しました。" "$(echo "$output" | tail -30)" >&2
  fi
  exit 2
fi
exit 0
