-- allocations の案件の制限（guard_allocation_project）: 新しく配分できるのは本人に割り当てられた有効な案件だけ。
-- その日に既に配分がある案件は、担当を外された・無効にされた後も変更・削除できる
begin;
\ir helpers.psql

select plan(14);

select tests.create_standard_users();

insert into public.projects (id, name, is_active) values
  ('10000000-0000-0000-0000-000000000001'::uuid, 'db-test 割当済み', true),
  ('10000000-0000-0000-0000-000000000002'::uuid, 'db-test 未割当', true),
  ('10000000-0000-0000-0000-000000000003'::uuid, 'db-test 割当済み・無効', false),
  ('10000000-0000-0000-0000-000000000004'::uuid, 'db-test 担当を外した', true);

insert into public.user_projects (user_id, project_id) values
  (tests.user_id('alice@db-test.invalid'), '10000000-0000-0000-0000-000000000001'),
  (tests.user_id('alice@db-test.invalid'), '10000000-0000-0000-0000-000000000003'),
  -- 他人（bob）にだけ割り当てた案件は、alice の配分には使えない
  (tests.user_id('bob@db-test.invalid'), '10000000-0000-0000-0000-000000000002');

insert into public.work_records (id, user_id, work_date) values
  ('20000000-0000-0000-0000-00000000000a'::uuid, tests.user_id('alice@db-test.invalid'), '2026-09-01'),
  ('20000000-0000-0000-0000-00000000000b'::uuid, tests.user_id('alice@db-test.invalid'), '2026-09-02');

-- 担当を外される前に配分した案件（直接DB接続で用意する）
insert into public.allocations (id, work_record_id, project_id, hours) values
  ('30000000-0000-0000-0000-00000000000a'::uuid, '20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-000000000004', 2);

select ok(
  not has_function_privilege('authenticated', 'public.guard_allocation_project()', 'EXECUTE')
    and not has_function_privilege('anon', 'public.guard_allocation_project()', 'EXECUTE'),
  'トリガー関数 guard_allocation_project は anon / authenticated から実行できない'
);

select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-000000000001', 3)
    returning project_id
  $$,
  $$ values ('10000000-0000-0000-0000-000000000001'::uuid) $$,
  '割り当てられた有効な案件には新しく配分できる'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000002', 1)
  $$,
  '42501', 'project_not_assigned', '割り当てられていない案件には新しく配分できない（他人に割り当てられた案件も同じ）'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000003', 1)
  $$,
  '42501', 'project_not_assigned', '無効な案件には、割り当てられていても新しく配分できない'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000002', 1)
    on conflict (work_record_id, project_id) do update set hours = excluded.hours
  $$,
  '42501', 'project_not_assigned', 'upsert でも、割り当てられていない案件には新しく配分できない'
);

select results_eq(
  $$ update public.allocations set hours = 1.5 where id = '30000000-0000-0000-0000-00000000000a' returning hours $$,
  $$ values (1.5::numeric) $$,
  '担当を外された案件でも、既にある配分の工数は変更できる'
);

select results_eq(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-000000000004', 0.5)
    on conflict (work_record_id, project_id) do update
      set project_id = excluded.project_id, hours = excluded.hours
    returning hours
  $$,
  $$ values (0.5::numeric) $$,
  '担当を外された案件でも、既にある配分は upsert で変更できる（アプリの保存と同じ）'
);

select throws_ok(
  $$
    update public.allocations set project_id = '10000000-0000-0000-0000-000000000002'
    where work_record_id = '20000000-0000-0000-0000-00000000000a' and project_id = '10000000-0000-0000-0000-000000000001'
  $$,
  '42501', 'project_not_assigned', '割り当てられていない案件に付け替えられない'
);

select throws_ok(
  $$
    update public.allocations set work_record_id = '20000000-0000-0000-0000-00000000000b'
    where id = '30000000-0000-0000-0000-00000000000a'
  $$,
  '42501', 'project_not_assigned', '担当を外された案件の配分を、別の日に付け替えられない'
);

select results_eq(
  $$
    update public.allocations set work_record_id = '20000000-0000-0000-0000-00000000000b'
    where work_record_id = '20000000-0000-0000-0000-00000000000a' and project_id = '10000000-0000-0000-0000-000000000001'
    returning work_record_id
  $$,
  $$ values ('20000000-0000-0000-0000-00000000000b'::uuid) $$,
  '割り当てられた有効な案件の配分は、別の日に付け替えられる'
);

select results_eq(
  $$ delete from public.allocations where id = '30000000-0000-0000-0000-00000000000a' returning id $$,
  $$ values ('30000000-0000-0000-0000-00000000000a'::uuid) $$,
  '担当を外された案件でも、既にある配分は削除できる'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-000000000004', 1)
  $$,
  '42501', 'project_not_assigned', '削除した後は、担当を外された案件に配分し直せない'
);

select tests.clear_authentication();

select lives_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000002', 1)
  $$,
  '直接DB接続（データの修正等）は、割り当てられていない案件にも配分できる'
);

select results_eq(
  $$
    select project_id from public.allocations
    where work_record_id in ('20000000-0000-0000-0000-00000000000a', '20000000-0000-0000-0000-00000000000b')
    order by project_id
  $$,
  $$ values ('10000000-0000-0000-0000-000000000001'::uuid), ('10000000-0000-0000-0000-000000000002'::uuid) $$,
  '拒否された追加・付け替えは反映されていない'
);

select * from finish();
rollback;
