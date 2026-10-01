-- Kanuchi (鍛冶) 利用停止 (除名)・復帰と、admin がいなくなる変更の禁止
--
-- 方針 (docs/requirements.md 5節):
--   - admin はユーザーを利用停止・復帰できる。利用停止したユーザーはログインできない。
--     アカウント (profiles) と過去の稼働記録は削除しない (profiles を削除すると work_records 等が cascade で消えるため)。
--   - 自分自身の利用停止・降格と、利用中の admin が1人もいなくなる利用停止・降格はできない。
--
-- 仕組み:
--   1. profiles.suspended_at (NULL = 利用中) を利用停止の状態の正とする。アプリからは set_user_suspended() でのみ変更できる
--      (authenticated には suspended_at 列の UPDATE 権限を付与しない)。
--   2. set_user_suspended() は、あわせて auth.users.banned_until を設定・解除し、利用停止時は auth.sessions を削除する。
--      - banned_until が未来の日時のユーザーは、Supabase Auth がログイン (マジックリンクの検証)・トークンの更新を拒否する。
--        マジックリンクのメール自体は送信されるが、リンクを開くと `#error_code=user_banned` でアプリに戻る。
--      - セッションの削除で、ログイン中の端末のリフレッシュトークンも無効になる (次のトークン更新でログアウトされる)。
--   3. 発行済みのアクセストークン (最長1時間有効) での Data API へのアクセスは、PostgREST の pre-request 関数
--      (reject_suspended_user) で拒否する。また is_admin() は利用停止中の admin を admin として扱わない。
--   4. 自分自身の利用停止・降格と、利用中の admin が0人になる変更は、profiles のトリガー (guard_active_admins) で拒否する。
--      2人の admin が互いを同時に降格・利用停止しても0人にならないよう、トリガー内で advisory lock を取って直列化する。
--
-- いずれの制限も、enforce_role_change_permission と同じく PostgREST 経由 (実行ロール authenticated) の場合のみ適用する。
-- SQL Editor 等の直接DB接続 (実行ロール postgres) は、初期セットアップや誤操作からの復旧のため制限の対象外とする。

-- ============================================================
-- profiles.suspended_at: 利用停止した日時。NULL なら利用中。
-- ============================================================
alter table public.profiles add column suspended_at timestamptz;

comment on column public.profiles.suspended_at is
  '利用停止した日時 (NULL = 利用中)。変更は set_user_suspended() でのみ行う (authenticated に UPDATE 権限なし)。';

-- ============================================================
-- is_admin(): 利用停止中の admin は admin として扱わない。
-- (pre-request 関数で利用停止中のユーザーの Data API へのアクセスは拒否しているが、
--  RLS・関数の判定でも利用停止中の admin に管理者権限を与えないようにする)
-- ============================================================
create or replace function public.is_admin()
returns boolean
language sql
security definer
set search_path = public
stable
as $$
  select exists (
    select 1 from public.profiles
    where id = auth.uid() and role = 'admin' and suspended_at is null
  );
$$;

-- ============================================================
-- guard_active_admins(): 自分自身の利用停止・降格と、利用中の admin が0人になる変更を拒否する。
--
-- 利用中の admin (role = 'admin' かつ suspended_at が NULL) が、降格または利用停止で利用中の admin でなくなる場合に、
--   1. 他に利用中の admin がいなければ拒否する (last_active_admin_required)
--   2. 対象が自分自身なら拒否する (cannot_demote_self / cannot_suspend_self)
-- 自分自身の利用停止は、対象が admin かどうかに関わらず拒否する。
--
-- 同時実行の対策: 2人の admin が互いを同時に降格すると、それぞれの「他に利用中の admin がいるか」の確認が
-- 相手の変更 (未コミット) を見ずに成功し、admin が0人になり得る。そのため、確認の前に transaction-level の
-- advisory lock を取って直列化する。READ COMMITTED では、ロック取得後に実行するクエリは先にコミットされた変更を見る
-- (この関数は VOLATILE のため、クエリごとに新しいスナップショットを使う)。
-- ============================================================
create function public.guard_active_admins()
returns trigger
language plpgsql
security definer
set search_path = ''
as $$
declare
  is_suspending boolean := old.suspended_at is null and new.suspended_at is not null;
  is_demoting boolean := old.role = 'admin' and new.role is distinct from 'admin';
  loses_active_admin boolean := old.role = 'admin' and old.suspended_at is null and (is_suspending or is_demoting);
