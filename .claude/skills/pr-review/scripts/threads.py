#!/usr/bin/env python3
"""PR のレビュースレッドの一覧・返信・Resolve を行う。

使い方（リポジトリのルートで実行）:
    python3 -I .claude/skills/pr-review/scripts/threads.py list <PR番号>
    python3 -I .claude/skills/pr-review/scripts/threads.py reply <thread id> <返信本文>
    python3 -I .claude/skills/pr-review/scripts/threads.py resolve <thread id>

- 実行する GraphQL はこの3つに固定している。skill の allowed-tools で `gh api graphql` そのものを許可すると、
  返信・Resolve 以外の mutation（Ready 化・マージ等）まで確認なしで実行できてしまうため、このスクリプト経由にする。
- list は JSON（スレッドごとの id・isResolved・path・line・コメントの author と body）を出力する。
"""

import argparse
import json
import subprocess
import sys

LIST_QUERY = (
    "query($o:String!,$r:String!,$n:Int!){repository(owner:$o,name:$r){pullRequest(number:$n){"
    "reviewThreads(first:100){nodes{id isResolved path line comments(first:50){nodes{author{login} body}}}}}}}"
)
REPLY_MUTATION = (
    "mutation($t:ID!,$b:String!){addPullRequestReviewThreadReply("
    "input:{pullRequestReviewThreadId:$t,body:$b}){comment{url}}}"
)
RESOLVE_MUTATION = "mutation($t:ID!){resolveReviewThread(input:{threadId:$t}){thread{isResolved}}}"


def gh(*args):
    result = subprocess.run(["gh", *args], capture_output=True, text=True, check=False)
    if result.returncode != 0:
        sys.exit(f"gh {' '.join(args[:3])} ... が失敗しました:\n{result.stderr}")
    return result.stdout


def graphql(query, **variables):
    args = ["api", "graphql", "-f", f"query={query}"]
    for name, value in variables.items():
        flag = "-F" if isinstance(value, int) else "-f"
        args += [flag, f"{name}={value}"]
    return json.loads(gh(*args))["data"]


def main():
    parser = argparse.ArgumentParser()
    sub = parser.add_subparsers(dest="command", required=True)
    list_parser = sub.add_parser("list")
    list_parser.add_argument("pr", type=int)
    reply_parser = sub.add_parser("reply")
    reply_parser.add_argument("thread_id")
    reply_parser.add_argument("body")
    resolve_parser = sub.add_parser("resolve")
    resolve_parser.add_argument("thread_id")
    args = parser.parse_args()

    if args.command == "list":
        owner, name = gh("repo", "view", "--json", "nameWithOwner", "-q", ".nameWithOwner").strip().split("/")
        data = graphql(LIST_QUERY, o=owner, r=name, n=args.pr)
        threads = data["repository"]["pullRequest"]["reviewThreads"]["nodes"]
        print(json.dumps(threads, ensure_ascii=False, indent=2))
    elif args.command == "reply":
        data = graphql(REPLY_MUTATION, t=args.thread_id, b=args.body)
        print(f"返信しました: {data['addPullRequestReviewThreadReply']['comment']['url']}")
    else:
        data = graphql(RESOLVE_MUTATION, t=args.thread_id)
        print(f"Resolve しました: isResolved={data['resolveReviewThread']['thread']['isResolved']}")


if __name__ == "__main__":
    main()
