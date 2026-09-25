-- Before User Created Hook（public.hook_before_user_created）: 許可/拒否・正規化・EXECUTE 権限
--
-- 実物の Supabase Auth は HTTP 経由ではなく、この関数を実行ロール supabase_auth_admin で呼び出す。
-- ここでは関数を SQL から直接呼び出し、戻り値（許可: {}、拒否: error オブジェクト）を検証する。
begin;
\ir helpers.psql

select plan(18);

insert into public.invitations (email) values ('invited@db-test.invalid');

-- Supabase Auth が渡すイベントに近い形（metadata と user オブジェクト）を組み立てる
create function tests.signup_event(email text)
returns jsonb
language sql
as $$
  select jsonb_build_object(
    'metadata', jsonb_build_object(
      'uuid', gen_random_uuid(),
      'time', now(),
      'name', 'before-user-created',
      'ip_address', '127.0.0.1'
    ),
    'user', jsonb_build_object(
      'id', gen_random_uuid(),
      'aud', 'authenticated',
      'role', '',
      'email', email,
      'phone', '',
      'app_metadata', jsonb_build_object('provider', 'email', 'providers', jsonb_build_array('email')),
      'user_metadata', '{}'::jsonb,
      'identities', '[]'::jsonb,
      'is_anonymous', false
    )
  );
$$;

-- ------------------------------------------------------------
-- 許可
-- ------------------------------------------------------------
select is(
  public.hook_before_user_created(tests.signup_event('invited@db-test.invalid')),
  '{}'::jsonb,
  '招待済みのメールアドレスは許可される（空オブジェクトを返す）'
);

select is(
  public.hook_before_user_created(tests.signup_event('  Invited@DB-Test.INVALID ')),
  '{}'::jsonb,
  '大文字・前後空白を含むメールアドレスも正規化して照合され、許可される'
);

insert into public.invitations (email) values ('  Mixed.Case@DB-Test.invalid ');

select is(
  public.hook_before_user_created(tests.signup_event('mixed.case@db-test.invalid')),
  '{}'::jsonb,
  '大文字・前後空白を含めて登録した招待も、正規化されたメールアドレスで照合され、許可される'
);

-- ------------------------------------------------------------
-- 拒否
-- ------------------------------------------------------------
select is(
  public.hook_before_user_created(tests.signup_event('stranger@db-test.invalid')),
  '{"error": {"http_code": 403, "message": "email_not_invited"}}'::jsonb,
  '未招待のメールアドレスは HTTP 403 のエラーで拒否される（内部情報を含まない固定の内容）'
);

-- data 層の SupabaseAuthRepository の定数 EMAIL_NOT_INVITED_HOOK_MESSAGE はこの値と一致している必要がある。
-- 変更する場合は Kotlin 側の定数も合わせて変更すること（.github/instructions/supabase.instructions.md）。
select is(
  public.hook_before_user_created(tests.signup_event('stranger@db-test.invalid')) -> 'error' ->> 'message',
  'email_not_invited',
  '拒否時の message は email_not_invited（Kotlin 側の EMAIL_NOT_INVITED_HOOK_MESSAGE と一致）'
);

select is(
  (public.hook_before_user_created(tests.signup_event('stranger@db-test.invalid')) -> 'error' ->> 'http_code')::int,
  403,
  '拒否時の http_code は 403'
);

select is(
  public.hook_before_user_created(tests.signup_event('invited@db-test.invalid.example')) -> 'error' ->> 'message',
  'email_not_invited',
  '招待済みのメールアドレスを前方一致で含むだけのメールアドレスは拒否される'
);

select is(
  public.hook_before_user_created(jsonb_set(tests.signup_event('x@db-test.invalid'), '{user,email}', 'null'::jsonb))
    -> 'error' ->> 'message',
  'email_not_invited',
  'メールアドレスが null のユーザー作成（電話番号でのサインアップ等）は拒否される'
);

select is(
  public.hook_before_user_created(tests.signup_event('')) -> 'error' ->> 'message',
  'email_not_invited',
  'メールアドレスが空文字のユーザー作成は拒否される'
);

select is(
  public.hook_before_user_created('{}'::jsonb) -> 'error' ->> 'message',
  'email_not_invited',
  'user オブジェクトの無いイベントは拒否される'
);

delete from public.invitations where email = 'invited@db-test.invalid';

select is(
  public.hook_before_user_created(tests.signup_event('invited@db-test.invalid')) -> 'error' ->> 'message',
  'email_not_invited',
  '招待を取り消したメールアドレスは拒否される'
);

-- ------------------------------------------------------------
-- EXECUTE 権限（Data API の rpc から呼び出されないこと）
-- ------------------------------------------------------------
select ok(
  not has_function_privilege('anon', 'public.hook_before_user_created(jsonb)', 'EXECUTE'),
  'anon は Hook 関数を実行できない'
);

select ok(
  not has_function_privilege('authenticated', 'public.hook_before_user_created(jsonb)', 'EXECUTE'),
  'authenticated は Hook 関数を実行できない'
);

-- has_function_privilege('anon', ...) は PUBLIC への付与も含めて判定するが、
-- 将来ロールが増えた場合にも備えて PUBLIC への付与が無いことを ACL から直接確認する。
select is_empty(
  $$
    select acl.privilege_type
    from pg_proc as p, aclexplode(coalesce(p.proacl, acldefault('f', p.proowner))) as acl
    where p.oid = 'public.hook_before_user_created(jsonb)'::regprocedure
      and acl.grantee = 0
  $$,
  'PUBLIC に Hook 関数の EXECUTE 権限が付与されていない'
);

select ok(
  has_function_privilege('supabase_auth_admin', 'public.hook_before_user_created(jsonb)', 'EXECUTE')
    and has_schema_privilege('supabase_auth_admin', 'public', 'USAGE'),
  'supabase_auth_admin（Supabase Auth）は Hook 関数を実行できる'
);

-- supabase_auth_admin には invitations への権限・RLS ポリシーを与えていないため、
-- SECURITY DEFINER（テーブル所有者権限）で照合していないと Hook が動かない
select is_definer(
  'public', 'hook_before_user_created', array['jsonb'],
  'Hook 関数は SECURITY DEFINER（invitations の RLS を経由せずに照合する）'
);

select tests.authenticate_as_anon();

select throws_ok(
  $$ select public.hook_before_user_created('{"user": {"email": "invited@db-test.invalid"}}'::jsonb) $$,
  '42501', null, 'anon として Hook 関数を呼び出すと権限エラーになる'
);

select tests.clear_authentication();
select tests.create_user('alice@db-test.invalid');
select tests.authenticate_as('alice@db-test.invalid');

select throws_ok(
  $$ select public.hook_before_user_created('{"user": {"email": "invited@db-test.invalid"}}'::jsonb) $$,
  '42501', null, 'authenticated として Hook 関数を呼び出すと権限エラーになる'
);

select * from finish();
rollback;
