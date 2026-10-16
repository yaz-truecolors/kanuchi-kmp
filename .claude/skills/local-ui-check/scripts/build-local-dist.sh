#!/usr/bin/env bash
# ローカル Supabase（supabase start）に接続する本番ビルドを作り、OUT_DIR にコピーする。
# SupabaseConfig.kt をビルドの間だけローカル値に書き換え、終了時（失敗・中断を含む）に必ず元に戻す。
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
CONFIG=data/src/commonMain/kotlin/jp/co/yaz/kanuchi/data/auth/SupabaseConfig.kt
DIST=app-wasmjs/build/dist/wasmJs/productionExecutable
OUT_DIR=${OUT_DIR:-app-wasmjs/build/local-supabase-dist}

if ! git diff --quiet -- "$CONFIG"; then
  echo "$CONFIG に未コミットの変更があります。前回の書き換えが残っていないか確認してください。" >&2
  exit 1
fi

# 必要な2つの値だけを取り出す（secret key 等は読み込まない・表示しない）
status_env=$(supabase status -o env)
api_url=$(printf '%s\n' "$status_env" | sed -n 's/^API_URL="\{0,1\}\([^"]*\)"\{0,1\}$/\1/p')
publishable_key=$(printf '%s\n' "$status_env" | sed -n 's/^PUBLISHABLE_KEY="\{0,1\}\([^"]*\)"\{0,1\}$/\1/p')
if [[ -z "$api_url" || -z "$publishable_key" ]]; then
  echo "supabase status から API_URL / PUBLISHABLE_KEY を取得できません（supabase start 済みか確認してください）。" >&2
  exit 1
fi

backup=$(mktemp)
cp "$CONFIG" "$backup"
restore() {
  cp "$backup" "$CONFIG"
  rm -f "$backup"
}
trap restore EXIT

perl -pi -e "s|(PROJECT_URL: String = )\"[^\"]*\"|\$1\"$api_url\"|; s|(PUBLISHABLE_KEY: String = )\"[^\"]*\"|\$1\"$publishable_key\"|" "$CONFIG"

./gradlew :app-wasmjs:wasmJsBrowserDistribution

rm -rf "$OUT_DIR"
cp -R "$DIST" "$OUT_DIR"
echo "ローカル Supabase 向けのビルドを $OUT_DIR に出力しました。"
