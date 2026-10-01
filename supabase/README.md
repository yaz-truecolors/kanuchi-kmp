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

## ログイン後の戻り先URL（Site URL / Redirect URLs）の設定

マジックリンクを開いた後に戻る先（アプリのURL）は、Supabase 側で許可されている必要がある。
アプリは今開いているページのURL（例: `https://yaz-truecolors.github.io/kanuchi-kmp/`）を戻り先として送るが、
許可されていないURLの場合、Supabase は **Site URL** へ戻す。Site URL が既定の `http://localhost:3000` のままだと、
リンクを開いてもアプリに戻れない（ブラウザにエラーページが表示される）。

本番（ダッシュボード）で1回だけ設定する：

1. Supabase Studio → **Authentication → URL Configuration** を開く
2. **Site URL** を `https://yaz-truecolors.github.io/kanuchi-kmp/` にして保存する
3. **Redirect URLs** に以下を追加する
   - `https://yaz-truecolors.github.io/kanuchi-kmp/**`（本番）
   - `http://127.0.0.1:8081/**`（ローカルで本番のSupabaseに接続して確認する場合。`serveDistribution` の既定URL）

ローカルの Supabase（`supabase start`）では `config.toml` の `[auth]` の `site_url` / `additional_redirect_urls` で
同じ設定をしている（`serveDistribution` の `http://127.0.0.1:8081/` に合わせている）。

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

同様に、「自分自身の降格・利用停止の禁止」「利用中のadminが1人もいなくなる降格・利用停止の禁止」
（`guard_active_admins()`、下記「利用停止（除名）・復帰」参照）もアプリ経由の場合のみ適用される。
Studio から直接 role・`suspended_at` を書き換えるとこれらの確認を迂回するため、adminを0人にしないよう注意する
（誤ってadminがいなくなった場合の復旧も、Studio から上記の手順で行う）。

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

通常は、adminがアプリの **ユーザー管理** 画面（ホーム画面の管理者メニュー）で招待リストに追加する
（一覧・取り消しも同じ画面で行える。メールアドレスは自動で小文字等に正規化される）。
招待メールは自動送信されないため、追加後に本人へアプリのURLを伝える。
本人がログイン画面でメールアドレスを入力すると、アカウントが作成されマジックリンクが届く。

アプリを使えない場合（初期adminの登録等）は、Supabase Studioから登録する。

1. Supabase Studio → **Table Editor** → `invitations` テーブル → **Insert row**
2. `email` に招待するメールアドレスを入力して保存（大文字・前後の空白は自動で正規化される）。
   `invited_by`（招待したadmin）は空欄のままでよい。Table Editorは直接DB接続のため
   ログインユーザーが存在せず、自動設定されない（空欄＝Studioから登録した、という意味になる）
3. 招待した本人にアプリのURLを伝える（招待メールは自動送信されない）
4. 本人がログイン画面でメールアドレスを入力すると、アカウントが作成されマジックリンクが届く

招待を取り消す場合は、ユーザー管理画面の「取り消し」（または `invitations` の行を削除）。
作成済みのアカウントは削除されないため、既にログインしたことがある人をログインできなくするには
下記「利用停止（除名）・復帰」を行う。

**注意**：Hookは Supabase Studio の「Invite user」「Add user」でも実行される。
Studioから直接ユーザーを追加する場合も、先に `invitations` への登録が必要。

## 利用停止（除名）・復帰

adminはアプリの **ユーザー管理** 画面で、ユーザーを利用停止・復帰させられる
（`migrations/20261001100000_user_suspension_and_admin_guard.sql`）。**ダッシュボードでの設定は不要**
（マイグレーションの適用だけで有効になる）。

