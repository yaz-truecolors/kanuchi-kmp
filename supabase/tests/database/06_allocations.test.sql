-- allocations: 所有者は work_records 経由で判定。本人のみ追加・変更・削除可、参照は本人 or admin（admin は参照のみ）
begin;
\ir helpers.psql

select plan(24);

select tests.create_standard_users();

insert into public.projects (id, name) values
  ('10000000-0000-0000-0000-000000000001'::uuid, 'db-test 案件1'),
  ('10000000-0000-0000-0000-000000000002'::uuid, 'db-test 案件2'),
  -- 案件3・4 は「拒否されるべき追加」専用（誤って成功しても後続のテストの一意制約に影響しないようにする）
  ('10000000-0000-0000-0000-000000000003'::uuid, 'db-test 案件3'),
  ('10000000-0000-0000-0000-000000000004'::uuid, 'db-test 案件4');

insert into public.work_records (id, user_id, work_date) values
  ('20000000-0000-0000-0000-00000000000a'::uuid, tests.user_id('alice@db-test.invalid'), '2026-09-01'),
  ('20000000-0000-0000-0000-00000000000b'::uuid, tests.user_id('bob@db-test.invalid'), '2026-09-01');

insert into public.allocations (id, work_record_id, project_id, hours) values
  ('30000000-0000-0000-0000-00000000000a'::uuid, '20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-000000000001', 4),
  ('30000000-0000-0000-0000-00000000000b'::uuid, '20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000001', 4);

-- ------------------------------------------------------------
-- member（alice）
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$ select id from public.allocations where id in ('30000000-0000-0000-0000-00000000000a', '30000000-0000-0000-0000-00000000000b') $$,
  $$ values ('30000000-0000-0000-0000-00000000000a'::uuid) $$,
  'member は自分の allocations だけを参照できる'
);

select is_empty(
  $$
    select a.id from public.allocations as a
    where not exists (select 1 from public.work_records as wr where wr.id = a.work_record_id and wr.user_id = auth.uid())
  $$,
  'member には自分の work_records に紐づかない allocations が見えない'
);

select results_eq(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-000000000002', 2)
    returning project_id
  $$,
  $$ values ('10000000-0000-0000-0000-000000000002'::uuid) $$,
  'member は自分の work_records に allocations を追加できる'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000003', 2)
  $$,
  '42501', null, 'member は他人の work_records に allocations を追加できない'
);

select results_eq(
  $$ update public.allocations set hours = 5 where id = '30000000-0000-0000-0000-00000000000a' returning hours $$,
  $$ values (5::numeric) $$,
  'member は自分の allocations を変更できる'
);

select is_empty(
  $$ update public.allocations set hours = 0 where id = '30000000-0000-0000-0000-00000000000b' returning id $$,
  'member は他人の allocations を変更できない'
);

select throws_ok(
  $$
    update public.allocations set work_record_id = '20000000-0000-0000-0000-00000000000b', project_id = '10000000-0000-0000-0000-000000000002'
    where id = '30000000-0000-0000-0000-00000000000a'
  $$,
  '42501', null, 'member は自分の allocations を他人の work_records に付け替えられない'
);

select is_empty(
  $$ delete from public.allocations where id = '30000000-0000-0000-0000-00000000000b' returning id $$,
  'member は他人の allocations を削除できない'
);

select results_eq(
  $$ delete from public.allocations where id = '30000000-0000-0000-0000-00000000000a' returning id $$,
  $$ values ('30000000-0000-0000-0000-00000000000a'::uuid) $$,
  'member は自分の allocations を削除できる'
);

-- ------------------------------------------------------------
-- admin（全員分を参照できるが、編集はできない）
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$
    select work_record_id from public.allocations
    where work_record_id in ('20000000-0000-0000-0000-00000000000a', '20000000-0000-0000-0000-00000000000b')
    order by work_record_id
  $$,
  $$ values ('20000000-0000-0000-0000-00000000000a'::uuid), ('20000000-0000-0000-0000-00000000000b'::uuid) $$,
  'admin は全員の allocations を参照できる'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000004', 1)
  $$,
  '42501', null, 'admin も他人の work_records に allocations を追加できない'
);

select is_empty(
  $$ update public.allocations set hours = 0 where id = '30000000-0000-0000-0000-00000000000b' returning id $$,
  'admin も他人の allocations を変更できない'
);

select is_empty(
  $$ delete from public.allocations where id = '30000000-0000-0000-0000-00000000000b' returning id $$,
  'admin も他人の allocations を削除できない'
);

-- ------------------------------------------------------------
-- 未ログイン（anon）
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok($$ select id from public.allocations $$, '42501', null, 'anon は allocations を参照できない');
select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-000000000001', 1)
  $$,
  '42501', null, 'anon は allocations を追加できない'
);
select throws_ok($$ update public.allocations set hours = 0 $$, '42501', null, 'anon は allocations を変更できない');
select throws_ok($$ delete from public.allocations $$, '42501', null, 'anon は allocations を削除できない');

-- ------------------------------------------------------------
-- CHECK・一意・外部キー制約
-- ------------------------------------------------------------
select tests.authenticate_as('bob@db-test.invalid');

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000002', -1)
  $$,
  '23514', null, '工数が負の allocations は登録できない'
);

select results_eq(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000002', 0)
    returning hours
  $$,
  $$ values (0::numeric) $$,
  '工数 0 の allocations は登録できる'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000001', 1)
  $$,
  '23505', null, '同じ work_records・同じ案件の allocations は2件登録できない'
);

select tests.clear_authentication();

select results_eq(
  $$ select hours from public.allocations where id = '30000000-0000-0000-0000-00000000000b' $$,
  $$ values (4::numeric) $$,
  '拒否された変更は他人の allocations に反映されていない'
);

select throws_ok(
  $$ delete from public.projects where id = '10000000-0000-0000-0000-000000000001' $$,
  '23503', null, 'allocations から参照されている案件は削除できない（on delete restrict）'
);

select lives_ok(
  $$ delete from public.work_records where id = '20000000-0000-0000-0000-00000000000b' $$,
  'work_records を削除できる'
);

select is_empty(
  $$ select id from public.allocations where work_record_id = '20000000-0000-0000-0000-00000000000b' $$,
  'work_records を削除するとその allocations も削除される（on delete cascade）'
);

select * from finish();
rollback;
