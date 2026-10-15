---
name: local-ui-check
description: ローカル Supabase（Docker）に接続した本番ビルドを起動し、テストユーザー（member / admin）でログインした状態の画面を実ブラウザで操作・スクリーンショット確認する手順。UI に関わる変更をした後の動作確認、「画面を確認して」「ブラウザで動かして」「スクショを撮って」と言われたときに使う。
---

# ローカル Supabase を使った画面確認

`copilot-construction.md`「4. テスト・検証（共通）」で求めている「変更した画面・操作を実ブラウザで確認する」ための手順です。
本番の Supabase には接続せず、`supabase start` で起動したローカルの Supabase（DB・Auth・Mailpit）を使います。
スクリプトは `.claude/skills/local-ui-check/scripts/` にあり、リポジトリのルートで実行します。

## 前提

- JDK と Chrome（`CHROME_BIN`）は `.claude/settings.local.json` の `env` で設定しておく（各自の環境のパス。コミットしない）。
- Docker（Docker Desktop）と Supabase CLI が必要。

## 手順

1. **Docker を起動する**（起動済みなら不要）。

   ```sh
   docker info >/dev/null 2>&1 || { open -a Docker; for i in $(seq 1 60); do docker info >/dev/null 2>&1 && break; sleep 2; done; }
   ```

2. **ローカル Supabase を起動する**（マイグレーションが適用される）。出力にはローカル用の鍵が含まれるので表示しない。

   ```sh
   supabase start >/dev/null && echo started
   ```

   - コンテナは `supabase/config.toml` の `project_id` 単位で worktree 間共有される。他のセッションが使っていることがあるため、
     **作業後に `supabase stop` しない**（止める場合はユーザーに確認する）。`supabase db reset` も同様に共有データを消すので確認する。
   - マイグレーションを追加・変更した直後は `supabase migration up` で適用する。

3. **ローカル Supabase 向けにビルドする**（`SupabaseConfig.kt` をビルドの間だけ書き換え、終了時に必ず元に戻す）。

   ```sh
   .claude/skills/local-ui-check/scripts/build-local-dist.sh
   ```

   - 出力先は `app-wasmjs/build/local-supabase-dist`。終わったら `git status` で `SupabaseConfig.kt` に差分が無いことを確認する
     （**ローカル値の `SupabaseConfig.kt` を絶対にコミットしない**）。
   - コードを変更したら、このステップからやり直す。

4. **配信する**（Bash の `run_in_background` で起動する）。

   ```sh
   .claude/skills/local-ui-check/scripts/serve-local-dist.sh
   ```

   - `http://127.0.0.1:8081/` で配信される（ローカル Auth の Redirect URLs に登録されているポート）。使用中なら `PORT=8082` 等を指定し、
     次の手順の `--base` もあわせる。`./gradlew :app-wasmjs:serveDistribution` は本番の設定で再ビルドするので、ここでは使わない。

5. **ログイン用の URL を作る**（招待登録 → マジックリンク要求 → Mailpit からリンクを取得、までを自動で行う）。

   ```sh
   python3 -I .claude/skills/local-ui-check/scripts/login_link.py <テスト用アドレス> [--admin] [--base http://127.0.0.1:8081/]
   ```

   - 出力された URL（`#access_token=...` 付き）をブラウザで開くと、そのユーザーでログインした状態になる。
   - テスト用アドレスは `<用途>@example.com` のように、このセッション用のものを使う（他のセッションと重ならないように）。
     管理者メニューを確認するときは `--admin` を付ける。
   - Mailpit（`http://127.0.0.1:54324/`）は共有なので、メールの全件削除はしない。

6. **ブラウザで確認する**。Claude Code のアプリ内ブラウザ（`mcp__Claude_Browser__*`）で URL を開き、スクリーンショットで確認する。
   - Compose for Web は `<canvas>` に描画するため、DOM（`read_page`・`find`）では画面の要素を取れない。スクリーンショットを見て
     座標でクリック・キー入力する（`presentation.instructions.md`「ブラウザ実行時の注意点（wasmJs）」）。
   - 初回表示はフォント（Noto Sans JP）の読み込みで文字が出るまで数秒かかる。文字が無い画面が写ったら数秒待って撮り直す。
   - 確認結果はスクリーンショットとあわせてユーザーに報告する。必要なら PR 本文の「確認方法」に書く。

7. **後片付け**: 配信サーバー（手順4のバックグラウンドプロセス）を止める。Supabase のコンテナは止めない（手順2）。
