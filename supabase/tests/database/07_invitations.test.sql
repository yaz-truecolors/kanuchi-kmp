-- invitations: 参照・追加・削除は admin のみ、メールアドレスの正規化、invited_by の自動設定と偽装拒否、CHECK 制約
begin;
\ir helpers.psql

select plan(29);

select tests.create_standard_users();

insert into public.invitations (email) values ('existing@db-test.invalid');

-- ------------------------------------------------------------
-- admin（アプリ経由 = authenticated）
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$ select email from public.invitations where email = 'existing@db-test.invalid' $$,
  $$ values ('existing@db-test.invalid') $$,
  'admin は invitations を参照できる'
);

select results_eq(
  $$ insert into public.invitations (email) values ('  New.Member@DB-Test.invalid ') returning email, invited_by $$,
  $$ values ('new.member@db-test.invalid', tests.user_id('admin@db-test.invalid')) $$,
  'admin は招待を追加でき、メールアドレスは小文字・前後空白なしに正規化され、invited_by は登録した admin 本人になる'
);

select throws_ok(
  $$ insert into public.invitations (email, invited_by) values ('spoof@db-test.invalid', tests.user_id('bob@db-test.invalid')) $$,
  '42501', null, 'admin でも invited_by に他人のIDを指定した招待は登録できない（偽装の拒否）'
);

select throws_ok(
  $$ insert into public.invitations (email, invited_by) values ('spoof@db-test.invalid', auth.uid()) $$,
  '42501', null, 'invited_by 列はアプリ経由では指定できない（email 列のみ INSERT 可）'
);

select throws_ok(
  $$ update public.invitations set email = 'changed@db-test.invalid' where email = 'existing@db-test.invalid' $$,
  '42501', null, 'admin も invitations を変更できない（UPDATE 権限なし）'
);

select results_eq(
  $$ delete from public.invitations where email = 'new.member@db-test.invalid' returning email $$,
  $$ values ('new.member@db-test.invalid') $$,
  'admin は招待を削除できる'
);

select throws_ok(
  $$ insert into public.invitations (email) values ('existing@db-test.invalid') $$,
  '23505', null, '登録済みのメールアドレスは重複して招待できない'
);

select throws_ok(
  $$ insert into public.invitations (email) values (' EXISTING@db-test.invalid') $$,
  '23505', null, '表記ゆれ（大文字・空白）があっても正規化後に重複していれば招待できない'
);

-- ------------------------------------------------------------
-- member（alice）
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select is_empty(
  $$ select email from public.invitations $$,
  'member は invitations を参照できない'
);

select throws_ok(
  $$ insert into public.invitations (email) values ('friend@db-test.invalid') $$,
  '42501', null, 'member は招待を追加できない'
);

select throws_ok(
  $$ update public.invitations set email = 'changed@db-test.invalid' where email = 'existing@db-test.invalid' $$,
  '42501', null, 'member は invitations を変更できない'
);

select is_empty(
  $$ delete from public.invitations where email = 'existing@db-test.invalid' returning email $$,
  'member は招待を削除できない'
);

-- ------------------------------------------------------------
-- 未ログイン（anon）
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok($$ select email from public.invitations $$, '42501', null, 'anon は invitations を参照できない');
select throws_ok(
  $$ insert into public.invitations (email) values ('anon@db-test.invalid') $$, '42501', null, 'anon は招待を追加できない'
);
select throws_ok(
  $$ update public.invitations set email = 'changed@db-test.invalid' $$, '42501', null, 'anon は invitations を変更できない'
);
select throws_ok($$ delete from public.invitations $$, '42501', null, 'anon は招待を削除できない');

-- ------------------------------------------------------------
-- 直接DB接続（Supabase Studio の Table Editor 等。実行ロール postgres、JWT なし）
-- ------------------------------------------------------------
select tests.clear_authentication();

