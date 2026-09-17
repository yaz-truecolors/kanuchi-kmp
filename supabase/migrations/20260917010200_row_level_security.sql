-- Kanuchi (鍛冶) Row Level Security 設定
--
-- 方針 (docs/requirements.md 5節):
--   - work_records / allocations: 本人データのみ参照・編集可、adminは全員分を参照可 (編集は不可)
--   - projects: 全員参照可、編集はadminのみ
--   - user_projects: 本人の割当のみ参照可、割当の管理はadminのみ
--   - shift_settings: 本人のみ参照・編集可 (adminの例外なし)
--
-- マジックリンク認証のみを利用するため、未ログイン (anon ロール) には
-- テーブル権限そのものを付与しない。ログイン済み (authenticated ロール) にのみ
-- 必要な権限を GRANT し、行単位の絞り込みは RLS ポリシーで行う。

-- ============================================================
-- profiles
-- ============================================================
alter table public.profiles enable row level security;

grant select on public.profiles to authenticated;
-- UPDATEは display_name (本人が変更可) と role (adminのみ、トリガーで強制) の
-- 2列に限定する。email/created_at/id 等はここに含めないことで、
-- auth.usersからの複製データや監査用カラムをRLS/トリガーを介さず
-- 直接書き換えられてしまう事故を防ぐ。
grant update (display_name, role) on public.profiles to authenticated;
-- INSERT は handle_new_user() トリガー (SECURITY DEFINER) のみが行うため、
-- authenticated ロールへの INSERT 権限は意図的に付与しない。

create policy "profiles_select_own_or_admin"
  on public.profiles
  for select
  to authenticated
  using (id = auth.uid() or public.is_admin());

create policy "profiles_update_own_or_admin"
  on public.profiles
  for update
  to authenticated
  using (id = auth.uid() or public.is_admin())
  with check (id = auth.uid() or public.is_admin());
-- role の実際の変更可否は enforce_role_change_permission トリガーがさらに検証する。

-- ============================================================
-- projects
-- ============================================================
alter table public.projects enable row level security;

grant select, insert, update, delete on public.projects to authenticated;

create policy "projects_select_all"
  on public.projects
  for select
  to authenticated
  using (true);

create policy "projects_admin_insert"
  on public.projects
  for insert
  to authenticated
  with check (public.is_admin());

create policy "projects_admin_update"
  on public.projects
  for update
  to authenticated
  using (public.is_admin())
  with check (public.is_admin());

create policy "projects_admin_delete"
  on public.projects
  for delete
  to authenticated
  using (public.is_admin());

-- ============================================================
-- user_projects
-- ============================================================
alter table public.user_projects enable row level security;

grant select, insert, update, delete on public.user_projects to authenticated;

create policy "user_projects_select_own_or_admin"
  on public.user_projects
  for select
  to authenticated
  using (user_id = auth.uid() or public.is_admin());

create policy "user_projects_admin_insert"
  on public.user_projects
  for insert
  to authenticated
  with check (public.is_admin());

create policy "user_projects_admin_update"
  on public.user_projects
  for update
  to authenticated
  using (public.is_admin())
  with check (public.is_admin());

create policy "user_projects_admin_delete"
  on public.user_projects
  for delete
  to authenticated
  using (public.is_admin());

-- ============================================================
-- shift_settings (本人のみ。admin例外なし)
-- ============================================================
alter table public.shift_settings enable row level security;

grant select, insert, update, delete on public.shift_settings to authenticated;

create policy "shift_settings_select_own"
  on public.shift_settings
  for select
  to authenticated
  using (user_id = auth.uid());

create policy "shift_settings_insert_own"
  on public.shift_settings
  for insert
  to authenticated
  with check (user_id = auth.uid());

create policy "shift_settings_update_own"
  on public.shift_settings
  for update
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "shift_settings_delete_own"
  on public.shift_settings
  for delete
  to authenticated
  using (user_id = auth.uid());

-- ============================================================
-- work_records (本人が編集、adminは参照のみ)
-- ============================================================
alter table public.work_records enable row level security;

grant select, insert, update, delete on public.work_records to authenticated;

create policy "work_records_select_own_or_admin"
  on public.work_records
  for select
  to authenticated
  using (user_id = auth.uid() or public.is_admin());

create policy "work_records_insert_own"
  on public.work_records
  for insert
  to authenticated
  with check (user_id = auth.uid());

create policy "work_records_update_own"
  on public.work_records
  for update
  to authenticated
  using (user_id = auth.uid())
  with check (user_id = auth.uid());

create policy "work_records_delete_own"
  on public.work_records
  for delete
  to authenticated
  using (user_id = auth.uid());

-- ============================================================
-- allocations (所有者はwork_records経由で判定。adminは参照のみ)
-- ============================================================
alter table public.allocations enable row level security;

grant select, insert, update, delete on public.allocations to authenticated;

create policy "allocations_select_own_or_admin"
  on public.allocations
  for select
  to authenticated
  using (
    exists (
      select 1 from public.work_records wr
      where wr.id = allocations.work_record_id
        and (wr.user_id = auth.uid() or public.is_admin())
    )
  );

create policy "allocations_insert_own"
  on public.allocations
  for insert
  to authenticated
  with check (
    exists (
      select 1 from public.work_records wr
      where wr.id = allocations.work_record_id
        and wr.user_id = auth.uid()
    )
  );

create policy "allocations_update_own"
  on public.allocations
  for update
  to authenticated
  using (
    exists (
      select 1 from public.work_records wr
      where wr.id = allocations.work_record_id
        and wr.user_id = auth.uid()
    )
  )
  with check (
    exists (
      select 1 from public.work_records wr
      where wr.id = allocations.work_record_id
        and wr.user_id = auth.uid()
    )
  );

create policy "allocations_delete_own"
  on public.allocations
  for delete
  to authenticated
  using (
    exists (
      select 1 from public.work_records wr
      where wr.id = allocations.work_record_id
        and wr.user_id = auth.uid()
    )
  );
