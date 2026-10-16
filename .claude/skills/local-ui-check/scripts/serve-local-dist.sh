#!/usr/bin/env bash
# build-local-dist.sh の出力を http://127.0.0.1:${PORT:-8081}/ でキャッシュ無効で配信する（e2e/serve.js を使う）。
# serveDistribution は本番の設定で再ビルドしてしまうため、ローカル Supabase 向けのビルドはこちらで配信する。
set -euo pipefail

cd "$(git rev-parse --show-toplevel)"
OUT_DIR=${OUT_DIR:-app-wasmjs/build/local-supabase-dist}
[[ -f "$OUT_DIR/index.html" ]] || { echo "$OUT_DIR がありません。先に build-local-dist.sh を実行してください。" >&2; exit 1; }

# Kotlin Gradle プラグインがダウンロードした Node.js を使う（PATH に node が無くても動くように）
node=$(ls -d "$HOME"/.gradle/nodejs/node-*/bin/node 2>/dev/null | tail -1 || true)
node=${node:-$(command -v node)}

DIST_DIR="$OUT_DIR" PORT="${PORT:-8081}" exec "$node" e2e/serve.js
