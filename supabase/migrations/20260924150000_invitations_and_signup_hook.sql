-- Kanuchi (鍛冶) 招待リスト (invitations) と Before User Created Hook
--
-- 方針 (docs/requirements.md 5節):
--   アカウント作成は招待制。admin が invitations にメールアドレスを登録し、
--   招待された本人がログイン画面からマジックリンクを要求した時点でアカウントが作成される。
--   招待リストに無いメールアドレスでのアカウント作成は、Supabase Auth の
--   Before User Created Hook (hook_before_user_created) で拒否する。
--
-- 注意: Hook の有効化 (Auth がこの関数を呼び出すようにする設定) はマイグレーションでは行えず、
-- Supabase ダッシュボードでの手動設定が必要。手順は supabase/README.md を参照。

-- ============================================================
-- invitations: admin が招待したメールアドレスの一覧 (招待リスト)。
-- メールアドレスは小文字・前後空白なしに正規化した値のみ保存する
-- (Hook 側の照合も同じ正規化で行うため、表記ゆれで照合漏れしないようにする)。
-- invited_by は招待したadmin。アプリ (PostgREST、JWTあり) から登録した場合のみ auth.uid() で
-- 自動設定される。Supabase Studio の Table Editor 等の直接DB接続から登録した場合は
-- JWT が無く auth.uid() が NULL になるため、invited_by も NULL (= Studioから登録) になる。
-- ============================================================
create table public.invitations (
  email text primary key,
  invited_by uuid default auth.uid() references public.profiles (id) on delete set null,
  created_at timestamptz not null default now(),
  constraint invitations_email_normalized_check check (email = lower(btrim(email))),
  constraint invitations_email_format_check check (email ~ '^[^@[:space:]]+@[^@[:space:]]+$')
);

comment on table public.invitations is '招待リスト。ここに登録されたメールアドレスのみアカウントを作成できる。管理はadminのみ。';

-- Supabase Studio の Table Editor 等から大文字混じりで登録されても照合できるよう、
-- 保存前に正規化する (上記 check 制約は、このトリガーを経由しない不正な値に対する保険)。
create function public.normalize_invitation_email()
returns trigger
language plpgsql
set search_path = ''
as $$
begin
  new.email = lower(btrim(new.email));
  return new;
end;
$$;

create trigger normalize_invitation_email
  before insert or update of email on public.invitations
  for each row execute function public.normalize_invitation_email();

alter table public.invitations enable row level security;

grant select, delete on public.invitations to authenticated;
-- INSERT は email 列のみに限定する。アプリ経由の登録では invited_by が default (auth.uid()) で
-- 自動設定され、クライアントから他人のIDを指定して偽装することはできない。
grant insert (email) on public.invitations to authenticated;

create policy "invitations_admin_select"
  on public.invitations
  for select
  to authenticated
  using (public.is_admin());

create policy "invitations_admin_insert"
  on public.invitations
  for insert
  to authenticated
  with check (public.is_admin());

create policy "invitations_admin_delete"
  on public.invitations
  for delete
  to authenticated
  using (public.is_admin());

-- ============================================================
-- hook_before_user_created(): Supabase Auth の Before User Created Hook。
-- ユーザー作成の直前に Auth (実行ロール supabase_auth_admin) から呼び出され、
-- 招待リストに無いメールアドレスであればエラーを返してアカウント作成を拒否する。
--
-- - SECURITY DEFINER にすることで、invitations の RLS を経由せずテーブル所有者権限で照合する
--   (supabase_auth_admin 用の RLS ポリシーを別途追加せずに済む)。
-- - 拒否時の message は、クライアント (SupabaseAuthRepository) が「未招待」と判定するための
--   固定の識別子。変更する場合はクライアント側の定数も合わせて変更すること。
-- - Supabase Auth は error.message をそのままクライアントへ返すため、内部情報は含めない。
-- ============================================================
create function public.hook_before_user_created(event jsonb)
returns jsonb
language plpgsql
security definer
set search_path = ''
stable
as $$
declare
  new_email text := lower(btrim(event -> 'user' ->> 'email'));
begin
  if new_email is not null
     and exists (select 1 from public.invitations where email = new_email) then
    return '{}'::jsonb;
  end if;

  return jsonb_build_object(
    'error', jsonb_build_object(
      'http_code', 403,
      'message', 'email_not_invited'
    )
  );
end;
$$;

-- Hook は Supabase Auth (supabase_auth_admin) からのみ実行できるようにし、
-- Data API (PostgREST の rpc) 経由で anon / authenticated から呼び出されないようにする。
grant usage on schema public to supabase_auth_admin;
grant execute on function public.hook_before_user_created(jsonb) to supabase_auth_admin;
revoke execute on function public.hook_before_user_created(jsonb) from public, anon, authenticated;
