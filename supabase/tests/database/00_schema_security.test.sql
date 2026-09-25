-- public スキーマ全体のセキュリティ設定（RLS・権限）を動的に走査して検証する。
-- テーブルを追加したときに「RLS を有効にし忘れた」「anon に権限を付与してしまった」等を検知するため、
-- テーブル名を列挙せず pg_catalog から全テーブルを走査する。
-- （新規テーブルの3点セットの規約は .github/instructions/supabase.instructions.md の「マイグレーション運用」）
begin;
\ir helpers.psql

select plan(17);

-- テーブルを追加・削除したら、この一覧を更新し、そのテーブルの RLS・権限のテストを追加すること。
select tables_are(
  'public',
  array['profiles', 'projects', 'user_projects', 'shift_settings', 'work_records', 'allocations', 'invitations'],
  'public スキーマのテーブル一覧が想定どおり（追加・削除したらテストも追加・更新する）'
);

select is_empty(
  $$
    select c.relname::text
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p')
      and not c.relrowsecurity
  $$,
  'public スキーマの全テーブルで RLS が有効'
);

select is_empty(
  $$
    select c.relname::text
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p')
      and not exists (select 1 from pg_policy as p where p.polrelid = c.oid)
  $$,
  'public スキーマの全テーブルにポリシーが1つ以上ある'
);

select is_empty(
  $$
    select c.relname::text
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p')
      and not (
        has_table_privilege('authenticated', c.oid, 'SELECT, INSERT, UPDATE, DELETE')
        or has_any_column_privilege('authenticated', c.oid, 'SELECT, INSERT, UPDATE')
      )
  $$,
  'public スキーマの全テーブルで authenticated に何らかの権限が付与されている'
);

select is_empty(
  $$
    select c.relname::text
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p', 'v', 'm', 'f')
      and (
        has_table_privilege('anon', c.oid, 'SELECT, INSERT, UPDATE, DELETE')
        or has_any_column_privilege('anon', c.oid, 'SELECT, INSERT, UPDATE')
      )
  $$,
  'anon は public スキーマのどのテーブル・ビューにも参照・追加・変更・削除の権限を持たない（列単位の権限も含む）'
);

select is_empty(
  $$
    select c.relname::text
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind = 'S'
      -- has_sequence_privilege はシーケンス以外を渡すとエラーになる。WHERE 句の評価順は保証されないため CASE で守る
      and case when c.relkind = 'S' then has_sequence_privilege('anon', c.oid, 'USAGE, SELECT, UPDATE') else false end
  $$,
  'anon は public スキーマのどのシーケンスにも権限を持たない'
);

select is_empty(
  $$
    select tablename::text || '.' || policyname::text
    from pg_policies
    where schemaname = 'public'
      and roles && array['public', 'anon']::name[]
  $$,
  'public スキーマに anon / PUBLIC 向けのポリシーが無い'
);

-- ビューは既定でビュー所有者の権限で実行され、元テーブルの RLS を迂回してしまうため、
-- ビューを追加する場合は security_invoker を有効にすること。
select is_empty(
  $$
    select c.relname::text
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind = 'v'
      and not coalesce(c.reloptions @> array['security_invoker=true'], false)
      and not coalesce(c.reloptions @> array['security_invoker=on'], false)
  $$,
  'public スキーマのビューはすべて security_invoker が有効（RLS を迂回しない）'
);

-- Supabase の既定権限（ALTER DEFAULT PRIVILEGES）で付与される TRUNCATE・REFERENCES・TRIGGER・MAINTAIN は
-- マイグレーション（*_revoke_excess_table_privileges.sql）で剥奪している。TRUNCATE は RLS を迂回するため、
-- anon / authenticated に残っていないことを確認する（MAINTAIN はローカルの PostgreSQL 17 を前提にしている）。
select is_empty(
  $$
    select c.relname::text
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p', 'v', 'm', 'f')
      and has_table_privilege('anon', c.oid, 'TRUNCATE, REFERENCES, TRIGGER, MAINTAIN')
  $$,
  'anon は public スキーマのどのテーブルにも TRUNCATE / REFERENCES / TRIGGER / MAINTAIN の権限を持たない'
);

select is_empty(
  $$
    select c.relname::text
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p', 'v', 'm', 'f')
      and has_table_privilege('authenticated', c.oid, 'TRUNCATE, REFERENCES, TRIGGER, MAINTAIN')
  $$,
  'authenticated は public スキーマのどのテーブルにも TRUNCATE（RLS を迂回する）/ REFERENCES / TRIGGER / MAINTAIN の権限を持たない'
);

