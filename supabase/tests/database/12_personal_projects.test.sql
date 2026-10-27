-- 個人案件（projects.owner_id があるもの）: 本人だけが作成・削除（論理削除）でき、参照は本人と admin だけ。
-- 名前は重複してよい。削除は当月以降の日に工数があるとできない。工数を新しく配分できるのは本人の削除していない個人案件
begin;
\ir helpers.psql

select plan(33);

select tests.create_standard_users();

insert into public.projects (id, name, owner_id) values
  ('10000000-0000-0000-0000-000000000001'::uuid, 'db-test 共有', null),
  ('10000000-0000-0000-0000-000000000011'::uuid, 'db-test alice 未使用', tests.user_id('alice@db-test.invalid')),
  ('10000000-0000-0000-0000-000000000012'::uuid, 'db-test alice 今月使用', tests.user_id('alice@db-test.invalid')),
  ('10000000-0000-0000-0000-000000000013'::uuid, 'db-test alice 先月まで使用', tests.user_id('alice@db-test.invalid')),
  ('10000000-0000-0000-0000-000000000014'::uuid, 'db-test alice 来月使用', tests.user_id('alice@db-test.invalid')),
  ('10000000-0000-0000-0000-000000000021'::uuid, 'db-test bob 個人', tests.user_id('bob@db-test.invalid'));

-- 日付は日本時間の今月を基準にする（削除できるかの判定が日本時間の今月1日以降の工数で決まるため）
insert into public.work_records (id, user_id, work_date) values
  (
    '20000000-0000-0000-0000-00000000000a'::uuid, tests.user_id('alice@db-test.invalid'),
    date_trunc('month', now() at time zone 'Asia/Tokyo')::date
  ),
  (
    '20000000-0000-0000-0000-00000000000b'::uuid, tests.user_id('alice@db-test.invalid'),
    (date_trunc('month', now() at time zone 'Asia/Tokyo') - interval '1 day')::date
  ),
  (
    '20000000-0000-0000-0000-00000000000c'::uuid, tests.user_id('alice@db-test.invalid'),
    (date_trunc('month', now() at time zone 'Asia/Tokyo') + interval '1 month')::date
  ),
  (
    '20000000-0000-0000-0000-00000000000d'::uuid, tests.user_id('alice@db-test.invalid'),
    (date_trunc('month', now() at time zone 'Asia/Tokyo') + interval '1 day')::date
  );

-- 直接DB接続で用意する配分（guard_allocation_project の対象外）
insert into public.allocations (id, work_record_id, project_id, hours) values
  ('30000000-0000-0000-0000-00000000000a'::uuid, '20000000-0000-0000-0000-00000000000a', '10000000-0000-0000-0000-000000000012', 1),
  ('30000000-0000-0000-0000-00000000000b'::uuid, '20000000-0000-0000-0000-00000000000b', '10000000-0000-0000-0000-000000000013', 2),
  ('30000000-0000-0000-0000-00000000000c'::uuid, '20000000-0000-0000-0000-00000000000c', '10000000-0000-0000-0000-000000000014', 3);

select ok(
  not has_function_privilege('anon', 'public.delete_personal_project(uuid)', 'EXECUTE')
    and has_function_privilege('authenticated', 'public.delete_personal_project(uuid)', 'EXECUTE'),
  'delete_personal_project は authenticated だけが実行できる（anon は実行できない）'
);

select throws_ok(
  $$ update public.projects set deleted_at = now() where id = '10000000-0000-0000-0000-000000000001' $$,
  '23514', null, '共有の案件は論理削除できない（check 制約。直接DB接続でも）'
);

-- ------------------------------------------------------------
-- 本人（alice）: 作成・参照
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  format(
    $$ insert into public.projects (name, owner_id) values ('db-test alice 新規', %L) returning name, owner_id, is_active, deleted_at $$,
    tests.user_id('alice@db-test.invalid')
  ),
  format(
    $$ values ('db-test alice 新規'::text, %L::uuid, true, null::timestamptz) $$,
    tests.user_id('alice@db-test.invalid')
  ),
  '本人は自分を作成者として個人案件を追加できる（有効・削除されていない状態で作られる）'
);

