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

`main` ブランチへのマージ時、GitHub Actions (`.github/workflows/supabase-deploy.yml`) が
Supabase CLI (`supabase db push`) を使って `supabase/migrations/` 配下の未適用マイグレーションを
自動的に本番プロジェクトへ適用する。手動でSQL Editorに貼り付ける必要はない。

### 初回セットアップ（リポジトリ管理者が1回だけ行う）

CIから利用するため、以下3つをGitHubリポジトリの **Settings → Secrets and variables → Actions**
に登録する（値はいずれもチャットやコード上で共有しないこと）。

| Secret名 | 値の取得元 |
|---|---|
| `SUPABASE_ACCESS_TOKEN` | Supabaseダッシュボード右上のアカウントメニュー → *Access Tokens* で発行する個人アクセストークン |
| `SUPABASE_DB_PASSWORD` | プロジェクト作成時に設定したデータベースパスワード |
| `SUPABASE_PROJECT_ID` | プロジェクトURLの `https://<project-id>.supabase.co` の `<project-id>` 部分（例: `azvfmvyquyrgzbkdfkga`） |

登録後は、`supabase/migrations/` に新しいSQLファイルを追加して `main` にマージするだけで
自動的に反映される。ローカルで動作確認したい場合や、CIを使わず手動適用したい場合は、
Supabase CLIをインストールした上で以下を実行する。

```sh
supabase login
supabase link --project-ref <project-id>
supabase db push
```

（もしくは、Supabase Studio → SQL Editor に `supabase/migrations/` 配下のSQLファイルを
日時順に貼り付けて実行しても同じ結果になる）

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
