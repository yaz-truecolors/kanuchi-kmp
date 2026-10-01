---
applyTo: "supabase/**"
---

# Supabase マイグレーション・RLS・Hook（`supabase/`）の規約

マイグレーションSQL・RLSポリシー・トリガー・Auth Hook・DBテスト（`supabase/tests/`）・`supabase/config.toml` を変更するときの
規約・ハマりどころです。
領域共通の原則は [copilot-construction.md](../../copilot-construction.md)、
クライアント側（supabase-kt・Repository 実装）の規約は [data.instructions.md](data.instructions.md) を参照してください。
RLS の権限方針（誰が何を参照・編集できるか）は `docs/requirements.md` の「5. 認証・権限設計」が正です。

## マイグレーション運用

- マイグレーションSQLは `supabase/migrations/` に配置し、ファイル名は
  `<タイムスタンプ>_<内容>.sql` の昇順で適用順を表す（Supabase CLI の規約に準拠）。
- `main` へのマージ時、GitHub Actions (`.github/workflows/supabase-deploy.yml`) が
  `supabase db push` を実行し、未適用のマイグレーションを自動的に本番へ反映する。
  手動でSupabase StudioのSQL Editorに貼り付ける必要はない（緊急時・CI障害時の
  フォールバックとしては引き続き可能）。
  このCIに必要な Secrets とトークンの有効期限の扱いは [ci.instructions.md](ci.instructions.md) を参照。
- `supabase/config.toml` はローカル開発 (`supabase start`) 用の設定。ダッシュボード側の
  Data API設定（`Automatically expose new tables` 等）と値を一致させておくこと
  （`auto_expose_new_tables = false` に設定済み）。
- 新しいテーブルを追加する際は、以下を必ずセットで行うこと（1つでも欠けると
  「テーブルはあるがAPI経由で一切アクセスできない」「RLSが無効なまま公開される」等の
  事故につながる）：
  1. `alter table ... enable row level security;`
  2. `grant select/insert/update/delete on ... to authenticated;`
     （`anon`には付与しない。本プロジェクトはマジックリンク認証必須のため）
  3. 用途に応じた `create policy ...`

  3点セットの漏れは DBテストの `supabase/tests/database/00_schema_security.test.sql` が `public` スキーマの
  全テーブルを走査して検知する（同ファイルのテーブル一覧 `tables_are` と、`authenticated` の権限の一覧
  （`set_eq`）も更新すること）。
- **Supabase の既定権限は余分な権限を付ける（ハマりどころ）。** Supabase は `ALTER DEFAULT PRIVILEGES` で、
  `public` スキーマに作ったテーブルへ `anon` / `authenticated` の `TRUNCATE`（RLS を迂回する）・`REFERENCES`・
  `TRIGGER`・`MAINTAIN` を、シーケンスへ `anon` の `UPDATE` を自動で付与する（`config.toml` の
  `auto_expose_new_tables = false` でも、参照・追加・変更・削除以外はこのとおり付与されることをローカルで確認済み）。
  マイグレーションを実行する `postgres` ロールの既定権限は `*_revoke_excess_table_privileges.sql` で調整済みのため、
  新しいテーブルを追加しても `anon` には何も付与されず、`authenticated` にも余分な権限は付かない
  （その分、`authenticated` への grant は上記2のとおり明示的に行う必要がある）。既定権限の調整・権限の剥奪を
  追加で行う場合は以下に注意する（本番の `supabase db push` が失敗すると、以降のマイグレーションが適用されなくなる）。
  - 既定権限は「誰が作るオブジェクトに対するものか」（`for role <作成ロール>`）ごとに別物。マイグレーションは
    `postgres`（スーパーユーザーではない）で実行されるため、変更できるのは `for role postgres` だけ。
    `for role supabase_admin` 等を変更しようとすると `permission denied to change default privileges` で失敗する。
    現状は `pg_default_acl` で確認する。
  - `MAINTAIN` 権限は PostgreSQL 17 以降にしか無い（16 以前では構文エラー）。ローカルは `config.toml` の
    `major_version` のバージョンで動くが、本番のバージョンと一致している保証は無いため、`MAINTAIN` を書く場合は
    `current_setting('server_version_num')` で判定して動的 SQL で実行する。
  - `revoke all on table ...` はテーブルに対する列単位の grant（`profiles` の `update (display_name, role)` 等）も
    剥奪してしまう（PostgreSQL の REVOKE のドキュメント: テーブルの権限を剥奪すると、各列の同じ権限も自動で剥奪される。
    「列単位の ACL は残る」というレビュー指摘は誤り）。`authenticated` からは剥奪する権限を列挙して `revoke` する。
    逆に `anon` には `revoke all on table ...` で列単位の権限まで剥奪できる。
  - 所有していないオブジェクトに権限を一切持たない場合、`revoke` はエラーになる。スキーマ内の全オブジェクトを
    対象にする場合は、実行ロールが所有するものに絞る（`pg_has_role(current_user, relowner, 'USAGE')`）。
