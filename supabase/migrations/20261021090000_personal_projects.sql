-- Kanuchi (鍛冶) 個人案件: 各ユーザーが自分だけの案件を作成・削除できるようにする
--
-- 方針 (docs/requirements.md の 5節・6節・9節):
--   - projects に作成者 (owner_id) を持たせ、owner_id が null の行を従来どおりの「共有の案件」(admin が管理し、担当を割り当てる)、
--     owner_id がある行を「個人案件」(本人だけが使い、他のユーザーには影響しない) とする。
--   - 個人案件は本人と admin だけが参照できる (admin は管理者ダッシュボード・案件別集計で全員分の稼働を見るため)。
--     作成は本人だけ、削除は本人だけが delete_personal_project() で行う (名前の変更・有効/無効の切り替えは無い)。
--   - 個人案件の削除は論理削除 (deleted_at) にする。先月までの工数は集計で引き続き見られるよう残す。
--     当月以降 (日本時間) の日に工数が配分されている場合は削除できない。
--   - 個人案件の名前は重複してよい (同じユーザーの個人案件同士・他人の個人案件・共有の案件のいずれとも)。
--     共有の案件の名前は従来どおり共有の案件の中で一意にする。
--   - 工数を新しく配分できる案件は「本人に割り当てられた有効な共有の案件」に加えて「本人の削除していない個人案件」にする
--     (guard_allocation_project)。共有の案件の担当の割当 (user_projects) には個人案件を使えない。

-- ============================================================
-- projects: 作成者・論理削除の列
-- ============================================================
alter table public.projects
  add column owner_id uuid references public.profiles (id) on delete cascade,
  add column deleted_at timestamptz,
  -- 論理削除できるのは個人案件だけ (共有の案件は削除せず無効にする)
  add constraint projects_deleted_only_personal_check check (owner_id is not null or deleted_at is null);

comment on column public.projects.owner_id is '個人案件の作成者。null なら共有の案件 (admin が管理)。';
comment on column public.projects.deleted_at is '個人案件を削除した日時 (論理削除)。null なら削除されていない。';
comment on table public.projects is '案件。owner_id が null なら共有の案件 (編集は admin のみ)、あれば本人だけが使う個人案件。';

create index projects_owner_id_idx on public.projects (owner_id) where owner_id is not null;

-- 名前の一意性は共有の案件の中だけにする (個人案件の名前は重複してよい)。
-- 違反時の SQLSTATE は従来どおり 23505 (クライアントは「同じ名前の案件がある」と表示する)。
alter table public.projects drop constraint projects_name_key;
create unique index projects_shared_name_key on public.projects (name) where owner_id is null;

-- ============================================================
-- projects の権限: 列単位にする
--   - insert: name と owner_id だけ (deleted_at・is_active 等は既定値。個人案件を削除済みの状態で作らせない)
--   - update: name と is_active だけ (owner_id を付け替えて共有の案件と個人案件を入れ替えさせない。
--     deleted_at は delete_personal_project() だけが変更する)
-- ============================================================
revoke insert, update on public.projects from authenticated;
grant insert (name, owner_id) on public.projects to authenticated;
grant update (name, is_active) on public.projects to authenticated;

drop policy "projects_select_all" on public.projects;
drop policy "projects_admin_insert" on public.projects;
drop policy "projects_admin_update" on public.projects;
drop policy "projects_admin_delete" on public.projects;

-- 共有の案件は全員、個人案件は本人と admin が参照できる
create policy "projects_select_shared_own_or_admin"
  on public.projects
  for select
  to authenticated
  using (owner_id is null or owner_id = auth.uid() or public.is_admin());

-- 共有の案件は admin が、個人案件は本人が (自分を作成者として) 追加できる
create policy "projects_insert_shared_by_admin_or_own"
  on public.projects
  for insert
  to authenticated
  with check ((owner_id is null and public.is_admin()) or owner_id = auth.uid());

-- 変更・削除 (物理削除) は共有の案件だけ、admin のみ。個人案件の削除は delete_personal_project() で行う
create policy "projects_admin_update_shared"
  on public.projects
  for update
  to authenticated
  using (owner_id is null and public.is_admin())
  with check (owner_id is null and public.is_admin());

create policy "projects_admin_delete_shared"
  on public.projects
  for delete
  to authenticated
  using (owner_id is null and public.is_admin());

