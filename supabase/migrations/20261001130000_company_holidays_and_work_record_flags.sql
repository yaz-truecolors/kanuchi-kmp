-- Kanuchi (鍛冶) 日次入力・集計の土台: 会社の休業日 (company_holidays)、admin による勤務時間設定の参照、日ごとの印の意味
--
-- 方針 (docs/requirements.md の 5節・6節・9節):
--   - 稼働日は「平日 (月〜金) かつ日本の祝日・会社の休業日でない日」。日本の祝日はアプリ (domain の JapaneseHolidays) が
--     祝日法のルールで判定するため DB には持たない。会社独自の休業日だけを company_holidays に登録する。
--     company_holidays は全員が参照でき (稼働時間の計算に使う)、追加・削除は admin のみ (変更はしない。名前を直す場合は削除して追加し直す)。
--   - 管理者ダッシュボードで各メンバーの定時・稼働時間の下限/上限を使うため、admin は全員分の shift_settings を参照できる
--     (編集は従来どおり本人のみ)。
--   - work_records.flag の値 (holiday / absence) は変えず、意味を「休 (個人の休暇)」「欠 (欠勤)」と定める。

-- ============================================================
-- company_holidays: 会社の休業日 (土日・日本の祝日以外で、会社として休みにする日)。
-- created_by は登録した admin。アプリ (PostgREST、JWTあり) から登録した場合のみ auth.uid() で自動設定される
-- (invitations.invited_by と同じ。クライアントから指定できないよう、INSERT は holiday_date・name 列のみに限定する)。
-- ============================================================
create table public.company_holidays (
  holiday_date date primary key,
  name text not null,
  created_by uuid default auth.uid() references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  -- 名前は前後の空白なしで 1〜50 文字 (アプリ側の CompanyHoliday.MAX_NAME_LENGTH と一致させること)
  constraint company_holidays_name_check check (name = btrim(name) and char_length(name) between 1 and 50)
);

comment on table public.company_holidays is '会社の休業日 (土日・日本の祝日以外の休み)。全員参照可、追加・削除はadminのみ。';

alter table public.company_holidays enable row level security;

grant select, delete on public.company_holidays to authenticated;
grant insert (holiday_date, name) on public.company_holidays to authenticated;

create policy "company_holidays_select_all"
  on public.company_holidays
  for select
  to authenticated
  using (true);

create policy "company_holidays_admin_insert"
  on public.company_holidays
  for insert
  to authenticated
  with check (public.is_admin());

create policy "company_holidays_admin_delete"
  on public.company_holidays
  for delete
  to authenticated
  using (public.is_admin());

-- ============================================================
-- shift_settings: admin は全員分を参照できる (管理者ダッシュボード用)。編集は本人のみのまま。
-- ============================================================
create policy "shift_settings_admin_select"
  on public.shift_settings
  for select
  to authenticated
  using (public.is_admin());

comment on table public.shift_settings is 'ユーザーごとの勤務時間設定。本人のみ編集可、参照は本人とadmin。';

-- ============================================================
-- work_records.flag: 日ごとの印の意味 (値は変更しない)。
-- ============================================================
comment on column public.work_records.flag is
  '日ごとの印。holiday = 休 (個人の休暇。稼働0、その月の営業日数から除く)、absence = 欠 (欠勤。稼働0、営業日数には含める)、NULL = 印なし。';
