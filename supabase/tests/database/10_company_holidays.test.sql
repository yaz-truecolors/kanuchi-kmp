-- company_holidays: 全員参照可、追加・削除は admin のみ（変更は不可）、created_by の自動設定と偽装拒否、CHECK・主キー制約
begin;
\ir helpers.psql

select plan(29);

select tests.create_standard_users();

insert into public.company_holidays (holiday_date, name) values ('2099-12-29', 'db-test 年末休業');

-- ------------------------------------------------------------
-- member（alice）
-- ------------------------------------------------------------
select tests.authenticate_as('alice@db-test.invalid');

select results_eq(
  $$ select name from public.company_holidays where holiday_date = '2099-12-29' $$,
  $$ values ('db-test 年末休業') $$,
  'member は company_holidays を参照できる'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-01', 'db-test member の休業日') $$,
  '42501', null, 'member は company_holidays を追加できない'
);

select throws_ok(
  $$ update public.company_holidays set name = 'hacked' where holiday_date = '2099-12-29' $$,
  '42501', null, 'member は company_holidays を変更できない（UPDATE 権限なし）'
);

select is_empty(
  $$ delete from public.company_holidays where holiday_date = '2099-12-29' returning holiday_date $$,
  'member は company_holidays を削除できない'
);

-- ------------------------------------------------------------
-- admin
-- ------------------------------------------------------------
select tests.authenticate_as('admin@db-test.invalid');

select results_eq(
  $$ select name from public.company_holidays where holiday_date = '2099-12-29' $$,
  $$ values ('db-test 年末休業') $$,
  'admin は company_holidays を参照できる'
);

select results_eq(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-30', 'db-test 年末休業2') returning name, created_by $$,
  $$ values ('db-test 年末休業2', tests.user_id('admin@db-test.invalid')) $$,
  'admin は company_holidays を追加でき、created_by は登録した admin 本人になる'
);

select throws_ok(
  $$
    insert into public.company_holidays (holiday_date, name, created_by)
    values ('2099-12-02', 'db-test 偽装', tests.user_id('bob@db-test.invalid'))
  $$,
  '42501', null, 'created_by 列はアプリ経由では指定できない（holiday_date・name 列のみ INSERT 可）'
);

select throws_ok(
  $$ update public.company_holidays set name = 'changed' where holiday_date = '2099-12-29' $$,
  '42501', null, 'admin も company_holidays を変更できない（UPDATE 権限なし。削除して追加し直す）'
);

select results_eq(
  $$ delete from public.company_holidays where holiday_date = '2099-12-30' returning holiday_date $$,
  $$ values ('2099-12-30'::date) $$,
  'admin は company_holidays を削除できる'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-29', 'db-test 重複') $$,
  '23505', null, '同じ日付の休業日は重複して登録できない'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-03', '') $$,
  '23514',
  'new row for relation "company_holidays" violates check constraint "company_holidays_name_check"',
  '名前が空の休業日は登録できない'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-03', '   ') $$,
  '23514',
  'new row for relation "company_holidays" violates check constraint "company_holidays_name_check"',
  '名前が空白だけの休業日は登録できない'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-03', ' db-test 前後に空白 ') $$,
  '23514',
  'new row for relation "company_holidays" violates check constraint "company_holidays_name_check"',
  '名前の前後に空白がある休業日は登録できない'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-03', E'\tdb-test 先頭にタブ') $$,
  '23514',
  'new row for relation "company_holidays" violates check constraint "company_holidays_name_check"',
  '名前の先頭にタブがある休業日は登録できない'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-03', E'db-test 末尾に改行\n') $$,
  '23514',
  'new row for relation "company_holidays" violates check constraint "company_holidays_name_check"',
  '名前の末尾に改行がある休業日は登録できない'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-03', U&'\3000db-test 先頭に全角スペース') $$,
  '23514',
  'new row for relation "company_holidays" violates check constraint "company_holidays_name_check"',
  '名前の先頭に全角スペースがある休業日は登録できない'
);

select results_eq(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-09', U&'db-test 年末\3000年始') returning name $$,
  $$ values (U&'db-test 年末\3000年始') $$,
  '名前の途中の全角スペースは許可される'
);

select results_eq(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-10', repeat(U&'\+01F38D', 50)) returning char_length(name) $$,
  $$ values (50) $$,
  '名前の文字数はコードポイント数で数える（絵文字50文字は登録できる）'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-03', repeat('あ', 51)) $$,
  '23514',
  'new row for relation "company_holidays" violates check constraint "company_holidays_name_check"',
  '名前が51文字の休業日は登録できない'
);

select results_eq(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-04', repeat('あ', 50)) returning char_length(name) $$,
  $$ values (50) $$,
  '名前が50文字の休業日は登録できる（境界値）'
);

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values (null, 'db-test 日付なし') $$,
  '23502', null, '日付の無い休業日は登録できない'
);

-- ------------------------------------------------------------
-- 利用停止中の admin（is_admin() が false になるため、admin の権限を持たない）
-- ------------------------------------------------------------
select tests.clear_authentication();
update public.profiles set suspended_at = now() where id = tests.user_id('admin@db-test.invalid');
select tests.authenticate_as('admin@db-test.invalid');

select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-05', 'db-test 利用停止中') $$,
  '42501', null, '利用停止中の admin は company_holidays を追加できない'
);

select is_empty(
  $$ delete from public.company_holidays where holiday_date = '2099-12-29' returning holiday_date $$,
  '利用停止中の admin は company_holidays を削除できない'
);

select tests.clear_authentication();
update public.profiles set suspended_at = null where id = tests.user_id('admin@db-test.invalid');

-- ------------------------------------------------------------
-- 未ログイン（anon）
-- ------------------------------------------------------------
select tests.authenticate_as_anon();

select throws_ok($$ select holiday_date from public.company_holidays $$, '42501', null, 'anon は company_holidays を参照できない');
select throws_ok(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-06', 'db-test anon') $$,
  '42501', null, 'anon は company_holidays を追加できない'
);
select throws_ok(
  $$ update public.company_holidays set name = 'hacked' $$, '42501', null, 'anon は company_holidays を変更できない'
);
select throws_ok($$ delete from public.company_holidays $$, '42501', null, 'anon は company_holidays を削除できない');

-- ------------------------------------------------------------
-- 直接DB接続（Supabase Studio 等）からの登録では created_by は NULL になる
-- ------------------------------------------------------------
select tests.clear_authentication();

select results_eq(
  $$ insert into public.company_holidays (holiday_date, name) values ('2099-12-07', 'db-test Studio から登録') returning created_by $$,
  $$ values (null::uuid) $$,
  '直接DB接続から登録した休業日の created_by は NULL'
);

-- ------------------------------------------------------------
-- 列のコメント（work_records.flag の意味）
-- ------------------------------------------------------------
select matches(
  col_description('public.work_records'::regclass, (
    select attnum from pg_attribute where attrelid = 'public.work_records'::regclass and attname = 'flag'
  )),
  '^日ごとの印。holiday = 休 .*absence = 欠 ',
  'work_records.flag のコメントに印の意味（holiday = 休、absence = 欠）が書かれている'
);

select * from finish();
rollback;
