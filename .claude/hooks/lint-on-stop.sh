#!/usr/bin/env bash
# Stop hook: 未コミットの .kt / .kts 変更があるモジュールに ktlintCheck・detekt をかける。
# 失敗したら decision: block で Claude に差し戻して修正させる（内側ループ）。
# 停止条件: 成功するか、同じセッションで MAX_ATTEMPTS 回差し戻したら止める（無限ループ防止）。
set -uo pipefail

MAX_ATTEMPTS=3

cd "${CLAUDE_PROJECT_DIR:-$(git rev-parse --show-toplevel)}" || exit 0

session_id=$(jq -r '.session_id // "unknown"')
counter_file="${TMPDIR:-/tmp}/kanuchi-lint-on-stop-${session_id}"

# 未コミット（ステージ済み・未ステージ・未追跡）の .kt / .kts ファイル
changed=$(git status --porcelain --untracked-files=all | awk '{print $NF}' | grep -E '\.kts?$' | grep -v '/build/')
if [ -z "$changed" ]; then
  rm -f "$counter_file"
  exit 0
fi

tasks=()
while IFS= read -r module; do
  case "$module" in
    domain | data | presentation | app-wasmjs) tasks+=(":$module:ktlintCheck" ":$module:detekt") ;;
    ROOT) tasks+=(":ktlintCheck") ;;
  esac
done < <(echo "$changed" | awk -F/ '{ print (NF == 1 ? "ROOT" : $1) }' | sort -u)

[ ${#tasks[@]} -eq 0 ] && exit 0

if output=$(./gradlew "${tasks[@]}" --quiet --continue 2>&1); then
  rm -f "$counter_file"
  exit 0
fi

attempts=$(($(cat "$counter_file" 2>/dev/null || echo 0) + 1))
echo "$attempts" >"$counter_file"

# 違反箇所（ファイル:行:列）の行だけを抜き出す。見つからなければ出力の末尾を返す。
# detekt はコンソールに出すが、ktlint は --quiet だと出力に載るレポートファイルにしか書かないため、そこから読む（ANSI の色は除く）
ktlint_reports=$(echo "$output" | sed -nE 's/^ *- (.*ktlint.*\.txt)$/\1/p' | xargs -I{} cat {})
violations=$(printf '%s\n%s\n' "$output" "$ktlint_reports" | sed -E $'s/\x1b\\[[0-9;]*m//g' |
  grep -E '\.kts?:[0-9]+:[0-9]+' | sort -u | head -40)
[ -z "$violations" ] && violations=$(echo "$output" | tail -30)

if [ "$attempts" -gt "$MAX_ATTEMPTS" ]; then
  rm -f "$counter_file"
  jq -n --arg msg "lint（${tasks[*]}）が ${MAX_ATTEMPTS} 回の差し戻し後も失敗しています。人の判断が必要です。" \
    '{systemMessage: $msg}'
  exit 0
fi

jq -n --arg reason "作業終了前の lint（${tasks[*]}）が失敗しました（差し戻し ${attempts}/${MAX_ATTEMPTS} 回目）。
ルール違反は原則コード側を直してください（copilot-construction.md「3. コード規約（共通）」）。
${violations}" '{decision: "block", reason: $reason}'
exit 0
