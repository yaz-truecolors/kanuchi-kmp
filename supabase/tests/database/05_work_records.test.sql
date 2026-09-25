-- work_records: 本人のみ追加・変更・削除可、参照は本人 or admin（admin は参照のみ）、CHECK・一意制約
begin;
\ir helpers.psql

select plan(25);

select tests.create_standard_users();

insert into public.work_records (id, user_id, work_date, clock_in, clock_out, break_hours) values
  ('20000000-0000-0000-0000-00000000000a'::uuid, tests.user_id('alice@db-test.invalid'), '2026-09-01', '09:30', '18:30', 1),
  ('20000000-0000-0000-0000-00000000000b'::uuid, tests.user_id('bob@db-test.invalid'), '2026-09-01', '09:30', '18:30', 1);

-- ------------------------------------------------------------
-- member（alice）
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$ select id from public.work_records where user_id = auth.uid() $$,
  $$ values ('20000000-0000-0000-0000-00000000000a'::uuid) $$,
  'member は自分の work_records を参照できる'
);

select is_empty(
  $$ select id from public.work_records where user_id <> auth.uid() $$,
  'member は他人の work_records を参照できない'
);

select results_eq(
  $$ insert into public.work_records (user_id, work_date) values (auth.uid(), '2026-09-02') returning work_date $$,
  $$ values ('2026-09-02'::date) $$,
  'member は自分の work_records を追加できる'
);

select throws_ok(
  $$ insert into public.work_records (user_id, work_date) values (tests.user_id('bob@db-test.invalid'), '2026-09-02') $$,
  '42501', null, 'member は他人の work_records を追加できない'
);

select results_eq(
  $$ update public.work_records set note = '自分のメモ' where id = '20000000-0000-0000-0000-00000000000a' returning note $$,
  $$ values ('自分のメモ') $$,
  'member は自分の work_records を変更できる'
);

select is_empty(
  $$ update public.work_records set note = 'hacked' where id = '20000000-0000-0000-0000-00000000000b' returning id $$,
  'member は他人の work_records を変更できない'
);

select throws_ok(
  $$
    update public.work_records set user_id = tests.user_id('bob@db-test.invalid'), work_date = '2026-09-03'
    where id = '20000000-0000-0000-0000-00000000000a'
  $$,
  '42501', null, 'member は自分の work_records を他人のものに付け替えられない'
);

select is_empty(
  $$ delete from public.work_records where id = '20000000-0000-0000-0000-00000000000b' returning id $$,
  'member は他人の work_records を削除できない'
);

select results_eq(
  $$ delete from public.work_records where user_id = auth.uid() and work_date = '2026-09-02' returning work_date $$,
  $$ values ('2026-09-02'::date) $$,
  'member は自分の work_records を削除できる'
);

-- ------------------------------------------------------------
-- admin（全員分を参照できるが、編集はできない）
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$
    select id from public.work_records
    where id in ('20000000-0000-0000-0000-00000000000a', '20000000-0000-0000-0000-00000000000b')
    order by id
  $$,
  $$ values ('20000000-0000-0000-0000-00000000000a'::uuid), ('20000000-0000-0000-0000-00000000000b'::uuid) $$,
  'admin は全員の work_records を参照できる'
);

select throws_ok(
  $$ insert into public.work_records (user_id, work_date) values (tests.user_id('alice@db-test.invalid'), '2026-09-04') $$,
  '42501', null, 'admin も他人の work_records を追加できない'
);

select is_empty(
  $$ update public.work_records set note = 'hacked' where id = '20000000-0000-0000-0000-00000000000a' returning id $$,
  'admin も他人の work_records を変更できない'
);

select is_empty(
  $$ delete from public.work_records where id = '20000000-0000-0000-0000-00000000000a' returning id $$,
  'admin も他人の work_records を削除できない'
);

select results_eq(
  $$ insert into public.work_records (user_id, work_date) values (auth.uid(), '2026-09-01') returning work_date $$,
  $$ values ('2026-09-01'::date) $$,
  'admin も自分の work_records は追加できる'
);

-- ------------------------------------------------------------
-- 未ログイン（anon）
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok($$ select id from public.work_records $$, '42501', null, 'anon は work_records を参照できない');
select throws_ok(
  $$ insert into public.work_records (user_id, work_date) values (tests.user_id('alice@db-test.invalid'), '2026-09-05') $$,
  '42501', null, 'anon は work_records を追加できない'
);
select throws_ok($$ update public.work_records set note = 'hacked' $$, '42501', null, 'anon は work_records を変更できない');
select throws_ok($$ delete from public.work_records $$, '42501', null, 'anon は work_records を削除できない');

-- ------------------------------------------------------------
-- CHECK・一意制約（本人としてログインした状態で検証する）
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$
    insert into public.work_records (user_id, work_date, flag)
    values (auth.uid(), '2026-09-10', 'holiday'), (auth.uid(), '2026-09-11', 'absence'), (auth.uid(), '2026-09-12', null)
    returning flag
  $$,
  $$ values ('holiday'), ('absence'), (null::text) $$,
  'flag は holiday / absence / null を登録できる'
);

select throws_ok(
  $$ insert into public.work_records (user_id, work_date, flag) values (auth.uid(), '2026-09-13', 'vacation') $$,
  '23514', null, 'flag に holiday / absence 以外の値は登録できない'
);

select throws_ok(
  $$ insert into public.work_records (user_id, work_date, break_hours) values (auth.uid(), '2026-09-13', -1) $$,
  '23514', null, '休憩時間が負の work_records は登録できない'
);

select results_eq(
  $$ insert into public.work_records (user_id, work_date, break_hours) values (auth.uid(), '2026-09-13', 0) returning break_hours $$,
  $$ values (0::numeric) $$,
  '休憩時間 0 の work_records は登録できる'
);

select throws_ok(
  $$ insert into public.work_records (user_id, work_date) values (auth.uid(), '2026-09-01') $$,
  '23505', null, '同じユーザー・同じ日の work_records は2件登録できない'
);

select tests.authenticate_as('bob@db-test.invalid');

select results_eq(
  $$ insert into public.work_records (user_id, work_date) values (auth.uid(), '2026-09-10') returning work_date $$,
  $$ values ('2026-09-10'::date) $$,
  '別のユーザーであれば同じ日の work_records を登録できる'
);

select tests.clear_authentication();

select results_eq(
  $$
    select count(*) from public.work_records
    where user_id = tests.user_id('bob@db-test.invalid') and note = 'hacked'
  $$,
  array[0::bigint],
  '拒否された変更は他人の work_records に反映されていない'
);

select * from finish();
rollback;