- **適用済み（`main` にマージ済み）のマイグレーションファイルは変更しない。** 本番に適用済みのため、
  修正は新しいマイグレーションファイルを追加して行う。

## DBテスト（`supabase/tests/`）

- **マイグレーションを追加・変更したら、対応するテストを `supabase/tests/database/` に追加・更新すること。**
  新しいテーブルならそのテーブルの RLS（本人/他人/admin/anon × 参照/追加/変更/削除）と制約、
  新しい関数・トリガーならその振る舞いと `EXECUTE` 権限を検証する。テストは pgTAP で書き、
  `supabase test db` で実行する（実行方法・テストの構成は `supabase/README.md` の「動作検証について（DBテスト）」）。
- ローカルでは `./supabase/tests/run.sh`（Docker と Supabase CLI が必要）で、CI（`ci.yml` の `db-test` ジョブ）と
  同じ手順を1コマンドで実行できる。`supabase/` を変更したらPR作成前に実行すること。
  Docker が必要なため `./gradlew verify` には含めていない。
- テストファイルの書き方:
  - 1ファイル = 1トランザクション（`begin;` → `\ir helpers.psql` → `select plan(N);` → テスト → `select * from finish();` → `rollback;`）。
    `plan(N)` の件数はテストの追加・削除に合わせて更新する（ずれると `Bad plan` で失敗する）。
  - ログイン状態は `helpers.psql` の `tests.authenticate_as('<email>')` / `tests.authenticate_as_anon()` /
    `tests.clear_authentication()`（直接DB接続 = 実行ロール `postgres` に戻す）で切り替える。
    実行ロールの切り替えと `request.jwt.claims` の設定という、PostgREST がリクエストごとに行う処理を再現している。
    自前の `auth` シムは作らず、Supabase の実物の `auth.uid()` を使う。
  - RLS で「見えない行」は参照・変更・削除してもエラーにならず0行になるため、`is_empty($$ update ... returning ... $$)` の
    ように **影響行数が0であること** で検証する。権限（GRANT）が無い操作と WITH CHECK 違反はエラー
    （`throws_ok(..., '42501', ...)`）になる。どちらになるかを取り違えないこと。
  - 他人の行は RLS の影響を受けない方法で特定する（固定のUUID、または `tests.user_id('<email>')`）。
    `where id = (select id from 他人の行)` のように RLS の対象テーブルをサブクエリで引くと、サブクエリ自体が
    RLS で空になり、ポリシーが壊れていてもテストが成功してしまう。
  - 「拒否されるべき追加」が誤って成功した場合に、後続のテストが一意制約違反で連鎖的に失敗しないよう、
    拒否されるべき追加には後続で使わない値を使う。
  - テストデータのメールアドレスは `@db-test.invalid` を使う（ローカルの開発用データと衝突させない）。
  - テストを追加したら、検証したいルールを一時的に壊して（ポリシーを緩める等）テストが失敗することを確認する。
  - ローカルの Supabase には開発用のユーザー（admin を含む）がいる。「admin が何人いるか」に依存するテストは、
    テスト内（rollback される）で `@db-test.invalid` 以外の admin を降格してから実行する（`09_user_suspension.test.sql` 参照）。
    降格は直接DB接続（`tests.clear_authentication()` の状態）で行えば、admin ガードのトリガーの対象外になる。
- 既知の問題を記録する場合は pgTAP の `todo()` を使う（失敗してもテスト全体は成功扱いになる）。
  問題を修正したら `todo` を外すこと。

## RLS・トリガー