select results_eq(
  $$ insert into public.invitations (email) values ('  Studio.User@DB-Test.INVALID ') returning email, invited_by $$,
  $$ values ('studio.user@db-test.invalid', null::uuid) $$,
  '直接DB接続での登録も正規化され、invited_by は空（Studioから登録）になる'
);

select results_eq(
  $$ update public.invitations set email = ' Renamed@DB-Test.invalid ' where email = 'studio.user@db-test.invalid' returning email $$,
  $$ values ('renamed@db-test.invalid') $$,
  'メールアドレスの変更時も正規化される'
);

select results_eq(
  $$
    insert into public.invitations (email, invited_by)
    values ('by-admin@db-test.invalid', tests.user_id('admin@db-test.invalid'))
    returning invited_by
  $$,
  $$ values (tests.user_id('admin@db-test.invalid')) $$,
  '直接DB接続では invited_by を明示的に指定できる'
);

select throws_ok(
  $$ insert into public.invitations (email, invited_by) values ('ghost@db-test.invalid', gen_random_uuid()) $$,
  '23503', null, 'invited_by に存在しないユーザーは指定できない（外部キー制約）'
);

select throws_ok(
  $$ insert into public.invitations (email) values ('not-an-email') $$,
  '23514',
  'new row for relation "invitations" violates check constraint "invitations_email_format_check"',
  '@ を含まないメールアドレスは招待できない'
);

select throws_ok(
  $$ insert into public.invitations (email) values ('a@b@db-test.invalid') $$,
  '23514',
  'new row for relation "invitations" violates check constraint "invitations_email_format_check"',
  '@ を複数含むメールアドレスは招待できない'
);

select throws_ok(
  $$ insert into public.invitations (email) values ('foo bar@db-test.invalid') $$,
  '23514',
  'new row for relation "invitations" violates check constraint "invitations_email_format_check"',
  '途中に空白を含むメールアドレスは招待できない'
);

select throws_ok(
  $$ insert into public.invitations (email) values ('@db-test.invalid') $$,
  '23514',
  'new row for relation "invitations" violates check constraint "invitations_email_format_check"',
  'ローカルパートが空のメールアドレスは招待できない'
);

select throws_ok(
  $$ insert into public.invitations (email) values ('   ') $$,
  '23514',
  null,
  '空白だけのメールアドレスは招待できない'
);

-- 正規化トリガーを経由しない不正な値に対する保険（invitations_email_normalized_check）
alter table public.invitations disable trigger normalize_invitation_email;

select throws_ok(
  $$ insert into public.invitations (email) values ('Upper@db-test.invalid') $$,
  '23514',
  'new row for relation "invitations" violates check constraint "invitations_email_normalized_check"',
  '正規化されていない（大文字を含む）メールアドレスは CHECK 制約で拒否される'
);

select throws_ok(
  $$ insert into public.invitations (email) values (' padded@db-test.invalid') $$,
  '23514',
  null,
  '前後に空白を含むメールアドレスは CHECK 制約（正規化・形式のいずれか）で拒否される'
);

alter table public.invitations enable trigger normalize_invitation_email;

-- ------------------------------------------------------------
-- 招待した admin のアカウントが削除された場合
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');
insert into public.invitations (email) values ('orphan@db-test.invalid');
select tests.clear_authentication();

select results_eq(
  $$ select invited_by from public.invitations where email = 'orphan@db-test.invalid' $$,
  $$ values (tests.user_id('admin@db-test.invalid')) $$,
  'アプリ経由で登録した招待の invited_by は登録した admin'
);

delete from auth.users where email = 'admin@db-test.invalid';

select results_eq(
  $$ select invited_by from public.invitations where email = 'orphan@db-test.invalid' $$,
  $$ values (null::uuid) $$,
  '招待した admin のアカウントが削除されても招待は残り、invited_by が空になる（on delete set null）'
);

select * from finish();
rollback;
