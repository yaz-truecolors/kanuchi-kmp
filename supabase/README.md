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

### `SUPABASE_ACCESS_TOKEN` の有効期限切れ・更新手順

個人アクセストークンには有効期限（Expiration）を設定できる（推奨）。期限が切れると
`supabase-deploy.yml` の実行が認証エラーで失敗するため、以下の手順で更新する。

1. [Supabaseダッシュボード](https://supabase.com/dashboard/account/tokens) にログインし、
   右上のアカウントメニュー → **Access Tokens** を開く
2. 期限切れの古いトークンを **Revoke**（失効）する
3. **Generate new token** で新しいトークンを発行する（名前は `github-actions-kanuchi` など、
   用途がわかるものにする。有効期限は運用ポリシーに応じて設定する）
4. 発行された値をコピーする（この画面を閉じると二度と表示されないので注意）
5. GitHubリポジトリの **Settings → Secrets and variables → Actions** を開き、
   `SUPABASE_ACCESS_TOKEN` を選択して **Update secret** に新しい値を貼り付けて保存する
6. `.github/workflows/supabase-deploy.yml` を **workflow_dispatch**（手動実行）で1回動かし、
   正常に完了することを確認する

`SUPABASE_DB_PASSWORD` / `SUPABASE_PROJECT_ID` には有効期限の概念はないため、
プロジェクトのDBパスワードをリセットしない限り更新不要。

（もしくは、Supabase Studio → SQL Editor に `supabase/migrations/` 配下のSQLファイルを
日時順に貼り付けて実行しても同じ結果になる）

## 新規登録（サインアップ）の無効化【必須】

アカウント作成は招待制のため、本番の Auth 設定で新規登録を無効化する。
アプリ側の `createUser = false` はクライアントが送るパラメータにすぎず、
publishable key（公開値）で `/auth/v1/otp` を直接呼び出されると防げないため、**この設定が実際の防御になる**。

1. Supabase Studio → **Authentication → Sign In / Providers** を開く
2. 「**Allow new users to sign up**」を **OFF** にして保存する

現在の設定は以下で確認できる（`"disable_signup": true` であれば無効化済み）。

```bash
curl -s https://azvfmvyquyrgzbkdfkga.supabase.co/auth/v1/settings \
  -H "apikey: <publishable key>" | grep -o '"disable_signup":[a-z]*'
```

新規登録を無効にしても、既存ユーザーのログインと、Studio の「Invite user」による招待は引き続き行える。

## 初期admin（最初の管理者）の設定

docs/requirements.md の方針どおり、最初の1人だけは手動でadmin化する。

1. Supabase Studio → **Authentication → Users → Invite user** でその人を招待し、
   届いたメールのリンクから一度アプリにログインしてもらう（`profiles`行が自動作成される）
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