-- ============================================================
-- user_projects: 担当の割当に使えるのは共有の案件だけ
-- ============================================================
drop policy "user_projects_admin_insert" on public.user_projects;
drop policy "user_projects_admin_update" on public.user_projects;

create policy "user_projects_admin_insert"
  on public.user_projects
  for insert
  to authenticated
  with check (
    public.is_admin()
    and exists (select 1 from public.projects as p where p.id = user_projects.project_id and p.owner_id is null)
  );

create policy "user_projects_admin_update"
  on public.user_projects
  for update
  to authenticated
  using (public.is_admin())
  with check (
    public.is_admin()
    and exists (select 1 from public.projects as p where p.id = user_projects.project_id and p.owner_id is null)
  );

-- ============================================================
-- guard_allocation_project(): 新しく配分できる案件に「本人の削除していない個人案件」を加える。
-- それ以外の振る舞い (upsert・付け替え・直接DB接続の扱い) は 20261002090000_allocations_assigned_project_guard.sql と同じ。
--
-- 個人案件の判定では案件の行を共有ロック (for share) する。delete_personal_project() は案件の行を排他ロックしてから
-- 当月以降の配分を確かめるため、「削除の確認を通過した後に、削除中の個人案件へ当月の配分が追加される」ことを防げる
-- (ロックを待った側は、相手のコミット後の行・配分を見て判定し直す)。
-- ============================================================
create or replace function public.guard_allocation_project()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
begin
  if current_setting('role', true) is distinct from 'authenticated' then
    return new;
  end if;

  if tg_op = 'UPDATE'
     and new.project_id is not distinct from old.project_id
     and new.work_record_id is not distinct from old.work_record_id then
    return new;
  end if;

  if tg_op = 'INSERT' and exists (
    select 1 from public.allocations as a
    where a.work_record_id = new.work_record_id and a.project_id = new.project_id
  ) then
    return new;
  end if;

  -- 本人に割り当てられた有効な共有の案件
  if exists (
    select 1
    from public.work_records as wr
    join public.user_projects as up on up.user_id = wr.user_id
    join public.projects as p on p.id = up.project_id
    where wr.id = new.work_record_id and up.project_id = new.project_id and p.is_active and p.owner_id is null
  ) then
    return new;
  end if;

  -- 本人の削除していない個人案件
  perform 1
  from public.projects as p
  join public.work_records as wr on wr.user_id = p.owner_id
  where p.id = new.project_id and wr.id = new.work_record_id and p.deleted_at is null
  for share of p;
  if found then
    return new;
  end if;

  raise exception 'project_not_assigned' using errcode = '42501';
end;
$$;

-- ============================================================
-- delete_personal_project(): ログイン中のユーザーが自分の個人案件を削除する (論理削除)。
--
-- - 本人の個人案件でなければ (共有の案件・他人の個人案件・存在しない案件) 'personal_project_not_found' (P0002)。
-- - 当月以降 (日本時間の今月1日以降) の日に工数が配分されていれば 'personal_project_in_use' (P0001) で削除しない。
--   先月までの配分は残し、集計で引き続き見られるようにする。
-- - 削除済みの個人案件を指定した場合は何もしない (同じ操作を繰り返しても同じ結果になる)。
-- - SECURITY DEFINER にしているのは、projects の deleted_at 列の更新権限を authenticated に与えないため
--   (削除の条件をこの関数でしか満たせないようにする)。
-- ============================================================
create function public.delete_personal_project(target_project_id uuid)
returns void
language plpgsql
security definer
set search_path = ''
as $$
declare
  project_owner_id uuid;
  project_deleted_at timestamptz;
begin
  select p.owner_id, p.deleted_at into project_owner_id, project_deleted_at
  from public.projects as p
  where p.id = target_project_id
  for update;

  if not found or project_owner_id is null or project_owner_id is distinct from auth.uid() then
    raise exception 'personal_project_not_found' using errcode = 'P0002';
  end if;

  if project_deleted_at is not null then
    return;
  end if;

  if exists (
    select 1
    from public.allocations as a
    join public.work_records as wr on wr.id = a.work_record_id
    where a.project_id = target_project_id
      and wr.work_date >= date_trunc('month', now() at time zone 'Asia/Tokyo')::date
  ) then
    raise exception 'personal_project_in_use';
  end if;

  update public.projects set deleted_at = now() where id = target_project_id;
end;
$$;

revoke execute on function public.delete_personal_project(uuid) from public, anon;
grant execute on function public.delete_personal_project(uuid) to authenticated;
