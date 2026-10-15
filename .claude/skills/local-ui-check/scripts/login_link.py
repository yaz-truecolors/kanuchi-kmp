#!/usr/bin/env python3
"""ローカル Supabase のテストユーザーでログインするための URL を出力する。

使い方: python3 -I login_link.py <email> [--admin] [--base http://127.0.0.1:8081/]

1. 招待リスト（public.invitations）に登録する（招待制のため。既に登録済みなら何もしない）
2. マジックリンクを要求する（初回はここでアカウントが作られる）
3. --admin 指定時は profiles.role を admin にする
4. Mailpit（共有）から自分宛てのメールだけを探し、リンクを開いて得たフラグメント（#access_token=...）を
   --base に付けた URL を出力する。この URL をブラウザで開くとログイン済みになる（Auth の flowType は IMPLICIT）。

Mailpit・DB は worktree 間で共有されるため、メールの全件削除や他人のデータの変更はしない。
"""

import argparse
import json
import re
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request

API_URL = "http://127.0.0.1:54321"
MAILPIT_URL = "http://127.0.0.1:54324"
DB_CONTAINER = "supabase_db_kanuchi"  # supabase/config.toml の project_id から決まる
EMAIL_PATTERN = re.compile(r"^[a-z0-9._+-]+@[a-z0-9.-]+\.[a-z]+$")


def psql(sql: str) -> None:
    subprocess.run(
        ["docker", "exec", "-i", DB_CONTAINER, "psql", "-U", "postgres", "-d", "postgres", "-v", "ON_ERROR_STOP=1", "-q"],
        input=sql,
        text=True,
        check=True,
    )


def publishable_key() -> str:
    out = subprocess.run(["supabase", "status", "-o", "env"], capture_output=True, text=True, check=True).stdout
    m = re.search(r'^PUBLISHABLE_KEY="?([^"\n]+)"?$', out, re.M)
    if not m:
        sys.exit("supabase status から PUBLISHABLE_KEY を取得できません（supabase start 済みか確認してください）。")
    return m.group(1)


def get_json(url: str) -> dict:
    with urllib.request.urlopen(url) as res:
        return json.load(res)


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, *args, **kwargs):
        return None


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("email")
    parser.add_argument("--admin", action="store_true", help="role を admin にする")
    parser.add_argument("--base", default="http://127.0.0.1:8081/", help="アプリの URL")
    args = parser.parse_args()

    email = args.email.strip().lower()
    if not EMAIL_PATTERN.match(email):
        sys.exit(f"メールアドレスの形式が不正です: {email}")

    psql(f"insert into public.invitations (email) values ('{email}') on conflict do nothing;")

    requested_at = time.time()
    req = urllib.request.Request(
        f"{API_URL}/auth/v1/otp",
        data=json.dumps({"email": email, "create_user": True}).encode(),
        headers={"apikey": publishable_key(), "Content-Type": "application/json"},
    )
    try:
        urllib.request.urlopen(req).close()
    except urllib.error.HTTPError as e:
        sys.exit(f"マジックリンクの要求に失敗しました: {e.code} {e.read().decode(errors='ignore')}")

    if args.admin:
        psql(f"update public.profiles set role = 'admin' where id = (select id from auth.users where email = '{email}');")

    query = urllib.parse.quote(f"to:{email}")
    for _ in range(40):
        time.sleep(0.5)
        messages = get_json(f"{MAILPIT_URL}/api/v1/search?query={query}").get("messages") or []
        # 自分が要求した後に届いたメールだけを使う（同じアドレスの古いメールを拾わない）
        fresh = [m for m in messages if _created_at(m["Created"]) >= requested_at - 2]
        if not fresh:
            continue
        msg = get_json(f"{MAILPIT_URL}/api/v1/message/{fresh[0]['ID']}")
        m = re.search(r"https?://\S+/auth/v1/verify\?[^\s\"'<>]+", msg.get("Text") or "") or re.search(
            r"href=\"([^\"]*/auth/v1/verify\?[^\"]*)\"", msg.get("HTML") or ""
        )
        link = (m.group(1) if m.lastindex else m.group(0)).replace("&amp;", "&")
        try:
            urllib.request.build_opener(NoRedirect).open(link)
            sys.exit("リンクを開いてもリダイレクトされませんでした。")
        except urllib.error.HTTPError as e:
            location = e.headers.get("Location") or ""
        fragment = location.partition("#")[2]
        params = urllib.parse.parse_qs(fragment)
        # 利用停止中のユーザー等は #error_code=user_banned のようなエラーで戻るため、トークンがそろっている場合だけ成功とする
        if not (params.get("access_token") and params.get("refresh_token")):
            reason = (params.get("error_code") or params.get("error") or ["トークンがありません"])[0]
            sys.exit(f"ログインに失敗しました: {reason}")
        print(args.base + "#" + fragment)
        return
    sys.exit("マジックリンクのメールが届きませんでした（Mailpit: http://127.0.0.1:54324/）。")


def _created_at(value: str) -> float:
    # Mailpit は RFC3339（例: 2026-10-15T08:00:00.123+09:00 / ...Z）で返す
    from datetime import datetime

    return datetime.fromisoformat(value.replace("Z", "+00:00")).timestamp()


if __name__ == "__main__":
    main()