select results_eq(
  format(
    $$
      insert into public.projects (name, owner_id) values ('db-test 共有', %1$L), ('db-test 共有', %1$L), ('db-test bob 個人', %1$L)
      returning name
    $$,
    tests.user_id('alice@db-test.invalid')
  ),
  $$ values ('db-test 共有'::text), ('db-test 共有'), ('db-test bob 個人') $$,
  '個人案件の名前は、共有の案件・自分の個人案件・他人の個人案件と重複してよい'
);

select throws_ok(
  format(
    $$ insert into public.projects (name, owner_id) values ('db-test 他人名義', %L) $$,
    tests.user_id('bob@db-test.invalid')
  ),
  '42501', null, '他人を作成者とする個人案件は追加できない'
);

select throws_ok(
  format(
    $$ insert into public.projects (name, owner_id, deleted_at) values ('db-test 削除済みで作成', %L, now()) $$,
    tests.user_id('alice@db-test.invalid')
  ),
  '42501', null, '削除日時（deleted_at）を指定して追加できない（列単位の権限）'
);

select results_eq(
  $$ select id from public.projects where name like 'db-test%' and id::text like '10000000-%' order by id $$,
  $$
    values
      ('10000000-0000-0000-0000-000000000001'::uuid),
      ('10000000-0000-0000-0000-000000000011'::uuid),
      ('10000000-0000-0000-0000-000000000012'::uuid),
      ('10000000-0000-0000-0000-000000000013'::uuid),
      ('10000000-0000-0000-0000-000000000014'::uuid)
  $$,
  '本人は共有の案件と自分の個人案件を参照でき、他人の個人案件は参照できない'
);

-- ------------------------------------------------------------
-- 本人（alice）: 変更・物理削除はできない
-- ------------------------------------------------------------
select is_empty(
  $$ update public.projects set name = 'hacked' where id = '10000000-0000-0000-0000-000000000011' returning id $$,
  '本人は個人案件の名前を変更できない'
);

select throws_ok(
  $$ update public.projects set owner_id = null where id = '10000000-0000-0000-0000-000000000011' $$,
  '42501', null, '作成者（owner_id）は変更できない（列単位の権限）'
);

select throws_ok(
  $$ update public.projects set deleted_at = now() where id = '10000000-0000-0000-0000-000000000011' $$,
  '42501', null, '削除日時（deleted_at）は直接変更できない（列単位の権限）'
);

select is_empty(
  $$ delete from public.projects where id = '10000000-0000-0000-0000-000000000011' returning id $$,
  '本人は個人案件を物理削除できない'
);

-- ------------------------------------------------------------
-- 本人（alice）: 工数の配分
-- ------------------------------------------------------------
select results_eq(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000d', '10000000-0000-0000-0000-000000000011', 1)
    returning project_id
  $$,
  $$ values ('10000000-0000-0000-0000-000000000011'::uuid) $$,
  '自分の個人案件には、担当の割当が無くても新しく配分できる'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000d', '10000000-0000-0000-0000-000000000021', 1)
  $$,
  '42501', 'project_not_assigned', '他人の個人案件には配分できない'
);

-- 上で配分した分を消して、未使用の個人案件に戻す
select results_eq(
  $$
    delete from public.allocations
    where work_record_id = '20000000-0000-0000-0000-00000000000d' and project_id = '10000000-0000-0000-0000-000000000011'
    returning hours
  $$,
  $$ values (1::numeric) $$,
  '自分の個人案件への配分は削除できる'
);

-- ------------------------------------------------------------
-- 本人（alice）: 削除（delete_personal_project）
-- ------------------------------------------------------------
select throws_ok(
  $$ select public.delete_personal_project('10000000-0000-0000-0000-000000000012') $$,
  'P0001', 'personal_project_in_use', '今月の日に工数がある個人案件は削除できない'
);

select throws_ok(
  $$ select public.delete_personal_project('10000000-0000-0000-0000-000000000014') $$,
  'P0001', 'personal_project_in_use', '来月以降の日に工数がある個人案件も削除できない'
);

