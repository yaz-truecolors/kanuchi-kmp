-- 利用停止・復帰（set_user_suspended）、利用停止中のユーザーのアクセス拒否（reject_suspended_user・is_admin）、
-- 自分自身・最後の利用中 admin の利用停止・降格の禁止（guard_active_admins）
begin;
\ir helpers.psql

select plan(44);

-- admin@db-test.invalid（admin）, alice / bob（member）に加えて、2人目の admin を作る
select tests.create_standard_users();
select tests.create_user('admin2@db-test.invalid');
update public.profiles set role = 'admin' where id = tests.user_id('admin2@db-test.invalid');

-- 「利用中の admin の人数」を検証するため、ローカルDBに開発用の admin がいれば member にしておく
-- （CI の DB には存在しない。末尾の rollback で元に戻る）
update public.profiles set role = 'member' where role = 'admin' and email not like '%@db-test.invalid';

-- ログイン中の端末のセッション（リフレッシュトークンの親）。利用停止で削除されることを確認する
insert into auth.sessions (id, user_id) values (gen_random_uuid(), tests.user_id('alice@db-test.invalid'));

-- ------------------------------------------------------------
-- 権限・設定
-- ------------------------------------------------------------
select ok(
  has_function_privilege('authenticated', 'public.set_user_suspended(uuid, boolean)', 'EXECUTE'),
  'authenticated は set_user_suspended を実行できる（admin かどうかは関数内で判定する）'
);

select ok(
  not has_function_privilege('anon', 'public.set_user_suspended(uuid, boolean)', 'EXECUTE'),
  'anon は set_user_suspended を実行できない（PUBLIC 経由も含む）'
);

select ok(
  has_function_privilege('anon', 'public.reject_suspended_user()', 'EXECUTE')
    and has_function_privilege('authenticated', 'public.reject_suspended_user()', 'EXECUTE'),
  'pre-request 関数は anon / authenticated が実行できる（PostgREST はロールの切り替え後に呼び出すため）'
);

select ok(
  not has_function_privilege('authenticated', 'public.guard_active_admins()', 'EXECUTE')
    and not has_function_privilege('anon', 'public.guard_active_admins()', 'EXECUTE'),
  'トリガー関数 guard_active_admins は anon / authenticated から実行できない'
);

select ok(
  exists (
    select 1
    from pg_db_role_setting as s
    where s.setrole = 'authenticator'::regrole
      and s.setdatabase = (select d.oid from pg_database as d where d.datname = current_database())
      and 'pgrst.db_pre_request=public.reject_suspended_user' = any (s.setconfig)
  ),
  'PostgREST の pre-request 関数として reject_suspended_user が登録されている'
);

-- ------------------------------------------------------------
-- member（alice）は利用停止・復帰できない
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select throws_ok(
  $$ select public.set_user_suspended(tests.user_id('bob@db-test.invalid'), true) $$,
  '42501',
  'Only admins can suspend or reactivate users',
  'member は他人を利用停止できない'
);

select throws_ok(
  $$ update public.profiles set suspended_at = now() where id = auth.uid() $$,
  '42501',
  null,
  'member は suspended_at を直接変更できない（UPDATE 権限なし）'
);

select lives_ok(
  $$ select public.reject_suspended_user() $$,
  '利用中のユーザーのリクエストは pre-request 関数で拒否されない'
);

-- ------------------------------------------------------------
-- anon
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok(
  $$ select public.set_user_suspended(tests.user_id('bob@db-test.invalid'), true) $$,
  '42501',
  null,
  'anon は利用停止できない（EXECUTE 権限なし）'
);

select lives_ok(
  $$ select public.reject_suspended_user() $$,
  '未ログインのリクエストは pre-request 関数で拒否されない'
);

-- ------------------------------------------------------------
-- admin による利用停止
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select throws_ok(
  $$ update public.profiles set suspended_at = now() where id = tests.user_id('alice@db-test.invalid') $$,
  '42501',
  null,
  'admin も suspended_at を直接変更できない（set_user_suspended を使う）'
);

select lives_ok(
  $$ select public.set_user_suspended(tests.user_id('alice@db-test.invalid'), true) $$,
  'admin は member を利用停止できる'
);

