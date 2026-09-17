-- Kanuchi (鍛冶) 関数・トリガー定義

-- ============================================================
-- updated_at を自動更新する共通トリガー関数
-- ============================================================
create function public.set_updated_at()
returns trigger
language plpgsql
as $$
begin
  new.updated_at = now();
  return new;
end;
$$;

create trigger set_updated_at
  before update on public.profiles
  for each row execute function public.set_updated_at();

create trigger set_updated_at
  before update on public.projects
  for each row execute function public.set_updated_at();

create trigger set_updated_at
  before update on public.shift_settings
  for each row execute function public.set_updated_at();

create trigger set_updated_at
  before update on public.work_records
  for each row execute function public.set_updated_at();

create trigger set_updated_at
  before update on public.allocations
  for each row execute function public.set_updated_at();

-- ============================================================
-- is_admin(): 現在のログインユーザーがadminかどうかを判定するヘルパー。
-- SECURITY DEFINER にすることで、profiles テーブルの RLS ポリシー内から
-- 参照しても再帰 (infinite recursion) にならないようにする。
-- ============================================================
create function public.is_admin()
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'admin'
  );
$$;

-- ============================================================
-- handle_new_user(): マジックリンクでの初回サインイン時、auth.users への
-- INSERT に連動して profiles 行を自動作成する。display_name はメールアドレスの
-- ローカルパートを初期値として使い、後でユーザー自身が変更できるようにする。
-- SECURITY DEFINER のため、profiles の RLS (INSERTポリシー無し) を経由せず
-- テーブル所有者権限で実行される。
-- ============================================================
create function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  insert into public.profiles (id, email, display_name, role)
  values (
    new.id,
    new.email,
    coalesce(new.raw_user_meta_data ->> 'display_name', split_part(new.email, '@', 1)),
    'member'
  );
  return new;
end;
$$;

create trigger on_auth_user_created
  after insert on auth.users
  for each row execute function public.handle_new_user();

-- ============================================================
-- enforce_role_change_permission(): profiles.role の変更は admin のみ許可する。
-- RLS の WITH CHECK だけでは「変更前後の値の比較」ができないため、トリガーで防御する。
--
-- 注意: この判定は PostgREST 経由 (= Postgres の実行ロールが `authenticated`) の
-- 場合のみ適用する。SQL Editor や `service_role` キー、マイグレーション等の
-- 直接的なDB接続 (実行ロールが `postgres` 等) はこのチェックの対象外とする。
-- こうしないと、運用開始時に「最初のadminをSupabase管理画面から手動設定する」
-- という docs/requirements.md の初期セットアップ手順自体がブロックされてしまう
-- (誰もadminがいない状態では is_admin() が常に false になるため)。
-- ============================================================
create function public.enforce_role_change_permission()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
  if new.role is distinct from old.role
     and current_setting('role', true) = 'authenticated'
     and not public.is_admin() then
    raise exception 'Only admins can change user roles';
  end if;
  return new;
end;
$$;

create trigger enforce_role_change_permission
  before update on public.profiles
  for each row execute function public.enforce_role_change_permission();