- RLS（Row Level Security）前提の設計。クライアント（`data` 層）のクエリがRLSに違反しないことの確認は
  [data.instructions.md](data.instructions.md) を参照。
- `profiles.role` の変更を「adminのみ許可」にするトリガー（`enforce_role_change_permission`）は、
  **Postgresの実行ロールが `authenticated` の場合のみ**チェックするように実装すること。
  無条件でチェックすると、運用開始時に「最初のadminをSupabase管理画面から手動設定する」
  という初期セットアップ手順自体がブロックされてしまう（誰もadminがいない状態では
  `is_admin()` が常に false になるため、直接DB接続からの昇格も拒否されてしまう）。
  `current_setting('role', true) = 'authenticated'` で判定し、SQL Editor 等の直接DB接続
  （実行ロールが `postgres` 等）はチェック対象外にする。
- `profiles` テーブルのRLSポリシー内で「自分がadminか」を判定する際は、素朴に
  `exists (select 1 from profiles where id = auth.uid() and role = 'admin')` と書くと
  無限再帰（自分自身のテーブルのRLSを評価するために自分自身のRLSを再評価する）になり得る。
  `SECURITY DEFINER` な `is_admin()` 関数として切り出し、テーブル所有者権限で実行することで
  再帰を回避している。
- `current_setting('role', true)` は `SECURITY DEFINER` の関数の中でも呼び出し元の実行ロール（`authenticated`）を返す
  （`SECURITY DEFINER` が変えるのは `current_user` だけ）。そのため、`set_user_suspended()` のような
  `SECURITY DEFINER` の RPC から `profiles` を更新しても、`authenticated` 限定のトリガー（`enforce_role_change_permission` /
  `guard_active_admins`）は適用される。判定に `current_user` を使うと、`SECURITY DEFINER` 経由で迂回されるので使わない。
- 「最後の1人は残す」のような、他の行の状態に依存するチェックをトリガーで行う場合は、同時実行で2つの
  トランザクションが互いの未コミットの変更を見ずに通過しないよう、確認の前に `pg_advisory_xact_lock` 等で直列化する
  （`guard_active_admins` 参照）。
- 利用停止中のユーザーのアクセス拒否は、RLS ではなく PostgREST の pre-request 関数（`reject_suspended_user`）で行っている
  （全テーブル・RPC に一律で効く）。pre-request 関数は PostgREST がロールを `anon` / `authenticated` に切り替えた後に
  呼ぶため、それらのロールに `EXECUTE` 権限が必要。登録は `alter role authenticator in database ... set pgrst.db_pre_request`
  で行い、`notify pgrst, 'reload config'` で反映する。pre-request 関数が例外を投げると Data API の全リクエストが失敗するため、
  変更時は慎重に（ローカルで PostgREST 経由の動作も確認する）。
- `auth.users.banned_until` を設定したユーザーも、`/auth/v1/otp`（マジックリンク要求）は成功しメールも送られる。
  拒否されるのはリンクを開いたとき（`#error_code=user_banned` でリダイレクト）とトークン更新時。
- RLSポリシー・トリガー・Hook の振る舞いは DBテスト（`supabase/tests/database/`）で検証する
  （上記「DBテスト（`supabase/tests/`）」）。Supabase実機に適用する前に、ローカルの Docker 上で
  `./supabase/tests/run.sh` を実行して確認すること。実物の Supabase Auth（HTTP）経由の動作など
  DBテストで検証できないものは `supabase/README.md` の「自動テストでは検証していないもの」に挙げており、
  該当箇所を変更した場合は `supabase start` によるローカルのSupabase一式で手動確認する。
- 「誰が作ったか」等、クライアントに送らせたくない列（例: `company_holidays.created_by`。既定値 `auth.uid()`）がある
  テーブルは、`grant insert (<送ってよい列>, ...)` のように列単位で insert を許可する。テーブル単位で許可すると、
  クライアントが他人の ID を送って既定値を上書きできてしまう（DBテストで「送ると 42501 になる」ことを確認する。実例:
  `10_company_holidays.test.sql`）。
