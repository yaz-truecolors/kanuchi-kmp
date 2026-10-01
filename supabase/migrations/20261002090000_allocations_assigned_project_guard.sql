-- Kanuchi (鍛冶) 工数配分の案件の制限
--
-- 工数を新しく配分できるのは、日次実績の本人に割り当てられた有効な案件だけにする (docs/requirements.md の「日次入力」)。
-- その日に既に配分がある案件は、担当を外された・無効にされた後も変更・削除できる (0 に直す等のため)。
-- 画面 (日次入力) でも同じ案件だけを入力できるようにしているが、改変したクライアントからの配分を防ぐため DB で強制する。

-- ============================================================
-- guard_allocation_project(): allocations の追加と、案件・日次実績の付け替えを、本人に割り当てられた有効な案件に限る。
--
-- - 追加 (insert) は、同じ日次実績・同じ案件の行が既にあれば許可する。upsert (insert ... on conflict do update) は
--   既存の行の更新になる場合も BEFORE INSERT トリガーが発火するため、既存の配分の工数の変更を拒否しないようにする。
-- - 変更 (update) は、案件か日次実績が変わる場合だけ判定する (upsert は値が同じでも全列を set する)。
-- - 判定は日次実績の本人 (work_records.user_id) の割当で行う。本人以外の日次実績への追加・付け替えは RLS が拒否する。
--   SECURITY DEFINER にしているのは、RLS で見えない行 (他人の日次実績) でもトリガーの判定が RLS の結果に
--   左右されないようにするため (拒否の理由を RLS とこのトリガーで混同しない)。
-- - enforce_role_change_permission と同じく、実行ロールが authenticated の場合 (Data API 経由) だけ判定する。
--   直接DB接続 (SQL Editor・マイグレーション等) によるデータの修正は対象外にする。
-- ============================================================
create function public.guard_allocation_project()
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

  if not exists (
    select 1
    from public.work_records as wr
    join public.user_projects as up on up.user_id = wr.user_id
    join public.projects as p on p.id = up.project_id
    where wr.id = new.work_record_id and up.project_id = new.project_id and p.is_active
  ) then
    raise exception 'project_not_assigned' using errcode = '42501';
  end if;

  return new;
end;
$$;

-- トリガー関数は直接呼び出せない (呼び出すとエラーになる) が、Data API の rpc の対象から外すため EXECUTE を剥奪しておく。
revoke execute on function public.guard_allocation_project() from public, anon, authenticated;

create trigger guard_allocation_project
  before insert or update of project_id, work_record_id on public.allocations
  for each row execute function public.guard_allocation_project();
