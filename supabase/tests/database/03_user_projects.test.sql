-- user_projects: 本人の割当 or admin は参照可、割当の追加・変更・削除は admin のみ
begin;
\ir helpers.psql

select plan(19);

select tests.create_standard_users();

insert into public.projects (id, name) values
  ('10000000-0000-0000-0000-000000000001'::uuid, 'db-test 案件1'),
  ('10000000-0000-0000-0000-000000000002'::uuid, 'db-test 案件2');

insert into public.user_projects (user_id, project_id) values
  (tests.user_id('alice@db-test.invalid'), '10000000-0000-0000-0000-000000000001'),
  (tests.user_id('bob@db-test.invalid'), '10000000-0000-0000-0000-000000000002');

-- ------------------------------------------------------------
-- member（alice）
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$ select project_id from public.user_projects where user_id = auth.uid() $$,
  $$ values ('10000000-0000-0000-0000-000000000001'::uuid) $$,
  'member は自分の割当を参照できる'
);

select is_empty(
  $$ select project_id from public.user_projects where user_id <> auth.uid() $$,
  'member は他人の割当を参照できない'
);

select throws_ok(
  $$ insert into public.user_projects (user_id, project_id) values (auth.uid(), '10000000-0000-0000-0000-000000000002') $$,
  '42501', null, 'member は自分に割当を追加できない'
);

select throws_ok(
  $$
    insert into public.user_projects (user_id, project_id)
    values (tests.user_id('bob@db-test.invalid'), '10000000-0000-0000-0000-000000000001')
  $$,
  '42501', null, 'member は他人に割当を追加できない'
);

select is_empty(
  $$
    update public.user_projects set project_id = '10000000-0000-0000-0000-000000000002'
    where user_id = auth.uid() returning project_id
  $$,
  'member は自分の割当を変更できない'
);

select is_empty(
  $$
    update public.user_projects set project_id = '10000000-0000-0000-0000-000000000001'
    where user_id = tests.user_id('bob@db-test.invalid') returning project_id
  $$,
  'member は他人の割当を変更できない'
);

select is_empty(
  $$ delete from public.user_projects where user_id = auth.uid() returning project_id $$,
  'member は自分の割当を削除できない'
);

select is_empty(
  $$ delete from public.user_projects where user_id = tests.user_id('bob@db-test.invalid') returning project_id $$,
  'member は他人の割当を削除できない'
);

-- ------------------------------------------------------------
-- admin
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$
    select count(*) from public.user_projects
    where user_id in (tests.user_id('alice@db-test.invalid'), tests.user_id('bob@db-test.invalid'))
  $$,
  array[2::bigint],
  'admin は全員の割当を参照できる'
);

select results_eq(
  $$
    insert into public.user_projects (user_id, project_id)
    values (tests.user_id('alice@db-test.invalid'), '10000000-0000-0000-0000-000000000002')
    returning project_id
  $$,
  $$ values ('10000000-0000-0000-0000-000000000002'::uuid) $$,
  'admin は他人に割当を追加できる'
);

select results_eq(
  $$
    insert into public.user_projects (user_id, project_id)
    values (auth.uid(), '10000000-0000-0000-0000-000000000001')
    returning project_id
  $$,
  $$ values ('10000000-0000-0000-0000-000000000001'::uuid) $$,
  'admin は自分に割当を追加できる'
);

select results_eq(
  $$
    update public.user_projects set project_id = '10000000-0000-0000-0000-000000000001'
    where user_id = tests.user_id('bob@db-test.invalid') returning project_id
  $$,
  $$ values ('10000000-0000-0000-0000-000000000001'::uuid) $$,
  'admin は他人の割当を変更できる'
);

select results_eq(
  $$
    delete from public.user_projects
    where user_id = tests.user_id('alice@db-test.invalid') and project_id = '10000000-0000-0000-0000-000000000002'
    returning project_id
  $$,
  $$ values ('10000000-0000-0000-0000-000000000002'::uuid) $$,
  'admin は他人の割当を削除できる'
);

-- ------------------------------------------------------------
-- admin 以外が admin の割当を見られないこと（admin の行も「他人」として扱われる）
-- ------------------------------------------------------------
select tests.authenticate_as('bob@db-test.invalid');

select is_empty(
  $$ select project_id from public.user_projects where user_id = tests.user_id('admin@db-test.invalid') $$,
  'member は admin の割当も参照できない'
);

select results_eq(
  $$ select project_id from public.user_projects where user_id = auth.uid() $$,
  $$ values ('10000000-0000-0000-0000-000000000001'::uuid) $$,
  'admin が変更した割当が本人から見える'
);

-- ------------------------------------------------------------
-- 未ログイン（anon）
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok($$ select user_id from public.user_projects $$, '42501', null, 'anon は user_projects を参照できない');
select throws_ok(
  $$
    insert into public.user_projects (user_id, project_id)
    values (tests.user_id('alice@db-test.invalid'), '10000000-0000-0000-0000-000000000002')
  $$,
  '42501', null, 'anon は user_projects を追加できない'
);
select throws_ok(
  $$ update public.user_projects set project_id = '10000000-0000-0000-0000-000000000002' $$,
  '42501', null, 'anon は user_projects を変更できない'
);
select throws_ok($$ delete from public.user_projects $$, '42501', null, 'anon は user_projects を削除できない');

select * from finish();
rollback;