- 利用停止したユーザーはログインできなくなる。アカウント（`profiles`）と過去の稼働記録は削除されず、集計に使える。
  復帰させると再びログインできる。
  - 仕組み：`profiles.suspended_at` に利用停止した日時を記録し、あわせて `auth.users.banned_until` を
    遠い未来に設定する（Supabase Auth がログインを拒否する）。ログイン中の端末のセッションも削除する。
  - 利用停止中のユーザーがログイン画面でメールアドレスを入力すると、**マジックリンクのメール自体は届く**
    （Supabase Auth の仕様）が、リンクを開くとログイン画面に「利用停止中」のエラーが表示される。
  - 利用停止の時点でログイン中だった端末は、発行済みのアクセストークン（最長1時間有効）が残るが、
    PostgREST の pre-request 関数（`reject_suspended_user`）が利用停止中のユーザーの Data API へのアクセスを
    すべて拒否する（HTTP 403）。画面には読み込みエラーが表示され、トークンの更新時にログアウトされる。
- 自分自身の利用停止、および利用中のadminが1人もいなくなる利用停止・降格はできない（DB 側のトリガーで拒否する。
  2人のadminが互いを同時に操作した場合も、ロックで直列化して0人にならないようにしている）。
- 利用停止中のadminは、adminとして扱われない（`is_admin()` が false になる）。
- **Studio の「Ban user」（Authentication → Users）は使わない**。`profiles.suspended_at` が更新されず、
  アプリの一覧では「利用中」と表示されてしまう。利用停止・復帰は必ずアプリ（`set_user_suspended()`）から行う。
  アプリを使えない場合は SQL Editor で
  `update public.profiles set suspended_at = null where email = '...'` と
  `update auth.users set banned_until = null where email = '...'`（復帰の例）のように両方を更新する。
- pre-request 関数は、マイグレーションで `authenticator` ロールの設定（`pgrst.db_pre_request`）として登録している
  （Supabase のドキュメント「Securing your API」の方法）。設定の確認は SQL Editor で
  `select * from pg_db_role_setting where setrole = 'authenticator'::regrole;`。

## RLS（Row Level Security）方針の要約

| テーブル | 参照 | 追加・変更・削除 |
|---|---|---|
| profiles | 本人 or admin | 本人 or admin（ただし role の変更は admin のみ、トリガーで強制。自分自身の降格・利用中のadminが0人になる降格は不可。`suspended_at` は直接変更できず、`set_user_suspended()`（adminのみ）で変更する） |
| projects | 全員 | admin のみ |
| user_projects | 本人 or admin | admin のみ |
| shift_settings | 本人のみ（adminの例外なし） | 本人のみ |
| work_records | 本人 or admin（参照のみ） | 本人のみ |
| allocations | 本人 or admin（参照のみ、work_records経由で判定） | 本人のみ（work_records経由で判定） |
| invitations | admin のみ | admin のみ（追加・削除。アプリ経由の追加は email 列のみで、invited_by は登録したadminに自動設定。Studioからの登録は invited_by が空になる） |

未ログイン（`anon`ロール）には一切のテーブル権限（参照・追加・変更・削除を含むすべて）を付与していない
（マジックリンク認証必須のため、`authenticated`ロールにのみGRANTしている）。
`authenticated` にもアプリが使う参照・追加・変更・削除（列単位を含む）以外の権限は付与していない。

Supabase の既定権限（`ALTER DEFAULT PRIVILEGES`）により、`public` スキーマに作ったテーブルには
`anon` / `authenticated` へ `TRUNCATE`（RLS を迂回する）・`REFERENCES`・`TRIGGER`・`MAINTAIN` が、
シーケンスには `anon` へ `UPDATE` が自動で付与される。これらは
`migrations/20260925090000_revoke_excess_table_privileges.sql` で既存のオブジェクトから剥奪し、
マイグレーションを実行する `postgres` ロールの既定権限からも取り除いている
（今後 `postgres` が `public` に作るテーブル・シーケンスには付与されない）。
`supabase_admin` ロールの既定権限は `postgres` から変更できないため調整していない
（`supabase_admin` が `public` にオブジェクトを作ると `anon` に権限が付与される。マイグレーションで `postgres` が作るテーブル等は該当しない）。
関数の `EXECUTE` 権限（PostgreSQL の既定で `PUBLIC` に付与される）と `service_role` の権限は変更していない。

