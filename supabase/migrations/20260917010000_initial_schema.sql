-- Kanuchi (鍛冶) 初期スキーマ
--
-- テーブル一覧は docs/requirements.md の「6. DBスキーマ (v1)」を参照。
-- Excel の数式で自動計算していた値 (稼働時間・過不足・割合など) はここには保存せず、
-- ドメイン層 (WorkingHoursCalculator 等) で都度計算する導出値として扱う。

-- ============================================================
-- profiles: auth.users と 1:1 のユーザー情報。role で権限を管理する。
-- ============================================================
create table public.profiles (
  id uuid primary key references auth.users (id) on delete cascade,
  email text not null,
  display_name text not null,
  role text not null default 'member' check (role in ('member', 'admin')),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.profiles is 'auth.usersと1:1のユーザー情報。roleで member/admin を管理する。';

-- ============================================================
-- projects: チーム共有の案件マスタ。admin のみが編集する。
-- ============================================================
create table public.projects (
  id uuid primary key default gen_random_uuid(),
  name text not null unique,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now()
);

comment on table public.projects is 'チーム共有の案件マスタ。編集はadminのみ。';

-- ============================================================
-- user_projects: ユーザーごとの担当案件割当 (多対多)。
-- ============================================================
create table public.user_projects (
  user_id uuid not null references public.profiles (id) on delete cascade,
  project_id uuid not null references public.projects (id) on delete cascade,
  created_at timestamptz not null default now(),
  primary key (user_id, project_id)
);

create index user_projects_project_id_idx on public.user_projects (project_id);

comment on table public.user_projects is 'ユーザーごとの担当案件割当。管理はadminのみ。';

-- ============================================================
-- shift_settings: ユーザーごとの勤務時間設定 (始業/終業/休憩/稼働上下限)。
-- ============================================================
create table public.shift_settings (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null unique references public.profiles (id) on delete cascade,
  start_time time not null default '09:30',
  end_time time not null default '18:30',
  break_hours numeric(4, 2) not null default 1.0,
  min_hours numeric(6, 2) not null default 140,
  max_hours numeric(6, 2) not null default 180,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint shift_settings_time_check check (end_time > start_time),
  constraint shift_settings_break_check check (break_hours >= 0),
  constraint shift_settings_hours_check check (min_hours <= max_hours)
);

comment on table public.shift_settings is 'ユーザーごとの勤務時間設定。本人のみ参照・編集可。';

-- ============================================================
-- work_records: 日次の稼働実績。1ユーザー・1日につき1レコード。
-- ============================================================
create table public.work_records (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null references public.profiles (id) on delete cascade,
  work_date date not null,
  clock_in time,
  clock_out time,
  break_hours numeric(4, 2),
  -- Excelの休暇記号(祝)・不良記号(欠)に相当。値が無ければ通常稼働日として扱う。
  flag text check (flag in ('holiday', 'absence')),
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (user_id, work_date)
);

create index work_records_work_date_idx on public.work_records (work_date);

comment on table public.work_records is '日次の稼働実績。本人データのみ参照・編集可、adminは全員分を参照可。';

-- ============================================================
-- allocations: 日次実績に対する案件別工数配分。
-- ============================================================
create table public.allocations (
  id uuid primary key default gen_random_uuid(),
  work_record_id uuid not null references public.work_records (id) on delete cascade,
  project_id uuid not null references public.projects (id) on delete restrict,
  hours numeric(4, 2) not null check (hours >= 0),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  unique (work_record_id, project_id)
);

comment on table public.allocations is '日×案件の工数配分。所有者はwork_records経由で判定する。';