-- 権限の剥奪でアプリが使う権限（列単位の grant を含む）まで消していないこと、余分な権限が無いことを、
-- authenticated の権限の一覧で確認する。テーブルや grant を追加・変更したら、この一覧も更新すること。
select set_eq(
  $$
    select c.relname::text, '' as column_name, a.privilege_type::text
    from pg_class as c, aclexplode(c.relacl) as a
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p', 'v', 'm', 'f')
      and a.grantee = 'authenticated'::regrole
    union all
    select c.relname::text, att.attname::text, a.privilege_type::text
    from pg_class as c
    join pg_attribute as att on att.attrelid = c.oid and not att.attisdropped
    cross join aclexplode(att.attacl) as a
    where c.relnamespace = 'public'::regnamespace
      and c.relkind in ('r', 'p', 'v', 'm', 'f')
      and a.grantee = 'authenticated'::regrole
  $$,
  $$
    values
      ('profiles', '', 'SELECT'),
      ('profiles', 'display_name', 'UPDATE'),
      ('profiles', 'role', 'UPDATE'),
      ('projects', '', 'SELECT'), ('projects', '', 'INSERT'), ('projects', '', 'UPDATE'), ('projects', '', 'DELETE'),
      ('user_projects', '', 'SELECT'), ('user_projects', '', 'INSERT'), ('user_projects', '', 'UPDATE'), ('user_projects', '', 'DELETE'),
      ('shift_settings', '', 'SELECT'), ('shift_settings', '', 'INSERT'), ('shift_settings', '', 'UPDATE'), ('shift_settings', '', 'DELETE'),
      ('work_records', '', 'SELECT'), ('work_records', '', 'INSERT'), ('work_records', '', 'UPDATE'), ('work_records', '', 'DELETE'),
      ('allocations', '', 'SELECT'), ('allocations', '', 'INSERT'), ('allocations', '', 'UPDATE'), ('allocations', '', 'DELETE'),
      ('invitations', '', 'SELECT'), ('invitations', '', 'DELETE'),
      ('invitations', 'email', 'INSERT')
  $$,
  'authenticated の権限はアプリが使う参照・追加・変更・削除（列単位を含む）だけ'
);

-- 今後追加するテーブル・シーケンスにも余分な権限が付かないよう、マイグレーションを実行するロール（postgres）の
-- public スキーマの既定権限を調整している。既定権限そのものと、実際に作ったテーブルの権限の両方で確認する。
select is_empty(
  $$
    select d.defaclobjtype::text || ':' || a.privilege_type
    from pg_default_acl as d, aclexplode(d.defaclacl) as a
    where d.defaclrole = 'postgres'::regrole
      and d.defaclnamespace = 'public'::regnamespace
      and d.defaclobjtype in ('r', 'S')
      and a.grantee = 'anon'::regrole
  $$,
  'postgres ロールの public スキーマの既定権限で、anon にテーブル・シーケンスの権限が付与されない'
);

select is_empty(
  $$
    select a.privilege_type
    from pg_default_acl as d, aclexplode(d.defaclacl) as a
    where d.defaclrole = 'postgres'::regrole
      and d.defaclnamespace = 'public'::regnamespace
      and d.defaclobjtype = 'r'
      and a.grantee = 'authenticated'::regrole
      and a.privilege_type in ('TRUNCATE', 'REFERENCES', 'TRIGGER', 'MAINTAIN')
  $$,
  'postgres ロールの public スキーマの既定権限で、authenticated に TRUNCATE / REFERENCES / TRIGGER / MAINTAIN が付与されない'
);

-- マイグレーションと同じ実行ロール（postgres。supabase test db は postgres で接続する）でテーブルを作り、
-- 既定権限が実際に効くことを確認する（rollback で消える）。上の public スキーマ全体を走査するテストに
-- 影響しないよう、最後に作成する。
create table public.tests_default_privileges_probe (
  id bigint generated by default as identity primary key
);

select is(
  (select c.relowner::regrole::text from pg_class as c where c.oid = 'public.tests_default_privileges_probe'::regclass),
  'postgres',
  '確認用のテーブルはマイグレーションと同じ postgres ロールが所有している'
);

select ok(
  not has_table_privilege(
    'anon',
    'public.tests_default_privileges_probe',
    'SELECT, INSERT, UPDATE, DELETE, TRUNCATE, REFERENCES, TRIGGER, MAINTAIN'
  ),
  '新しく作ったテーブルで anon に権限が付与されない'
);

select ok(
  not has_table_privilege('authenticated', 'public.tests_default_privileges_probe', 'TRUNCATE, REFERENCES, TRIGGER, MAINTAIN'),
  '新しく作ったテーブルで authenticated に TRUNCATE / REFERENCES / TRIGGER / MAINTAIN が付与されない'
);

select ok(
  not has_sequence_privilege(
    'anon',
    pg_get_serial_sequence('public.tests_default_privileges_probe', 'id'),
    'USAGE, SELECT, UPDATE'
  ),
  '新しく作ったシーケンスで anon に権限が付与されない'
);

select * from finish();
rollback;
