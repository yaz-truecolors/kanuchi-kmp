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

## 新規登録（サインアップ）の設定について

アカウント作成は招待制。アプリ側の `createUser` はクライアントが送るパラメータにすぎず、
publishable key（公開値）で `/auth/v1/otp` を直接呼び出されると防げないため、
**防御は必ずサーバー側（Supabase の Auth 設定・Hook）で行う**。設定手順は下記
「招待制（招待リスト＋Before User Created Hook）」を参照。

現在の「Allow new users to sign up」の状態は以下で確認できる
（`"disable_signup": true` なら新規登録OFF、`false` なら ON）。

```bash
curl -s https://azvfmvyquyrgzbkdfkga.supabase.co/auth/v1/settings \
  -H "apikey: <publishable key>" | grep -o '"disable_signup":[a-z]*'
```

## 初期admin（最初の管理者）の設定

docs/requirements.md の方針どおり、最初の1人だけは手動でadmin化する。

1. その人のメールアドレスを招待リスト（`invitations`）に登録し、アプリのログイン画面から
   一度ログインしてもらう（`profiles`行が自動作成される）。
   新規プロジェクトの場合は、下記「本番での初回セットアップ」の手順3で登録する
2. Supabase Studio → **Table Editor** → `profiles` テーブルを開く
3. 対象ユーザーの `role` 列を `member` から `admin` に直接書き換える

**注意**：`profiles.role` の変更は、アプリ経由（PostgREST API、実行ロール`authenticated`）の場合のみ
「adminだけが行える」よう制限されている。Supabase Studio の Table Editor / SQL Editor は
直接DB接続（実行ロールは`postgres`等）で行われるため、この制限の対象外であり、
上記の手動操作は問題なく行える。（詳細は `functions_and_triggers.sql` の
`enforce_role_change_permission()` のコメントを参照）

## 招待制（招待リスト＋Before User Created Hook）

アカウントは、招待リスト（`public.invitations`）に登録されたメールアドレスでのみ作成できる。
未招待のメールアドレスは、Supabase Authの Before User Created Hook（`public.hook_before_user_created`）が
ユーザー作成直前に拒否する（`migrations/20260924150000_invitations_and_signup_hook.sql`）。

### 本番での初回セットアップ（1回だけ・順番厳守）

Hookの有効化はマイグレーションでは行えないため、ダッシュボードで手動設定する。
**Hookが無効なまま「Allow new users to sign up」をONにすると、誰でもアカウントを作れてしまう**ため、
必ず以下の順番で行うこと。

1. （マージ前）Supabase Studio → **Authentication → Sign In / Providers** で
   「**Allow new users to sign up**」が **OFF** になっていることを確認する（OFFにしておく）。
   OFFの間は、アプリ側が新規作成を要求してもSupabaseが拒否するため安全。
2. PRをマージし、GitHub Actions（Supabase Deploy）でマイグレーションが適用されたことを確認する。
3. （新規プロジェクトなど、まだ誰もアカウントを持っていない場合のみ）Supabase Studio →
   **Table Editor** → `invitations` に初期adminのメールアドレスを登録する。
   Hookを有効にすると招待リストに無いメールアドレスは一切アカウントを作れなくなるため、
   これを忘れると初期adminもログインできなくなる。既存ユーザーはHookの影響を受けない
   （Hookはユーザー作成時にのみ実行される）ため、既にアカウントがある人の登録は不要。
4. Supabase Studio → **Authentication → Hooks** → **Add hook** → **Before User Created** を選び、
   - Hook type: **Postgres**
   - Schema: `public`
   - Function: `hook_before_user_created`

   を指定して保存（有効化）する。
5. 手順1の「**Allow new users to sign up**」を **ON** にする。
6. 動作確認：招待リストに無いメールアドレスでログインを試し、「このメールアドレスは登録されていません」と
   表示されることを確認する。

### 新しいメンバーを招待する（日常運用）

アプリ内の招待画面ができるまでは、Supabase Studioから登録する。

1. Supabase Studio → **Table Editor** → `invitations` テーブル → **Insert row**
2. `email` に招待するメールアドレスを入力して保存（大文字・前後の空白は自動で正規化される）。
   `invited_by`（招待したadmin）は空欄のままでよい。Table Editorは直接DB接続のため
   ログインユーザーが存在せず、自動設定されない（空欄＝Studioから登録した、という意味になる）
3. 招待した本人にアプリのURLを伝える（招待メールは自動送信されない）
4. 本人がログイン画面でメールアドレスを入力すると、アカウントが作成されマジックリンクが届く

招待を取り消す場合は `invitations` の行を削除する（作成済みのアカウントは削除されない。
アカウント自体を削除する場合は **Authentication → Users** から行う）。

**注意**：Hookは Supabase Studio の「Invite user」「Add user」でも実行される。
Studioから直接ユーザーを追加する場合も、先に `invitations` への登録が必要。

## RLS（Row Level Security）方針の要約

| テーブル | 参照 | 追加・変更・削除 |
|---|---|---|
| profiles | 本人 or admin | 本人 or admin（ただし role の変更は admin のみ、トリガーで強制） |
| projects | 全員 | admin のみ |
| user_projects | 本人 or admin | admin のみ |
| shift_settings | 本人のみ（adminの例外なし） | 本人のみ |
| work_records | 本人 or admin（参照のみ） | 本人のみ |
| allocations | 本人 or admin（参照のみ、work_records経由で判定） | 本人のみ（work_records経由で判定） |
| invitations | admin のみ | admin のみ（追加・削除。アプリ経由の追加は email 列のみで、invited_by は登録したadminに自動設定。Studioからの登録は invited_by が空になる） |

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

招待リスト・Hook（`20260924150000_invitations_and_signup_hook.sql`）は、`supabase start` による
ローカルのSupabase一式（実物のSupabase Auth）で以下を検証済み：

- 未招待のメールアドレスでのマジックリンク要求は HTTP 403（`msg: "email_not_invited"`）で拒否され、
  アカウントは作成されない。アプリ画面には「このメールアドレスは登録されていません」と表示される
- 招待済みのメールアドレスでは、アカウントと `profiles` 行が作成され、マジックリンクが送信される
- 大文字・前後空白を含む登録も正規化され、照合できる
- Admin API（Studioの「Invite user」相当）でも、未招待のメールアドレスは拒否される
- `invitations` の参照・追加・削除はadminのみ。一般ユーザー・未ログインはアクセス不可
- アプリ経由（`authenticated`）での登録では `invited_by` が自動で登録者本人になり、他人のIDを指定した偽装は拒否される
  （Studioからの直接登録では `invited_by` は空になる）
- Hook関数は Data API（`rpc`）から `anon` / `authenticated` で呼び出せない
