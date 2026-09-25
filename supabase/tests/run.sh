#!/usr/bin/env bash
# DBテスト（supabase/tests/database/ の pgTAP テスト）を実行する。ローカルと CI（ci.yml の db-test ジョブ）で共通。
#
# 必要なもの: Docker（起動済み）と Supabase CLI。
# ローカルの Supabase（Postgres のみ）を起動し、未適用のマイグレーションを適用してからテストする。
# `supabase start` で一式を起動済みの場合も、そのまま実行できる。
# 各テストは begin ... rollback で実行されるため、ローカルDBのデータは変更しない。
set -euo pipefail

cd "$(dirname "$0")/../.."

# Postgres だけを起動する（Auth・PostgREST 等のコンテナは起動しない）。起動済みなら何もしない。
supabase db start

# 未適用のマイグレーションを適用する（ローカルの既存データは消さない）。
# 適用済みのマイグレーションファイルを書き換えた場合は反映されないため、`supabase db reset` を実行してから再度実行すること。
supabase migration up --local --include-all

supabase test db