- 文字列の check 制約は、アプリ（domain）の検証と判定を完全に一致させる。DB が通した値を domain の変換が拒否すると、
  その行を含む一覧の取得全体が失敗する。特に `btrim()` は既定で半角スペースしか除かないため、「前後に空白なし」は
  Kotlin の `Char.isWhitespace()` と同じ文字の集合（タブ・改行・全角スペース等）を正規表現で明示して判定する
  （`[[:space:]]` はロケール依存なので使わない）。文字数は `char_length`（コードポイント数）なので、domain 側も
  `String.length`（UTF-16 の単位数）ではなくコードポイント数で数える。実例: `company_holidays_name_check` と `CompanyHoliday.isValidName`。

## 招待制アカウント作成（Before User Created Hook）

- **アカウント作成は招待制（招待リスト＋Before User Created Hook）。**
  adminが `public.invitations` にメールアドレスを登録し、本人の初回マジックリンク要求時に
  アカウントが作成される。未招待メールアドレスの拒否はSupabase Authの Before User Created Hook
  （`public.hook_before_user_created`、`supabase/migrations/*_invitations_and_signup_hook.sql`）で
  **サーバー側に強制している**。クライアント側で拒否を実現しようとしないこと（クライアント設定
  `OTP.Config.createUser = true` の理由は [data.instructions.md](data.instructions.md) を参照）。
- Hookは拒否時に `{"error": {"http_code": 403, "message": "email_not_invited"}}` を返す。
  **SQL側のメッセージとKotlin側の定数 `EMAIL_NOT_INVITED_HOOK_MESSAGE`（`data` 層の `SupabaseAuthRepository`）は
  必ず一致させること。** クライアントでの受け取られ方と例外変換は [data.instructions.md](data.instructions.md) を参照。
  SQL側の値は DBテスト（`supabase/tests/database/08_hook_before_user_created.test.sql`）で固定値として検証しているため、
  変更するとテストが失敗する（Kotlin 側の定数とテストの期待値もあわせて変更すること）。
  Hookのmessageはそのままクライアントに返るため、内部情報を含めないこと。
- **Hookの有効化はマイグレーションでは行えず、Supabaseダッシュボードでの手動設定が必要**
  （Authentication → Hooks）。`supabase/config.toml` の `[auth.hook.before_user_created]` は
  ローカル環境（`supabase start`）専用。本番の手順は `supabase/README.md` を参照。
  Hookが無効なまま「Allow new users to sign up」をONにすると、誰でもアカウントを作れてしまう。
- HookはSupabase Studioの「Invite user」やAdmin API経由のユーザー作成でも実行される。
  Studioから手動でユーザーを追加する場合も、先に `invitations` への登録が必要。
- Hook関数は `SECURITY DEFINER` で `invitations` を参照し（`supabase_auth_admin` 用のRLSポリシーを
  追加しない）、`PUBLIC` / `anon` / `authenticated` からの `EXECUTE` 権限は必ず剥奪する（剥奪しないと
  Data APIの `rpc` 経由で呼び出せてしまう）。PostgreSQL の関数は作成時に既定で `PUBLIC` に `EXECUTE` が
  付与されるため、`anon` / `authenticated` だけを剥奪しても `PUBLIC` 経由で呼び出せてしまう。既存の
  マイグレーションでも `revoke execute on function ... from public, anon, authenticated;` としている。
  剥奪漏れは DBテスト（`08_hook_before_user_created.test.sql`）が検知する。
- ログイン画面が招待済みかどうかを区別できるエラーを返すことは意図的に許容しているトレードオフ
  （理由は [data.instructions.md](data.instructions.md) を参照）。
- Edge Function + Admin API（`inviteUserByEmail`）方式は、TypeScript/Deno・secret key管理・
  デプロイ経路の追加が必要になるため採用していない（`docs/requirements.md` 5節）。

## upsert と BEFORE INSERT トリガー

- `insert ... on conflict do update`（supabase-kt の `upsert`）は、既存の行の更新になる場合も **BEFORE INSERT トリガーが発火する**。
  「新しい行の追加だけを制限する」トリガーは、同じキーの行が既にあれば許可するようにしないと、既存の行の upsert による更新まで拒否してしまう。
  また、`do update set` は値が変わらない列も含めて全列を set するため、`before update of <列>` のトリガーは値が変わったか
  （`is distinct from old.<列>`）で判定する。実例: `guard_allocation_project`（`allocations` に新しく配分できるのは本人に割り当てられた
  有効な案件だけ。既にある配分は担当を外された後も変更・削除できる）と `11_allocation_project_guard.test.sql`。