## 動作検証について（DBテスト）

マイグレーションのアクセス制御ルール（RLS・トリガー・招待制Hook）は、pgTAP によるDBテスト
（`supabase/tests/database/*.test.sql`）で自動検証している。Supabase CLI の `supabase test db` を使い、
Supabase の実際の Postgres イメージ（`auth.users` / `auth.uid()` / `supabase_auth_admin` 等が本物）に
全マイグレーションを適用した状態でテストする。CI（`.github/workflows/ci.yml` の `db-test` ジョブ）で
全PR・`main` へのpushごとに実行される。

### 実行方法（ローカル）

Docker（起動済み）と [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started) が必要。
リポジトリのルートで以下を実行する（Postgres だけを起動し、未適用のマイグレーションを適用してからテストする）。

```sh
./supabase/tests/run.sh
```

- CI は Supabase CLI のバージョンを固定している（`ci.yml` / `supabase-deploy.yml` の `SUPABASE_CLI_VERSION`）。
  ローカルの CLI が大きく異なる場合に結果が変わるときは、同じバージョンに合わせる。
- 各テストファイルは `begin; ... rollback;` で実行されるため、ローカルDBのデータは変更しない。
  テストデータのメールアドレスは `@db-test.invalid` を使う。
- 適用済みのマイグレーションファイルを書き換えた場合は反映されないため、`supabase db reset` してから再度実行する
  （`db reset` はローカルDBのデータを消す）。
- 終わったら `supabase stop` で停止できる。
- Docker が必要なため、`./gradlew verify` には含めていない（`verify` は Docker の無い環境でも実行できるようにしている）。
  `supabase/` 配下を変更したPRでは、PR作成前にこのスクリプトも実行すること。

### テストの構成

