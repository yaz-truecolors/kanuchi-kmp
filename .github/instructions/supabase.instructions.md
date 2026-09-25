---
applyTo: "supabase/**"
---

# Supabase マイグレーション・RLS・Hook（`supabase/`）の規約

マイグレーションSQL・RLSポリシー・トリガー・Auth Hook・`supabase/config.toml` を変更するときの
規約・ハマりどころです。
領域共通の原則は [copilot-construction.md](../../copilot-construction.md)、
クライアント側（supabase-kt・Repository 実装）の規約は [data.instructions.md](data.instructions.md) を参照してください。
RLS の権限方針（誰が何を参照・編集できるか）は `docs/requirements.md` の「5. 認証・権限設計」が正です。

## マイグレーション運用

- マイグレーションSQLは `supabase/migrations/` に配置し、ファイル名は
  `<タイムスタンプ>_<内容>.sql` の昇順で適用順を表す（Supabase CLI の規約に準拠）。
- `main` へのマージ時、GitHub Actions (`.github/workflows/supabase-deploy.yml`) が
  `supabase db push` を実行し、未適用のマイグレーションを自動的に本番へ反映する。
  手動でSupabase StudioのSQL Editorに貼り付ける必要はない（緊急時・CI障害時の
  フォールバックとしては引き続き可能）。
  このCIに必要な Secrets とトークンの有効期限の扱いは [ci.instructions.md](ci.instructions.md) を参照。
- `supabase/config.toml` はローカル開発 (`supabase start`) 用の設定。ダッシュボード側の
  Data API設定（`Automatically expose new tables` 等）と値を一致させておくこと
  （`auto_expose_new_tables = false` に設定済み）。
- 新しいテーブルを追加する際は、以下を必ずセットで行うこと（1つでも欠けると
  「テーブルはあるがAPI経由で一切アクセスできない」「RLSが無効なまま公開される」等の
  事故につながる）：
  1. `alter table ... enable row level security;`
  2. `grant select/insert/update/delete on ... to authenticated;`
     （`anon`には付与しない。本プロジェクトはマジックリンク認証必須のため）
  3. 用途に応じた `create policy ...`

## RLS・トリガー

- RLS（Row Level Security）前提の設計。クライアント（`data` 層）のクエリがRLSに違反しないことの確認は
  [data.instructions.md](data.instructions.md) を参照。
- `profiles.role` の変更を「adminのみ許可」にするトリガー（`enforce_role_change_permission`）は、
  **Postgresの実行ロールが `authenticated` の場合のみ**チェックするように実装すること。
  無条件でチェックすると、運用開始時に「最初のadminをSupabase管理画面から手動設定する」
  という初期セットアップ手順自体がブロックされてしまう（誰もadminがいない状態では
  `is_admin()` が常に false になるため、直接DB接続からの昇格も拒否されてしまう）。
  `current_setting('role', true) = 'authenticated'` で判定し、SQL Editor 等の直接DB接続
  （実行ロールが `postgres` 等）はチェック対象外にする。
- `profiles` テーブルのRLSポリシー内で「自分がadminか」を判定する際は、素朴に
  `exists (select 1 from profiles where id = auth.uid() and role = 'admin')` と書くと
  無限再帰（自分自身のテーブルのRLSを評価するために自分自身のRLSを再評価する）になり得る。
  `SECURITY DEFINER` な `is_admin()` 関数として切り出し、テーブル所有者権限で実行することで
  再帰を回避している。
- RLSポリシー・トリガーはSupabase実機に適用する前に、ローカルのDocker上のPostgresで
  検証すること。`auth.users` / `auth.uid()` / `auth.role()` を模した最小限のシムを用意し、
  `SET LOCAL ROLE authenticated; SET LOCAL "request.jwt.claim.sub" = '<uuid>';` で
  「特定ユーザーとしてログインした状態」を再現できる（PostgRESTが実際のリクエストごとに
  行っているセッション変数の設定を模している）。詳細は `supabase/README.md` を参照。

## 招待制アカウント作成（Before User Created Hook）

- **アカウント作成は招待制（招待リスト＋Before User Created Hook）。**
  adminが `public.invitations` にメールアドレスを登録し、本人の初回マジックリンク要求時に
  アカウントが作成される。未招待メールアドレスの拒否はSupabase Authの Before User Created Hook
  （`public.hook_before_user_created`、`supabase/migrations/*_invitations_and_signup_hook.sql`）で
  **サーバー側に強制している**。クライアント側で拒否を実現しようとしないこと（クライアント設定
  `OTP.Config.createUser = true` の理由は [data.instructions.md](data.instructions.md) を参照）。
- Hookは拒否時に `{"error": {"http_code": 403, "message": "email_not_invited"}}` を返す。
  **SQL側のメッセージとKotlin側の定数 `EMAIL_NOT_INVITED_HOOK_MESSAGE`（`data` 層の `SupabaseAuthRepository`）は
  必ず一致させること。** クライアントでの受け取られ方と例外変換は [data.instructions.md](data.instructions.md) を参照。
  Hookのmessageはそのままクライアントに返るため、内部情報を含めないこと。
- **Hookの有効化はマイグレーションでは行えず、Supabaseダッシュボードでの手動設定が必要**
  （Authentication → Hooks）。`supabase/config.toml` の `[auth.hook.before_user_created]` は
  ローカル環境（`supabase start`）専用。本番の手順は `supabase/README.md` を参照。
  Hookが無効なまま「Allow new users to sign up」をONにすると、誰でもアカウントを作れてしまう。
- HookはSupabase Studioの「Invite user」やAdmin API経由のユーザー作成でも実行される。
  Studioから手動でユーザーを追加する場合も、先に `invitations` への登録が必要。
- Hook関数は `SECURITY DEFINER` で `invitations` を参照し（`supabase_auth_admin` 用のRLSポリシーを
  追加しない）、`anon` / `authenticated` からの `EXECUTE` 権限は必ず剥奪する（剥奪しないと
  Data APIの `rpc` 経由で呼び出せてしまう）。
- ログイン画面が招待済みかどうかを区別できるエラーを返すことは意図的に許容しているトレードオフ
  （理由は [data.instructions.md](data.instructions.md) を参照）。
- Edge Function + Admin API（`inviteUserByEmail`）方式は、TypeScript/Deno・secret key管理・
  デプロイ経路の追加が必要になるため採用していない（`docs/requirements.md` 5節）。
