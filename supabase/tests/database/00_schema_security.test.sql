-- public スキーマ全体のセキュリティ設定（RLS・権限）を動的に走査して検証する。
-- テーブルを追加したときに「RLS を有効にし忘れた」「anon に権限を付与してしまった」等を検知するため、
-- テーブル名を列挙せず pg_catalog から全テーブルを走査する。
-- （新規テーブルの3点セットの規約は .github/instructions/supabase.instructions.md の「マイグレーション運用」）
begin;
\ir helpers.psql

select plan(10);

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

-- 既知の問題: Supabase の既定権限（ALTER DEFAULT PRIVILEGES）により、public スキーマのテーブルには
-- anon / authenticated に TRUNCATE・REFERENCES・TRIGGER・MAINTAIN が自動で付与されている。
-- Data API（PostgREST）からはこれらの操作を実行できないため実害は無いが、
-- 「anon には一切のテーブル権限を付与しない」という方針と食い違うため todo として記録している。
-- 剥奪するマイグレーションを追加したら todo を外すこと（外さないと pg_prove が「予期せず成功」と報告する）。
select todo('Supabase の既定権限で付与される TRUNCATE 等が未剥奪（既知の問題）', 2);

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
      and c.relkind in ('r', 'p')
      and has_table_privilege('authenticated', c.oid, 'TRUNCATE')
  $$,
  'authenticated は public スキーマのどのテーブルにも TRUNCATE（RLS を迂回する）の権限を持たない'
);

select * from finish();
rollback;