select tests.clear_authentication();

select isnt(
  (select suspended_at from public.profiles where id = tests.user_id('alice@db-test.invalid')),
  null,
  '利用停止すると profiles.suspended_at が設定される'
);

select ok(
  (select banned_until > now() + interval '99 years' from auth.users where id = tests.user_id('alice@db-test.invalid')),
  '利用停止すると auth.users.banned_until が遠い未来に設定される（Supabase Auth がログインを拒否する）'
);

select is_empty(
  $$ select id from auth.sessions where user_id = tests.user_id('alice@db-test.invalid') $$,
  '利用停止するとログイン中のセッションが削除される（リフレッシュトークンが無効になる）'
);

select ok(
  exists (select 1 from public.profiles where id = tests.user_id('alice@db-test.invalid')),
  '利用停止してもアカウント（profiles）は削除されない'
);

select tests.authenticate_as('admin@db-test.invalid');

select lives_ok(
  $$ select public.set_user_suspended(tests.user_id('alice@db-test.invalid'), true) $$,
  '利用停止中のユーザーを再度利用停止してもエラーにならない'
);

select throws_ok(
  $$ select public.set_user_suspended(gen_random_uuid(), true) $$,
  'P0002',
  'user_not_found',
  '存在しないユーザーは利用停止できない'
);

-- ------------------------------------------------------------
-- 利用停止中のユーザー（alice）のアクセス
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select throws_ok(
  $$ select public.reject_suspended_user() $$,
  '42501',
  'user_suspended',
  '利用停止中のユーザーのリクエストは pre-request 関数で拒否される（発行済みのアクセストークンでも Data API を使えない）'
);

-- ------------------------------------------------------------
-- 復帰
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select lives_ok(
  $$ select public.set_user_suspended(tests.user_id('alice@db-test.invalid'), false) $$,
  'admin は利用停止中のユーザーを復帰させられる'
);

select tests.clear_authentication();

select results_eq(
  $$
    select p.suspended_at, u.banned_until
    from public.profiles as p join auth.users as u on u.id = p.id
    where p.id = tests.user_id('alice@db-test.invalid')
  $$,
  $$ values (null::timestamptz, null::timestamptz) $$,
  '復帰すると profiles.suspended_at と auth.users.banned_until が解除される'
);

select tests.authenticate_as('alice@db-test.invalid');

select lives_ok(
  $$ select public.reject_suspended_user() $$,
  '復帰したユーザーのリクエストは pre-request 関数で拒否されない'
);

-- ------------------------------------------------------------
-- 自分自身の利用停止・降格の禁止
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select throws_ok(
  $$ select public.set_user_suspended(auth.uid(), true) $$,
  'P0001',
  'cannot_suspend_self',
  'admin は自分自身を利用停止できない（他に利用中の admin がいても）'
);

select throws_ok(
  $$ update public.profiles set role = 'member' where id = auth.uid() $$,
  'P0001',
  'cannot_demote_self',
  'admin は自分自身を降格できない（他に利用中の admin がいても）'
);

select results_eq(
  $$ select role from public.profiles where id = auth.uid() $$,
  $$ values ('admin') $$,
  '自分自身の降格が拒否された後も admin のまま'
);

-- ------------------------------------------------------------
-- 他の admin の利用停止・降格（利用中の admin が残る場合は可能）
-- ------------------------------------------------------------
select lives_ok(
  $$ select public.set_user_suspended(tests.user_id('admin2@db-test.invalid'), true) $$,
  'admin は他の admin を利用停止できる（自分が利用中の admin として残るため）'
);

-- 利用停止中の admin は admin として扱われない
select tests.authenticate_as('admin2@db-test.invalid');

select ok(
  not public.is_admin(),
  '利用停止中の admin は is_admin() が false になる'
);

select is_empty(
  $$ select id from public.profiles where id <> auth.uid() $$,
  '利用停止中の admin は他人の profiles を参照できない'
);

select is_empty(
  $$ select email from public.invitations $$,
  '利用停止中の admin は invitations を参照できない'
);

select throws_ok(
  $$ select public.set_user_suspended(tests.user_id('bob@db-test.invalid'), true) $$,
  '42501',
  'Only admins can suspend or reactivate users',
  '利用停止中の admin は他人を利用停止できない'
);

