-- Kanuchi (鍛冶) Supabase の既定権限で付与されている余分なテーブル権限の剥奪
--
-- 背景:
--   Supabase の既定権限 (ALTER DEFAULT PRIVILEGES。postgres ロールが public スキーマに作るオブジェクトが対象) により、
--   public スキーマのテーブルには anon / authenticated へ TRUNCATE・REFERENCES・TRIGGER・MAINTAIN が、
--   シーケンスには anon へ UPDATE が自動で付与されている。TRUNCATE は RLS を迂回するうえ、
--   「anon には一切のテーブル権限を付与しない」という方針 (supabase/README.md) とも食い違うため剥奪する。
--   Data API (PostgREST) からはこれらの操作を実行できないため、アプリの動作は変わらない。
--
-- 方針:
--   - anon: public スキーマのテーブル・ビュー・シーケンス等の権限をすべて剥奪する。
--   - authenticated: アプリが使う SELECT / INSERT / UPDATE / DELETE (列単位の grant を含む) は残し、
--     TRUNCATE・REFERENCES・TRIGGER・MAINTAIN だけを剥奪する。
--     (REVOKE ALL はテーブルに対する列単位の権限も剥奪してしまうため使わない)
--     シーケンスの権限は、将来 serial 列等を追加したときの INSERT (nextval) に必要になり得るため変更しない。
--   - 既存のオブジェクトに加えて、今後 postgres ロールが public スキーマに作るオブジェクトにも
--     同じ権限が付かないよう、postgres ロールの既定権限を調整する。
--   - service_role・関数の EXECUTE 権限・public スキーマの USAGE 権限は対象外 (変更しない)。
--
-- 本番 (supabase db push) で失敗しないための注意:
--   - マイグレーションは postgres ロール (スーパーユーザーではない) で実行される。
--     supabase_admin の既定権限 (ALTER DEFAULT PRIVILEGES FOR ROLE supabase_admin) は postgres からは
--     変更できず permission denied で失敗するため、postgres ロールの既定権限だけを調整する。
--   - 権限の剥奪は、実行ロールが所有者 (またはそのメンバー) であるオブジェクトだけを対象にする
--     (所有していないオブジェクトに権限を一切持っていない場合、REVOKE がエラーになるため)。
--   - MAINTAIN 権限は PostgreSQL 17 以降にしか存在しない (16 以前では構文エラー) ため、
--     サーバーのバージョンを判定して 17 以降の場合のみ剥奪する。
--   - いずれの文も、既に剥奪済みであれば何もしないため、何度適用しても同じ結果になる (冪等)。

do $$
declare
  -- authenticated から剥奪する権限。MAINTAIN は PostgreSQL 17 以降のみ存在する。
  authenticated_revoked_privileges text :=
    case
      when current_setting('server_version_num')::int >= 170000 then 'truncate, references, trigger, maintain'
      else 'truncate, references, trigger'
    end;
  rel record;
begin
  -- ============================================================
  -- 既存のオブジェクト
  -- ============================================================
  for rel in
    select c.oid::regclass as name, c.relkind
    from pg_class as c
    where c.relnamespace = 'public'::regnamespace
      -- r: テーブル, p: パーティションテーブル, v: ビュー, m: マテリアライズドビュー, f: 外部テーブル, S: シーケンス
      and c.relkind in ('r', 'p', 'v', 'm', 'f', 'S')
      and pg_has_role(current_user, c.relowner, 'USAGE')
  loop
    if rel.relkind = 'S' then
      execute format('revoke all on sequence %s from anon', rel.name);
    else
      execute format('revoke all on table %s from anon', rel.name);
      execute format('revoke %s on table %s from authenticated', authenticated_revoked_privileges, rel.name);
    end if;
  end loop;

  -- ============================================================
  -- 今後 postgres ロールが public スキーマに作るオブジェクトの既定権限
  -- ============================================================
  alter default privileges for role postgres in schema public revoke all on tables from anon;
  alter default privileges for role postgres in schema public revoke all on sequences from anon;
  execute format(
    'alter default privileges for role postgres in schema public revoke %s on tables from authenticated',
    authenticated_revoked_privileges
  );
end;
$$;
