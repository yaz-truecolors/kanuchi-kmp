-- projects: 全員参照可、追加・変更・削除は admin のみ
begin;
\ir helpers.psql

select plan(12);

select tests.create_standard_users();

insert into public.projects (id, name) values
  ('10000000-0000-0000-0000-000000000001'::uuid, 'db-test 案件1');

-- ------------------------------------------------------------
-- member（alice）
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$ select name from public.projects where id = '10000000-0000-0000-0000-000000000001' $$,
  $$ values ('db-test 案件1') $$,
  'member は projects を参照できる'
);

select throws_ok(
  $$ insert into public.projects (name) values ('db-test member が追加しようとした案件') $$,
  '42501', null, 'member は projects を追加できない'
);

select is_empty(
  $$ update public.projects set name = 'hacked' where id = '10000000-0000-0000-0000-000000000001' returning id $$,
  'member は projects を変更できない'
);

select is_empty(
  $$ delete from public.projects where id = '10000000-0000-0000-0000-000000000001' returning id $$,
  'member は projects を削除できない'
);

-- ------------------------------------------------------------
-- admin
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$ select name from public.projects where id = '10000000-0000-0000-0000-000000000001' $$,
  $$ values ('db-test 案件1') $$,
  'admin は projects を参照できる'
);

select results_eq(
  $$ insert into public.projects (name) values ('db-test 案件2') returning name $$,
  $$ values ('db-test 案件2') $$,
  'admin は projects を追加できる'
);

select results_eq(
  $$ update public.projects set is_active = false where id = '10000000-0000-0000-0000-000000000001' returning is_active $$,
  $$ values (false) $$,
  'admin は projects を変更できる'
);

select results_eq(
  $$ delete from public.projects where name = 'db-test 案件2' returning name $$,
  $$ values ('db-test 案件2') $$,
  'admin は projects を削除できる'
);

-- ------------------------------------------------------------
-- 未ログイン（anon）
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok($$ select id from public.projects $$, '42501', null, 'anon は projects を参照できない');
select throws_ok(
  $$ insert into public.projects (name) values ('db-test anon') $$, '42501', null, 'anon は projects を追加できない'
);
select throws_ok($$ update public.projects set name = 'hacked' $$, '42501', null, 'anon は projects を変更できない');
select throws_ok($$ delete from public.projects $$, '42501', null, 'anon は projects を削除できない');

select * from finish();
rollback;
