#!/usr/bin/env bash
# マイグレーションの順番のチェック。ローカルと CI（ci.yml の migration-order ジョブ）で共通。
#
# 使い方: ./supabase/tests/check-migration-order.sh [ベースの ref]（省略時は origin/main。事前に git fetch しておく）
#
# 次の2つを確かめる。どちらかが崩れると、main へのマージ後に supabase-deploy.yml の `supabase db push`（--include-all なし）が
# 止まる。DBテスト（run.sh）はファイル名順に空の DB へ適用するため、どちらも検出できない。
# 1. ベースにあるマイグレーション（main なら本番に適用済み）が、HEAD でもすべて同じ名前で残っている（削除・名前の変更をしていない）
# 2. HEAD にあってベースに無いマイグレーション（この PR が追加・ファイル名を変えたもの）のタイムスタンプが、
#    ベースにあるマイグレーションの最新のタイムスタンプより後である
# CI（pull_request）では HEAD は PR を main に取り込んだ merge コミットなので、main の最新のファイルは必ず HEAD にある。
# ローカルで、main より古いブランチのまま実行すると 1 で失敗するため、main を取り込んでから実行する。
set -euo pipefail
export LC_ALL=C # sort と comm の並び順をそろえる

cd "$(dirname "$0")/../.."

base_ref="${1:-origin/main}"
dir="supabase/migrations"

if ! git rev-parse --verify --quiet "${base_ref}^{commit}" >/dev/null; then
  echo "ベースの ref が見つかりません: ${base_ref}（git fetch してから実行してください）" >&2
  exit 2
fi

list_migrations() {
  git ls-tree --name-only "$1" "$dir/" | sed "s|^$dir/||" | grep -E '\.sql$' | sort || true
}

base_files=$(list_migrations "$base_ref")
head_files=$(list_migrations HEAD)
new_files=$(comm -13 <(echo "$base_files") <(echo "$head_files") | sed '/^$/d')
removed_files=$(comm -23 <(echo "$base_files") <(echo "$head_files") | sed '/^$/d')

if [ -n "$removed_files" ]; then
  while IFS= read -r f; do
    echo "NG: ${base_ref} にある $dir/$f が HEAD にありません（削除・名前の変更）。" >&2
  done <<<"$removed_files"
  cat >&2 <<EOF

${base_ref} にあるマイグレーション（main なら本番に適用済み）は、削除・名前の変更をしないでください。修正は新しいマイグレーションを追加して行います。
ブランチが ${base_ref} より古いだけの場合は、${base_ref} を取り込んで（git merge ${base_ref}）から実行し直してください。
EOF
  exit 1
fi

if [ -z "$new_files" ]; then
  echo "この変更で追加されたマイグレーションはありません。"
  exit 0
fi

# ファイル名の先頭の数字（Supabase CLI のバージョン＝タイムスタンプ）を取り出す
timestamp_of() { sed -nE 's/^([0-9]+)_.*\.sql$/\1/p' <<<"$1"; }

latest_base=""
while IFS= read -r f; do
  [ -z "$f" ] && continue
  ts=$(timestamp_of "$f")
  if [ -n "$ts" ] && { [ -z "$latest_base" ] || [ "$ts" -gt "$latest_base" ]; }; then
    latest_base=$ts
  fi
done <<<"$base_files"

failed=0
while IFS= read -r f; do
  ts=$(timestamp_of "$f")
  if [ -z "$ts" ]; then
    echo "NG: $dir/$f のファイル名が <タイムスタンプ>_<内容>.sql の形式ではありません。" >&2
    failed=1
  elif [ -n "$latest_base" ] && [ "$ts" -le "$latest_base" ]; then
    echo "NG: $dir/$f のタイムスタンプ（$ts）が、${base_ref} の最新のマイグレーション（$latest_base）以前です。" >&2
    failed=1
  else
    echo "OK: $dir/$f"
  fi
done <<<"$new_files"

if [ "$failed" -ne 0 ]; then
  cat >&2 <<EOF

この変更で追加したマイグレーションのファイル名のタイムスタンプを、${latest_base} より後に振り直してください（元の順番は保つ）。
本番に未適用のファイルなので、ファイル名を変えてかまいません（内容は変えない）。
振り直したら ./supabase/tests/run.sh で DBテストが通ることも確かめてください。
EOF
  exit 1
fi
