-- shift_settings: 本人のみ参照・追加・変更・削除可（admin の例外なし）、CHECK 制約
begin;
\ir helpers.psql

select plan(24);

select tests.create_standard_users();
select tests.create_user('carol@db-test.invalid');

insert into public.shift_settings (user_id) values
  (tests.user_id('alice@db-test.invalid')),
  (tests.user_id('bob@db-test.invalid'));

-- ------------------------------------------------------------
-- member（alice）
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$ select user_id from public.shift_settings where user_id = auth.uid() $$,
  $$ values (tests.user_id('alice@db-test.invalid')) $$,
  'member は自分の shift_settings を参照できる'
);

select is_empty(
  $$ select user_id from public.shift_settings where user_id <> auth.uid() $$,
  'member は他人の shift_settings を参照できない'
);

select results_eq(
  $$ update public.shift_settings set start_time = '10:00' where user_id = auth.uid() returning start_time $$,
  $$ values ('10:00'::time) $$,
  'member は自分の shift_settings を変更できる'
);

select is_empty(
  $$
    update public.shift_settings set start_time = '10:00'
    where user_id = tests.user_id('bob@db-test.invalid') returning user_id
  $$,
  'member は他人の shift_settings を変更できない'
);

select throws_ok(
  $$ update public.shift_settings set user_id = tests.user_id('carol@db-test.invalid') where user_id = auth.uid() $$,
  '42501', null, 'member は自分の shift_settings を他人のものに付け替えられない'
);

select is_empty(
  $$ delete from public.shift_settings where user_id = tests.user_id('bob@db-test.invalid') returning user_id $$,
  'member は他人の shift_settings を削除できない'
);

select results_eq(
  $$ delete from public.shift_settings where user_id = auth.uid() returning user_id $$,
  $$ values (tests.user_id('alice@db-test.invalid')) $$,
  'member は自分の shift_settings を削除できる'
);

select results_eq(
  $$ insert into public.shift_settings (user_id) values (auth.uid()) returning user_id $$,
  $$ values (tests.user_id('alice@db-test.invalid')) $$,
  'member は自分の shift_settings を追加できる'
);

select throws_ok(
  $$ insert into public.shift_settings (user_id) values (tests.user_id('carol@db-test.invalid')) $$,
  '42501', null, 'member は他人の shift_settings を追加できない'
);

-- ------------------------------------------------------------
-- admin（例外なし: 他人の shift_settings には一切アクセスできない）
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select is_empty(
  $$ select user_id from public.shift_settings where user_id <> auth.uid() $$,
  'admin も他人の shift_settings を参照できない'
);

select throws_ok(
  $$ insert into public.shift_settings (user_id) values (tests.user_id('carol@db-test.invalid')) $$,
  '42501', null, 'admin も他人の shift_settings を追加できない'
);

select is_empty(
  $$
    update public.shift_settings set start_time = '10:00'
    where user_id = tests.user_id('bob@db-test.invalid') returning user_id
  $$,
  'admin も他人の shift_settings を変更できない'
);

select is_empty(
  $$ delete from public.shift_settings where user_id = tests.user_id('bob@db-test.invalid') returning user_id $$,
  'admin も他人の shift_settings を削除できない'
);

select results_eq(
  $$ insert into public.shift_settings (user_id) values (auth.uid()) returning user_id $$,
  $$ values (tests.user_id('admin@db-test.invalid')) $$,
  'admin も自分の shift_settings は追加できる'
);

-- ------------------------------------------------------------
-- 未ログイン（anon）
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok($$ select user_id from public.shift_settings $$, '42501', null, 'anon は shift_settings を参照できない');
select throws_ok(
  $$ insert into public.shift_settings (user_id) values (tests.user_id('carol@db-test.invalid')) $$,
  '42501', null, 'anon は shift_settings を追加できない'
);
select throws_ok(
  $$ update public.shift_settings set start_time = '10:00' $$, '42501', null, 'anon は shift_settings を変更できない'
);
select throws_ok($$ delete from public.shift_settings $$, '42501', null, 'anon は shift_settings を削除できない');

-- ------------------------------------------------------------
-- CHECK 制約（本人としてログインした状態で検証する）
-- ------------------------------------------------------------
select tests.authenticate_as('carol@db-test.invalid');

select throws_ok(
  $$ insert into public.shift_settings (user_id, start_time, end_time) values (auth.uid(), '18:30', '09:30') $$,
  '23514',
  'new row for relation "shift_settings" violates check constraint "shift_settings_time_check"',
  '終業が始業より前の shift_settings は拒否される'
);

select throws_ok(
  $$ insert into public.shift_settings (user_id, start_time, end_time) values (auth.uid(), '09:30', '09:30') $$,
  '23514',
  'new row for relation "shift_settings" violates check constraint "shift_settings_time_check"',
  '終業と始業が同じ shift_settings は拒否される'
);

select throws_ok(
  $$ insert into public.shift_settings (user_id, break_hours) values (auth.uid(), -0.5) $$,
  '23514',
  'new row for relation "shift_settings" violates check constraint "shift_settings_break_check"',
  '休憩時間が負の shift_settings は拒否される'
);

select throws_ok(
  $$ insert into public.shift_settings (user_id, min_hours, max_hours) values (auth.uid(), 181, 180) $$,
  '23514',
  'new row for relation "shift_settings" violates check constraint "shift_settings_hours_check"',
  '稼働下限が上限より大きい shift_settings は拒否される'
);

select throws_ok(
  $$ insert into public.shift_settings (user_id, min_hours, max_hours) values (auth.uid(), -1, 180) $$,
  '23514',
  'new row for relation "shift_settings" violates check constraint "shift_settings_hours_check"',
  '稼働下限が負の shift_settings は拒否される'
);

select results_eq(
  $$
    insert into public.shift_settings (user_id, start_time, end_time, break_hours, min_hours, max_hours)
    values (auth.uid(), '09:00', '09:01', 0, 160, 160)
    returning user_id
  $$,
  $$ values (tests.user_id('carol@db-test.invalid')) $$,
  '境界値（終業 = 始業 + 1分、休憩 0、下限 = 上限）の shift_settings は登録できる'
);

select * from finish();
rollback;
