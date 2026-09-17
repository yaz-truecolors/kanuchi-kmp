# Supabase セットアップ

## プロジェクト情報

- Project URL: `https://azvfmvyquyrgzbkdfkga.supabase.co`
- Region: Northeast Asia (Tokyo)
- 認証方式: マジックリンク（メールアドレスのみ）
- APIキー: 新方式の **publishable key**（`sb_publishable_...`）を使用する。
  Legacy の `anon key` は Supabase側で2026年末に廃止予定のため使用しない。
  publishable key はクライアントに同梱される前提の公開可能な値（RLSで保護される）。
  `service_role` / secret key は絶対にクライアントコードに含めないこと。

## マイグレーションの適用方法

このリポジトリでは Supabase CLI をリンクしていないため、`supabase/migrations/` 配下の
SQLファイルは **Supabase Studio の SQL Editor** で手動適用する運用とする。

適用順序（ファイル名の日時順、これより前後させないこと）：

1. `20260917010000_initial_schema.sql` — テーブル定義
2. `20260917010100_functions_and_triggers.sql` — 関数・トリガー（updated_at自動更新、
   新規ユーザーのprofile自動作成、role変更の権限チェック）
3. `20260917010200_row_level_security.sql` — RLS有効化・権限GRANT・ポリシー定義

適用手順：

1. Supabase Studio → 左メニュー **SQL Editor** を開く
2. 上記1〜3のファイルの中身を **この順番で** 貼り付けて実行する
3. エラーが出ないことを確認する

## 初期admin（最初の管理者）の設定

docs/requirements.md の方針どおり、最初の1人だけは手動でadmin化する。

1. マジックリンクでその人が一度アプリにログインする（`profiles`行が自動作成される）
2. Supabase Studio → **Table Editor** → `profiles` テーブルを開く
3. 対象ユーザーの `role` 列を `member` から `admin` に直接書き換える

**注意**：`profiles.role` の変更は、アプリ経由（PostgREST API、実行ロール`authenticated`）の場合のみ
「adminだけが行える」よう制限されている。Supabase Studio の Table Editor / SQL Editor は
直接DB接続（実行ロールは`postgres`等）で行われるため、この制限の対象外であり、
上記の手動操作は問題なく行える。（詳細は `functions_and_triggers.sql` の
`enforce_role_change_permission()` のコメントを参照）

## RLS（Row Level Security）方針の要約

| テーブル | 参照 | 追加・変更・削除 |
|---|---|---|
| profiles | 本人 or admin | 本人 or admin（ただし role の変更は admin のみ、トリガーで強制） |
| projects | 全員 | admin のみ |
| user_projects | 本人 or admin | admin のみ |
| shift_settings | 本人のみ（adminの例外なし） | 本人のみ |
| work_records | 本人 or admin（参照のみ） | 本人のみ |
| allocations | 本人 or admin（参照のみ、work_records経由で判定） | 本人のみ（work_records経由で判定） |

未ログイン（`anon`ロール）には一切のテーブル権限を付与していない
（マジックリンク認証必須のため、`authenticated`ロールにのみGRANTしている）。

## 動作検証について

上記マイグレーションは、ローカルのDocker上に一時的なPostgresコンテナを立て、
Supabaseの `auth.users` / `auth.uid()` / `auth.role()` を模した最小限のシムを用意した上で、
以下のシナリオを実際に実行して検証済み：

- 新規ユーザー作成時に `profiles` 行が自動作成される
- 直接DB接続（初期admin設定を想定）ではrole変更が制限なく行える
- 一般ユーザーが自分の `role` を昇格しようとすると拒否される
- adminは他ユーザーを昇格できる
- `projects` の作成はadminのみ、参照は全員可能
- `work_records` / `allocations` は本人のみ編集可、adminは参照のみ可能、他人はアクセス不可
- `shift_settings` は本人のみ（adminの例外なし）
- `shift_settings` の不正な値（終業 <= 始業 等）はCHECK制約で拒否される