select lives_ok(
  $$ select public.delete_personal_project('10000000-0000-0000-0000-000000000011') $$,
  '工数の無い個人案件は削除できる'
);

select lives_ok(
  $$ select public.delete_personal_project('10000000-0000-0000-0000-000000000013') $$,
  '先月までにしか工数が無い個人案件は削除できる'
);

select lives_ok(
  $$ select public.delete_personal_project('10000000-0000-0000-0000-000000000013') $$,
  '削除済みの個人案件をもう一度削除してもエラーにならない'
);

select throws_ok(
  $$ select public.delete_personal_project('10000000-0000-0000-0000-000000000021') $$,
  'P0002', 'personal_project_not_found', '他人の個人案件は削除できない'
);

select throws_ok(
  $$ select public.delete_personal_project('10000000-0000-0000-0000-000000000001') $$,
  'P0002', 'personal_project_not_found', '共有の案件は削除できない'
);

select results_eq(
  $$
    select id, deleted_at is not null from public.projects
    where id in ('10000000-0000-0000-0000-000000000011', '10000000-0000-0000-0000-000000000012', '10000000-0000-0000-0000-000000000013')
    order by id
  $$,
  $$
    values
      ('10000000-0000-0000-0000-000000000011'::uuid, true),
      ('10000000-0000-0000-0000-000000000012'::uuid, false),
      ('10000000-0000-0000-0000-000000000013'::uuid, true)
  $$,
  '削除は論理削除で、削除した個人案件も本人は参照できる（先月までの集計に使う）'
);

select results_eq(
  $$ select hours from public.allocations where id = '30000000-0000-0000-0000-00000000000b' $$,
  $$ values (2::numeric) $$,
  '削除した個人案件の先月までの工数は残る'
);

select throws_ok(
  $$
    insert into public.allocations (work_record_id, project_id, hours)
    values ('20000000-0000-0000-0000-00000000000d', '10000000-0000-0000-0000-000000000013', 1)
  $$,
  '42501', 'project_not_assigned', '削除した個人案件には新しく配分できない'
);

select results_eq(
  $$ update public.allocations set hours = 0.5 where id = '30000000-0000-0000-0000-00000000000b' returning hours $$,
  $$ values (0.5::numeric) $$,
  '削除した個人案件でも、既にある配分の工数は変更できる'
);

-- ------------------------------------------------------------
-- 他人（bob）
-- ------------------------------------------------------------
select tests.authenticate_as('bob@db-test.invalid');

select is_empty(
  $$ select id from public.projects where owner_id = tests.user_id('alice@db-test.invalid') $$,
  '他人は個人案件を参照できない'
);

-- ------------------------------------------------------------
-- admin
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$ select name from public.projects where id = '10000000-0000-0000-0000-000000000021' $$,
  $$ values ('db-test bob 個人'::text) $$,
  'admin は他のユーザーの個人案件を参照できる（稼働の集計の閲覧に使う）'
);

select is_empty(
  $$ update public.projects set is_active = false where id = '10000000-0000-0000-0000-000000000021' returning id $$,
  'admin は個人案件を変更できない'
);

select is_empty(
  $$ delete from public.projects where id = '10000000-0000-0000-0000-000000000021' returning id $$,
  'admin は個人案件を物理削除できない'
);

select throws_ok(
  $$ select public.delete_personal_project('10000000-0000-0000-0000-000000000021') $$,
  'P0002', 'personal_project_not_found', 'admin も他のユーザーの個人案件は削除できない'
);

select throws_ok(
  format(
    $$ insert into public.user_projects (user_id, project_id) values (%L, '10000000-0000-0000-0000-000000000021') $$,
    tests.user_id('bob@db-test.invalid')
  ),
  '42501', null, '個人案件は担当の割当に使えない'
);

select throws_ok(
  $$ insert into public.projects (name) values ('db-test 共有') $$,
  '23505', null, '共有の案件の名前は、共有の案件の中では重複できない'
);

select results_eq(
  $$ insert into public.projects (name) values ('db-test alice 未使用') returning name $$,
  $$ values ('db-test alice 未使用'::text) $$,
  '共有の案件の名前は、個人案件の名前と重複してよい'
);

select * from finish();
rollback;