select throws_ok(
  $$ update public.profiles set role = 'member' where id = auth.uid() $$,
  'P0001',
  'Only admins can change user roles',
  '利用停止中の admin は role を変更できない（他人の行は RLS で見えないため、自分の行で確認）'
);

select throws_ok(
  $$ select public.reject_suspended_user() $$,
  '42501',
  'user_suspended',
  '利用停止中の admin のリクエストも pre-request 関数で拒否される'
);

-- 利用停止中の admin（admin2）の降格は、利用中の admin の数を減らさないため可能
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$ update public.profiles set role = 'member' where id = tests.user_id('admin2@db-test.invalid') returning role $$,
  $$ values ('member') $$,
  '利用停止中の admin は降格できる（利用中の admin の数は変わらない）'
);

select lives_ok(
  $$ select public.set_user_suspended(tests.user_id('admin2@db-test.invalid'), false) $$,
  '復帰させられる'
);

select results_eq(
  $$ update public.profiles set role = 'admin' where id = tests.user_id('admin2@db-test.invalid') returning role $$,
  $$ values ('admin') $$,
  'admin に昇格できる'
);

select results_eq(
  $$ update public.profiles set role = 'member' where id = tests.user_id('admin2@db-test.invalid') returning role $$,
  $$ values ('member') $$,
  'admin は他の利用中の admin を降格できる（自分が利用中の admin として残るため）'
);

-- ------------------------------------------------------------
-- 利用中の admin が1人だけの場合（admin のみ）
-- ------------------------------------------------------------
select throws_ok(
  $$ update public.profiles set role = 'member' where id = auth.uid() $$,
  'P0001',
  'last_active_admin_required',
  '利用中の admin が自分だけの場合、自分を降格できない（利用中の admin が0人になる）'
);

select throws_ok(
  $$ select public.set_user_suspended(auth.uid(), true) $$,
  'P0001',
  'last_active_admin_required',
  '利用中の admin が自分だけの場合、自分を利用停止できない（利用中の admin が0人になる）'
);

-- 利用停止中の admin しか他にいない場合も、利用中の admin は自分だけ
select tests.clear_authentication();
update public.profiles set role = 'admin' where id = tests.user_id('admin2@db-test.invalid');
select public.set_user_suspended(tests.user_id('admin2@db-test.invalid'), true);
select tests.authenticate_as('admin@db-test.invalid');

select throws_ok(
  $$ update public.profiles set role = 'member' where id = auth.uid() $$,
  'P0001',
  'last_active_admin_required',
  '他の admin が利用停止中だけの場合も、自分を降格できない'
);

-- 他の admin に降格された結果、利用中の admin が自分だけになった場合
select tests.clear_authentication();
select public.set_user_suspended(tests.user_id('admin2@db-test.invalid'), false);
select tests.authenticate_as('admin2@db-test.invalid');

select results_eq(
  $$ update public.profiles set role = 'member' where id = tests.user_id('admin@db-test.invalid') returning role $$,
  $$ values ('member') $$,
  '利用中の admin が2人なら、一方がもう一方を降格できる'
);

select throws_ok(
  $$ update public.profiles set role = 'member' where id = auth.uid() $$,
  'P0001',
  'last_active_admin_required',
  '降格された結果、残った利用中の admin（自分）は降格できない'
);

-- ------------------------------------------------------------
-- 直接DB接続（SQL Editor。実行ロール postgres）は制限の対象外（初期セットアップ・復旧のため）
-- ------------------------------------------------------------
select tests.clear_authentication();

select lives_ok(
  $$ update public.profiles set role = 'member' where id = tests.user_id('admin2@db-test.invalid') $$,
  '直接DB接続では最後の利用中 admin も降格できる（復旧のため）'
);

select lives_ok(
  $$ select public.set_user_suspended(tests.user_id('bob@db-test.invalid'), true) $$,
  '直接DB接続では set_user_suspended を実行できる'
);

select lives_ok(
  $$ update public.profiles set role = 'admin' where id = tests.user_id('admin@db-test.invalid') $$,
  '直接DB接続では admin に昇格できる（初期adminの手動設定）'
);

select * from finish();
rollback;
