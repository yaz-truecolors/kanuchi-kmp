#!/usr/bin/env python3
"""PR にレビュー（概要コメント＋行コメント）を1件のレビューとして投稿する。

使い方（リポジトリのルートで実行）:
    python3 -I .claude/skills/pr-review/scripts/post_review.py --dry-run <review.json>
    python3 -I .claude/skills/pr-review/scripts/post_review.py <review.json>

<review.json> に `-` を渡すと標準入力から読む（ファイルを書かずにヒアドキュメントで渡せる）。

review.json の形式:
    {
      "pr": 53,
      "commit_id": "<レビューしたコミットの SHA>",
      "body": "<概要コメント（Markdown）>",
      "comments": [
        {"path": "data/src/.../Foo.kt", "line": 42, "body": "..."},
        {"path": "...", "start_line": 10, "line": 14, "side": "RIGHT", "body": "..."}
      ]
    }

- commit_id は必須。PR の最新コミットと違う（レビュー中に push された）場合は投稿しない。
- 差分の外の行を指す行コメントは GitHub が受け付けない（422）ため、概要コメントの末尾
  「差分外の指摘」に移して投稿する。
- event は常に COMMENT（自分の PR には REQUEST_CHANGES / APPROVE を付けられないため）。
"""

import argparse
import json
import re
import subprocess
import sys

HUNK_HEADER = re.compile(r"^@@ -(\d+)(?:,\d+)? \+(\d+)(?:,\d+)? @@")


def gh(*args, stdin=None):
    result = subprocess.run(
        ["gh", *args], input=stdin, capture_output=True, text=True, check=False
    )
    if result.returncode != 0:
        sys.exit(f"gh {' '.join(args[:3])} ... が失敗しました:\n{result.stderr}")
    return result.stdout


def commentable_lines(diff):
    """差分から、行コメントを付けられる行番号を path ごと・side ごとに集める。"""
    lines = {}
    path = old_path = None
    left = right = 0
    in_hunk = False
    for raw in diff.splitlines():
        if raw.startswith("diff --git "):
            path = old_path = None
            in_hunk = False
            continue
        if not in_hunk and raw.startswith("--- "):
            source = raw[4:]
            old_path = source[2:] if source.startswith("a/") else None
            continue
        if not in_hunk and raw.startswith("+++ "):
            target = raw[4:]
            # 削除したファイルは +++ が /dev/null になるので、削除前のパスで集める
            path = target[2:] if target.startswith("b/") else old_path
            if path is not None:
                lines.setdefault(path, {"LEFT": set(), "RIGHT": set()})
            continue
        match = HUNK_HEADER.match(raw)
        if match:
            left, right = int(match.group(1)), int(match.group(2))
            in_hunk = path is not None
            continue
        if not in_hunk or raw.startswith("\\"):
            continue
        if raw.startswith("+"):
            lines[path]["RIGHT"].add(right)
            right += 1
        elif raw.startswith("-"):
            lines[path]["LEFT"].add(left)
            left += 1
        else:
            lines[path]["LEFT"].add(left)
            lines[path]["RIGHT"].add(right)
            left += 1
            right += 1
    return lines


def is_commentable(comment, lines):
    side = comment.get("side", "RIGHT")
    available = lines.get(comment["path"], {}).get(side, set())
    start = comment.get("start_line", comment["line"])
    if start > comment["line"]:
        return False
    return all(n in available for n in range(start, comment["line"] + 1))


def to_api_comment(comment):
    side = comment.get("side", "RIGHT")
    api = {"path": comment["path"], "line": comment["line"], "side": side, "body": comment["body"]}
    if "start_line" in comment and comment["start_line"] != comment["line"]:
        api["start_line"] = comment["start_line"]
        api["start_side"] = side
    return api


def outside_section(comments):
    parts = ["", "### 差分外の指摘", ""]
    for c in comments:
        start = c.get("start_line")
        where = f"{c['path']}:{start}-{c['line']}" if start else f"{c['path']}:{c['line']}"
        parts.append(f"#### `{where}`")
        parts.append("")
        parts.append(c["body"])
        parts.append("")
    return "\n".join(parts)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="投稿せずに送信内容を表示する")
    parser.add_argument("review_json")
    args = parser.parse_args()

    if args.review_json == "-":
        review = json.load(sys.stdin)
    else:
        with open(args.review_json, encoding="utf-8") as f:
            review = json.load(f)
    pr = str(review["pr"])

    repo = gh("repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner").strip()
    head = gh("pr", "view", pr, "--json", "headRefOid", "-q", ".headRefOid").strip()
    commit_id = review.get("commit_id")
    if not commit_id:
        sys.exit("commit_id（レビューしたコミットの SHA）を指定してください。")
    if commit_id != head:
        sys.exit(
            f"レビューしたコミット {commit_id[:7]} が PR の最新 {head[:7]} と違います。"
            "最新のコミットでレビューし直してください。"
        )

    lines = commentable_lines(gh("pr", "diff", pr))
    inline = [c for c in review.get("comments", []) if is_commentable(c, lines)]
    outside = [c for c in review.get("comments", []) if not is_commentable(c, lines)]

    body = review["body"]
    if outside:
        body += "\n" + outside_section(outside)
    payload = {
        "commit_id": commit_id,
        "event": "COMMENT",
        "body": body,
        "comments": [to_api_comment(c) for c in inline],
    }

    print(f"PR #{pr}（{repo}）: 行コメント {len(inline)} 件、差分外として概要に移した指摘 {len(outside)} 件")
    for c in outside:
        print(f"  差分外: {c['path']}:{c.get('start_line', c['line'])}-{c['line']}")
    if args.dry_run:
        print(json.dumps(payload, ensure_ascii=False, indent=2))
        return

    response = gh(
        "api", "--method", "POST", f"repos/{repo}/pulls/{pr}/reviews", "--input", "-",
        stdin=json.dumps(payload),
    )
    print(f"投稿しました: {json.loads(response)['html_url']}")


if __name__ == "__main__":
    main()