begin
  if current_setting('role', true) is distinct from 'authenticated' then
    return new;
  end if;

  if loses_active_admin then
    perform pg_advisory_xact_lock(hashtextextended('kanuchi:guard_active_admins', 0));
    if not exists (
      select 1 from public.profiles as p
      where p.id <> new.id and p.role = 'admin' and p.suspended_at is null
    ) then
      raise exception 'last_active_admin_required';
    end if;
  end if;

  if new.id = auth.uid() then
    if is_suspending then
      raise exception 'cannot_suspend_self';
    end if;
    if is_demoting then
      raise exception 'cannot_demote_self';
    end if;
  end if;

  return new;
end;
$$;

-- トリガー関数は直接呼び出せない (呼び出すとエラーになる) が、Data API の rpc の対象から外すため EXECUTE を剥奪しておく。
revoke execute on function public.guard_active_admins() from public, anon, authenticated;

-- BEFORE トリガーは名前の順に実行されるため、enforce_role_change_permission (admin 以外の role 変更の拒否) の後に実行される。
create trigger guard_active_admins
  before update of role, suspended_at on public.profiles
  for each row execute function public.guard_active_admins();

-- ============================================================
-- set_user_suspended(): ユーザーを利用停止・復帰させる (admin のみ)。
--
-- - 利用停止: profiles.suspended_at を設定し、auth.users.banned_until を遠い未来 (100年後) にして、
--   auth.sessions を削除する (ログイン中の端末のリフレッシュトークンを無効にする)。
--   banned_until に 'infinity' を使わないのは、Supabase Auth (Go) が日時として読み込めない可能性があるため
--   (Supabase Auth の Admin API の ban_duration も期間指定で、同じく有限の日時を設定する)。
-- - 復帰: profiles.suspended_at と auth.users.banned_until を NULL に戻す。
-- - 自分自身の利用停止・利用中の admin が0人になる利用停止は、guard_active_admins トリガーが拒否する。
-- - SECURITY DEFINER (所有者 postgres) で auth.users / auth.sessions を更新する。
--   Supabase のホスト環境でも postgres ロールは auth.users / auth.sessions の更新・削除の権限を持つ
--   (auth スキーマへの DDL は制限されているが、DML は許可されている)。
-- ============================================================
create function public.set_user_suspended(target_user_id uuid, suspended boolean)
returns void
language plpgsql
security definer
set search_path = ''
as $$
begin
  if current_setting('role', true) = 'authenticated' and not public.is_admin() then
    raise exception 'Only admins can suspend or reactivate users' using errcode = '42501';
  end if;

  if suspended is null then
    raise exception 'suspended must not be null' using errcode = '22004';
  end if;

  update public.profiles
  set suspended_at = case when suspended then coalesce(suspended_at, now()) end
  where id = target_user_id;

  if not found then
    raise exception 'user_not_found' using errcode = 'P0002';
  end if;

  update auth.users
  set banned_until = case when suspended then now() + interval '100 years' end
  where id = target_user_id;

  if suspended then
    delete from auth.sessions where user_id = target_user_id;
  end if;
end;
$$;

revoke execute on function public.set_user_suspended(uuid, boolean) from public, anon;
grant execute on function public.set_user_suspended(uuid, boolean) to authenticated;

-- ============================================================
-- reject_suspended_user(): PostgREST の pre-request 関数 (Data API へのリクエストごとに、ロールの切り替え後に呼ばれる)。
-- 利用停止中のユーザーのリクエストを拒否する (利用停止前に発行されたアクセストークンは最長1時間有効なため)。
--
-- - PostgREST は anon / authenticated に切り替えた後でこの関数を呼ぶため、両ロールに EXECUTE 権限が必要
--   (rpc として直接呼び出せるが、自分が利用停止中かどうかで例外になるだけで、情報は返さない)。
-- - 例外の SQLSTATE 42501 は、PostgREST が HTTP 403 に変換する。
-- - Data API (PostgREST) にのみ適用される (Realtime・Storage は使っていない)。
-- ============================================================
create function public.reject_suspended_user()
returns void
language plpgsql
security definer
set search_path = ''
stable
as $$
begin
  if exists (
    select 1 from public.profiles
    where id = auth.uid() and suspended_at is not null
  ) then
    raise exception 'user_suspended' using errcode = '42501';
  end if;
end;
$$;

revoke execute on function public.reject_suspended_user() from public;
grant execute on function public.reject_suspended_user() to anon, authenticated, service_role;

-- pre-request 関数を PostgREST に登録する (Supabase のドキュメント「Securing your API」の
-- 「Configure a pre-request function」の手順)。データベース単位の設定 (IN DATABASE) にして、
-- データベースを作り直した場合 (ローカルの supabase db reset 等) に、関数の無い設定だけが残らないようにする。
do $$
begin
  execute format(
    'alter role authenticator in database %I set pgrst.db_pre_request = %L',
    current_database(),
    'public.reject_suspended_user'
  );
end;
$$;

notify pgrst, 'reload config';
