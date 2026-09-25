-- profiles: 自動作成・メールアドレス同期・RLS・role 変更の制限（enforce_role_change_permission）
begin;
\ir helpers.psql

select plan(28);

-- role 変更トリガーの検証のため、create_standard_users() は使わずに作成・昇格する
select tests.create_user('admin@db-test.invalid');
select tests.create_user('alice@db-test.invalid');
select tests.create_user('bob@db-test.invalid');
select tests.create_user('carol@db-test.invalid', 'キャロル');

-- ------------------------------------------------------------
-- 自動作成・同期（handle_new_user / handle_user_email_updated）
-- ------------------------------------------------------------
select results_eq(
  $$ select email, display_name, role from public.profiles where id = tests.user_id('alice@db-test.invalid') $$,
  $$ values ('alice@db-test.invalid', 'alice', 'member') $$,
  'auth.users への追加で profiles 行が自動作成される（display_name はメールのローカルパート、role は member）'
);

select results_eq(
  $$ select display_name from public.profiles where id = tests.user_id('carol@db-test.invalid') $$,
  $$ values ('キャロル') $$,
  'raw_user_meta_data の display_name があれば profiles の display_name に使われる'
);

update auth.users set email = 'carol-new@db-test.invalid' where email = 'carol@db-test.invalid';

select results_eq(
  $$ select email from public.profiles where id = tests.user_id('carol-new@db-test.invalid') $$,
  $$ values ('carol-new@db-test.invalid') $$,
  'auth.users.email の変更が profiles.email に同期される'
);

-- ------------------------------------------------------------
-- 直接DB接続（SQL Editor・Table Editor。実行ロール postgres）
-- ------------------------------------------------------------
select lives_ok(
  $$ update public.profiles set role = 'admin' where id = tests.user_id('admin@db-test.invalid') $$,
  '直接DB接続（ログインユーザーなし = is_admin() が false）でも role を admin に変更できる（初期adminの手動設定）'
);

select lives_ok(
  $$ update public.profiles set role = 'admin' where id = tests.user_id('bob@db-test.invalid') $$,
  '直接DB接続では任意のユーザーの role を変更できる（role 変更の制限は authenticated のみに適用）'
);

update public.profiles set role = 'member' where id = tests.user_id('bob@db-test.invalid');

select throws_ok(
  $$ update public.profiles set role = 'owner' where id = tests.user_id('bob@db-test.invalid') $$,
  '23514',
  null,
  'role は member / admin 以外の値にできない（CHECK制約）'
);

-- ------------------------------------------------------------
-- member（alice）としてログイン
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$ select id from public.profiles where id = auth.uid() $$,
  $$ values (tests.user_id('alice@db-test.invalid')) $$,
  'member は自分の profiles を参照できる'
);

select is_empty(
  $$ select id from public.profiles where id <> auth.uid() $$,
  'member は他人の profiles を参照できない'
);

select results_eq(
  $$ update public.profiles set display_name = 'アリス' where id = auth.uid() returning display_name $$,
  $$ values ('アリス') $$,
  'member は自分の display_name を変更できる'
);

select is_empty(
  $$ update public.profiles set display_name = 'hacked' where id = tests.user_id('bob@db-test.invalid') returning id $$,
  'member は他人の display_name を変更できない'
);

select throws_ok(
  $$ update public.profiles set role = 'admin' where id = auth.uid() $$,
  'P0001',
  'Only admins can change user roles',
  'member は自分の role を admin に昇格できない'
);

select is_empty(
  $$ update public.profiles set role = 'admin' where id = tests.user_id('bob@db-test.invalid') returning id $$,
  'member は他人の role を変更できない'
);

select throws_ok(
  $$ update public.profiles set email = 'spoofed@db-test.invalid' where id = auth.uid() $$,
  '42501',
  null,
  'member は自分の email を直接変更できない（UPDATE は display_name / role 列のみ許可）'
);

select throws_ok(
  $$ insert into public.profiles (id, email, display_name) values (gen_random_uuid(), 'x@db-test.invalid', 'x') $$,
  '42501',
  null,
  'member は profiles を追加できない（追加は handle_new_user トリガーのみ）'
);

select throws_ok(
  $$ delete from public.profiles where id = auth.uid() $$,
  '42501',
  null,
  'member は自分の profiles を削除できない'
);

-- ------------------------------------------------------------
-- admin としてログイン
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$
    select count(*) from public.profiles
    where id in (tests.user_id('alice@db-test.invalid'), tests.user_id('bob@db-test.invalid'), auth.uid())
  $$,
  array[3::bigint],
  'admin は全員の profiles を参照できる'
);

select results_eq(
  $$ update public.profiles set display_name = 'ボブ' where id = tests.user_id('bob@db-test.invalid') returning display_name $$,
  $$ values ('ボブ') $$,
  'admin は他人の display_name を変更できる'
);

select results_eq(
  $$ update public.profiles set role = 'admin' where id = tests.user_id('alice@db-test.invalid') returning role $$,
  $$ values ('admin') $$,
  'admin は他のユーザーを admin に昇格できる'
);

select results_eq(
  $$ update public.profiles set role = 'member' where id = tests.user_id('alice@db-test.invalid') returning role $$,
  $$ values ('member') $$,
  'admin は他のユーザーを member に戻せる'
);

select throws_ok(
  $$ update public.profiles set email = 'spoofed@db-test.invalid' where id = tests.user_id('bob@db-test.invalid') $$,
  '42501',
  null,
  'admin も email を直接変更できない'
);

select throws_ok(
  $$ insert into public.profiles (id, email, display_name) values (gen_random_uuid(), 'x@db-test.invalid', 'x') $$,
  '42501',
  null,
  'admin も profiles を追加できない'
);

select throws_ok(
  $$ delete from public.profiles where id = tests.user_id('bob@db-test.invalid') $$,
  '42501',
  null,
  'admin も profiles を削除できない'
);

-- ------------------------------------------------------------
-- member に戻った元 admin（昇格・降格の判定がログイン中のユーザーの現在の role で行われること）
-- ------------------------------------------------------------
select tests.clear_authentication();
update public.profiles set role = 'member' where id = tests.user_id('admin@db-test.invalid');
select tests.authenticate_as('admin@db-test.invalid');

select throws_ok(
  $$ update public.profiles set role = 'admin' where id = auth.uid() $$,
  'P0001',
  'Only admins can change user roles',
  'member に降格されたユーザーは自分を admin に戻せない'
);

select is_empty(
  $$ select id from public.profiles where id <> auth.uid() $$,
  'member に降格されたユーザーは他人の profiles を参照できない'
);

-- ------------------------------------------------------------
-- 未ログイン（anon）
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok(
  $$ select id from public.profiles $$,
  '42501', null, 'anon は profiles を参照できない'
);

select throws_ok(
  $$ insert into public.profiles (id, email, display_name) values (gen_random_uuid(), 'x@db-test.invalid', 'x') $$,
  '42501', null, 'anon は profiles を追加できない'
);

select throws_ok(
  $$ update public.profiles set display_name = 'hacked' $$,
  '42501', null, 'anon は profiles を変更できない'
);

select throws_ok(
  $$ delete from public.profiles $$,
  '42501', null, 'anon は profiles を削除できない'
);

select * from finish();
rollback;