| ファイル | 検証内容 |
|---|---|
| `helpers.psql` | 共通ヘルパー（テストではない）。テストユーザーの作成（`auth.users` への INSERT で実際のトリガーを発火させる）、ログイン状態の再現（実行ロールを `authenticated` / `anon` に切り替え、`request.jwt.claims` を設定する。PostgREST がリクエストごとに行っている設定と同じ） |
| `00_schema_security.test.sql` | `public` スキーマの全テーブルを動的に走査し、RLS が有効・ポリシーがある・`authenticated` に権限がある・`anon` に一切の権限が無い（列単位も含む）・`authenticated` に `TRUNCATE` 等の余分な権限が無く、アプリが使う権限（列単位を含む）の一覧が想定どおり・`anon` / `PUBLIC` 向けのポリシーが無い・ビューが `security_invoker` であることを検証する。`postgres` ロールの既定権限が調整済みであること（実際にテーブルを作って確認する）も検証する。テーブル一覧（`tables_are`）と `authenticated` の権限の一覧も検証するため、テーブルや grant を追加するとテストの追加・更新を促す形で失敗する |
| `01_profiles.test.sql` | `profiles` 行の自動作成（`display_name` の初期値含む）・`auth.users.email` 変更の同期、直接DB接続では role を変更できること、member は自分の role を昇格できないこと、admin は他ユーザーを昇格・降格できること、`email` 列を直接変更できないこと、RLS（本人/他人/admin/anon × 参照/追加/変更/削除） |
| `02_projects.test.sql` | RLS（member/admin/anon × 参照/追加/変更/削除） |
| `03_user_projects.test.sql` | RLS（本人/他人/admin/anon × 参照/追加/変更/削除） |
| `04_shift_settings.test.sql` | RLS（本人/他人/admin/anon × 参照/追加/変更/削除。adminの例外なし）、CHECK制約（終業 <= 始業、休憩・下限が負、下限 > 上限）と境界値 |
| `05_work_records.test.sql` | RLS（本人/他人/admin/anon × 参照/追加/変更/削除。adminは参照のみ、他人への付け替え不可）、CHECK制約（`flag`・休憩）、1ユーザー1日1件の一意制約 |
| `06_allocations.test.sql` | RLS（work_records 経由の所有者判定。本人/他人/admin/anon × 参照/追加/変更/削除、他人の work_records への付け替え不可）、CHECK・一意・外部キー（`on delete restrict` / `cascade`）制約 |
| `07_invitations.test.sql` | RLS（admin/member/anon × 参照/追加/変更/削除）、メールアドレスの正規化（追加・変更時）、`invited_by` の自動設定と偽装の拒否、直接DB接続での登録（`invited_by` が空）、CHECK制約（形式・正規化）、重複の拒否、招待したadmin削除時の `on delete set null` |
| `08_hook_before_user_created.test.sql` | Hook 関数を SQL から直接呼び出し、招待済みは許可（`{}`）・未招待は拒否（`{"error": {"http_code": 403, "message": "email_not_invited"}}`）、正規化した照合、メールアドレスが無い/空のイベントの拒否、招待取り消し後の拒否。`PUBLIC` / `anon` / `authenticated` に `EXECUTE` 権限が無く呼び出すと権限エラーになること、`supabase_auth_admin` は実行できること、`SECURITY DEFINER` であること |
| `09_user_suspension.test.sql` | `set_user_suspended()` による利用停止・復帰（`suspended_at`・`auth.users.banned_until`・セッション削除）、admin 以外（member・利用停止中のadmin・anon）は実行できないこと、自分自身の利用停止・降格の拒否、利用中のadminが0人になる利用停止・降格の拒否、`suspended_at` を直接変更できないこと、`is_admin()` が利用停止中のadminを除外すること、pre-request 関数 `reject_suspended_user()` が利用停止中のユーザーだけを拒否すること。ローカルDBに開発用のadminがいても検証できるよう、テスト内（rollback される）で `@db-test.invalid` 以外のadminを降格してから実行する |

### 自動テストでは検証していないもの

以下は実物の Supabase Auth（HTTP）やダッシュボード設定が関わるため、DBテストの対象外。
Hook・マイグレーションを変更した場合は、`supabase start` によるローカルのSupabase一式で手動確認する。

- 未招待のメールアドレスでのマジックリンク要求が HTTP 403（`msg: "email_not_invited"`）になり、
  アプリ画面に「このメールアドレスは登録されていません」と表示されること
  （Hook 関数の戻り値はDBテストで検証しているが、Supabase Auth がそれをHTTPレスポンスに変換する部分は検証していない）
- 招待済みのメールアドレスでマジックリンクが送信されること
- Admin API（Studio の「Invite user」相当）でも Hook が実行され、未招待のメールアドレスが拒否されること
- Data API（PostgREST）の `rpc` 経由で Hook 関数を呼び出せないこと（DBテストでは `EXECUTE` 権限と、
  `anon` / `authenticated` として呼び出したときの権限エラーで担保している）
- 本番の Hook 有効化・「Allow new users to sign up」等のダッシュボード設定（マイグレーションでは管理できない）
- 利用停止中のユーザーがマジックリンクを開くと、Supabase Auth が `#error_code=user_banned` でアプリに戻り、
  ログイン画面に「利用停止中」と表示されること（`banned_until` の設定はDBテストで検証している）
- pre-request 関数が PostgREST に登録され、利用停止中のユーザーの Data API へのリクエストが HTTP 403 になること
  （関数の動作はDBテストで検証しているが、PostgREST への登録・呼び出しは検証していない）
- 2人のadminが互いを同時に降格・利用停止したときに、後の操作がロック待ちの後に拒否されること
  （DBテストは1つのトランザクションで実行するため、同時実行は再現できない）
