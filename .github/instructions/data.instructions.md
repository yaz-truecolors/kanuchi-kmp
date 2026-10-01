---
applyTo: "data/**"
---

# Supabase 連携・data 層（`data/`）の規約

Repository 実装・Supabase クライアント（supabase-kt）・DTO/マッパーを変更するときの規約・ハマりどころです。
領域共通の原則（アーキテクチャ・モジュール依存関係・共通コード規約・テスト実行）は
[copilot-construction.md](../../copilot-construction.md) を参照してください。
DB側（マイグレーション・RLS・トリガー・Hook の SQL）の規約は [supabase.instructions.md](supabase.instructions.md) を参照。

## 認証・RLS・鍵の扱い

- 認証はマジックリンクのみ。パスワード認証・OAuthは実装しない。
- RLS（Row Level Security）前提の設計。`data` 層のRepository実装はRLSに違反しないクエリになっているか
  必ず確認する（本人データのみ／adminは全件、等）。RLS ポリシー自体の方針は `docs/requirements.md` の「5. 認証・権限設計」。
- SupabaseのURL・anonキーはRLSで保護される前提の公開可能な値として扱う。秘匿すべき鍵
  （service_role key等）は絶対にクライアントコードに埋め込まない。
- Supabaseは2026年末までに `anon`/`service_role` キーを廃止予定。新規実装では
  publishable key（`sb_publishable_...`）/ secret key（`sb_secret_...`）方式を使う。
  `supabase-kt` の `createSupabaseClient()` は単純に文字列キーを渡すだけなので、
  どちらの方式でも実装コードの変更は不要。

## 例外の扱い

- **supabase-ktの例外をそのままUIに表示しない。** `AuthRestException` 等の `message`/`toString()`
  にはAuthorizationヘッダーを含む生のHTTPレスポンス詳細が含まれており、そのまま
  `errorMessage`としてUIに出すと内部情報が漏れる（実際に発生した事故: `LoginViewModel`が
  `error.message`をそのまま表示していたところ、画面に`Headers: {Authorization=[******`
  が表示されてしまった）。data層のRepository実装で例外を捕捉し、`AuthRestException.errorDescription`
  （Supabase Authが提供するユーザー向け説明文）をmessageに持つ例外か、具体的な理由を提示できない場合は
  `domain` の無メッセージのマーカー例外（例: `GenericAuthFailureException`）のどちらかに
  変換してから`Result.failure`に包むこと。`data` 層はUI表示用の文言を持たないため、汎用的な日本語メッセージへの
  変換は `presentation` 層が行う（詳細は [presentation.instructions.md](presentation.instructions.md) の「文言・テキスト定数」）。
- `AuthRepository`インターフェース側のKDocに、失敗時の例外ごとの表示方法を契約として明記している:
  `EmailNotInvitedException` と `GenericAuthFailureException` は `presentation` 層が `strings.xml` の文言に変換して
  表示し、それ以外の例外で message が非nullの場合は「UIにそのまま表示してよい（実装側が認証ヘッダー等の
  内部詳細を含まない安全なメッセージに変換する責任を持つ）」。
- コルーチン内で `catch (e: Exception)` する際の `CancellationException` の扱いは
  `copilot-construction.md` の「3. コード規約（共通）」を参照（先に `catch` して再送出する）。

## DB アクセス（PostgREST）の実装パターン

- Repository 実装は `SupabaseProfileRepository` を手本にする。
  - テーブルの1行は `@Serializable` の DTO（`internal`、列名が snake_case の場合は `@SerialName`）で受け、
    `toDomain()` で domain のモデルに変換する。DTO と変換はテストしやすいよう Repository と分け、
    `data/src/commonTest/` に変換のテスト（JSON → DTO → domain）を書く。
  - 取得する列は `Columns.list(...)` で明示する（`select()` の既定の `*` だと、テーブルに列が増えたときに
    DTO にない列で失敗したり、不要な列まで取得したりするため）。
  - 例外は共通の `runCatchingData { ... }`（`data/profile/SupabaseProfileRepository.kt`）で
    `GenericDataFailureException` に変換して `Result` で返す。supabase-kt の例外の message は UI に出さない。
  - ログイン中のユーザーIDは `supabaseClient.auth.currentUserOrNull()?.id` で取得する。
  - DB が返す特定のエラーを画面で個別に表示したい場合は、`runCatchingData` が包んだ例外の `cause` を
    `PostgrestRestException` として取り出し、`code`（SQLSTATE。例: 一意制約違反 `23505`、`raise exception` の既定 `P0001`）と
    `error`（例外のメッセージ。例: `last_active_admin_required`）で判別して domain のマーカー例外に変換する
    （`data/user/UserManagementFailureMapping.kt` の `toUserManagementFailure`）。判別は HTTP ステータスではなく
    SQLSTATE とメッセージで行う（PostgREST は `P0001` を 400、`42501` を 403 等に変換するため、ステータスでは区別できない）。
    `PostgrestRestException` は HttpResponse が無いと作れずテストしにくいため、判別はコードとメッセージを受け取る関数に分けてテストする。
- 列の型ごとの受け渡し: `time` 型は PostgREST から `HH:mm:ss` 形式の文字列で返る（保存時は `HH:mm` で送ってよい）。
  `numeric` 型は JSON の数値なので `Double` で受け、domain の `Hours` に変換するときに 0.01 単位に四捨五入する
  （実例: `ShiftSettingsDto`）。
- 「行が無ければ追加、あれば上書き」は `upsert(...) { onConflict = "<unique な列>" }` で行う（実例:
  `SupabaseShiftSettingsRepository`。RLS の insert / update の両方のポリシーが必要）。
- RLS で参照・変更できない行は、エラーにならず「0行」として扱われることがある（PostgREST の仕様）。
  例えば権限の無い `update` は失敗せず何も更新しない。変更の成否を確かめたい場合は、
  `select()` を付けて更新後の行を受け取り、0行なら失敗として扱う。
  （`insert` は RLS で拒否されると HTTP 403・`code` `42501` のエラーになる。`delete` も `update` と同じく0行になるので、
  `select()` を付けて削除した行数を確かめる。実例: `SupabaseProjectRepository`）
- 制約違反などをユーザーに理由を伝えるエラーに変換する場合は、`PostgrestRestException.code`（Postgres のエラーコード。
  unique 制約違反は `23505`）で判別する（制約違反の message は Postgres が組み立てる文言なので判別に使わない。
  `raise exception` で独自に投げたエラーは、上記のとおり `code` と独自のメッセージで判別する）。`runCatchingData` で `GenericDataFailureException` に
  包んだ後、`cause` の `code` を見て domain の専用例外（例: `DuplicateProjectNameException`）に変換する。
  `PostgrestRestException` は `HttpResponse` が必要でテストで作りにくいため、「コード → 例外」の変換は純粋な関数に分けてテストする
  （例: `projectNameSaveFailure`）。
- 親子のテーブル（例: `work_records` と `allocations`）は、外部キーを使った埋め込み（resource embedding）で1回のリクエストで取得する
  （例: `select(Columns.raw("work_date, ..., allocations(project_id, hours)"))`。`Columns.raw` は引用符の外の空白を取り除いて送る）。
  埋め込んだ子の行は DTO の `List<...>` で受ける（子が無ければ空の配列で返る）。子のテーブルの RLS も適用される。
  実例: `SupabaseWorkRecordRepository`・`WorkRecordDto`。
- `date` 型は PostgREST から `yyyy-MM-dd` の文字列で返るので `LocalDate.parse` で変換し、絞り込み（`gte` / `lte`）には
  `LocalDate.toString()` を渡す。`time` 型・`numeric` 型の変換は `data/common/DbValues.kt`（`parseDbTime` / `hoursOf` / `Hours.toDbValue()`）を使う。

## ログイン状態（セッション）・マジックリンクの戻り

- マジックリンクから戻った際のURL（`#access_token=...`）の取り込み、URLの掃除、セッションの localStorage への保存・
  起動時の復元・自動更新は、supabase-kt の Auth プラグインが初期化時に自動で行う。`SupabaseAuthRepository` は
  その結果（`auth.sessionStatus`）を domain の `AuthState` に変換するだけで、自前でURLやストレージを扱わない。
- **Auth の `flowType` は既定の IMPLICIT のままにする（PKCE にしない）。** PKCE はリンクを要求したブラウザに保存した
  値でしかログインを完了できないため、「PCで要求して、別のブラウザ（メールアプリ内のブラウザ等）でリンクを開く」と
  ログインできない。
- **マジックリンクの戻り先（`redirectUrl`）は必ず明示的に渡す。** supabase-kt の既定値は `window.location.origin`
  （パスを含まない）で、GitHub Pages（`https://<owner>.github.io/kanuchi-kmp/`）では `/kanuchi-kmp/` が落ちて
  別のページに戻ってしまう。戻り先はブラウザの現在のURLから作る必要があるが、`data` は Node.js ターゲットも持つため
  ブラウザAPI（`kotlinx.browser.window`）を使えない。そのため `AuthRedirectUrl` を `app-wasmjs` の Koin モジュールで
  組み立てて注入している。戻り先は Supabase の Redirect URLs に登録されている必要がある（`supabase/README.md`）。
- 戻ってきたURLにエラー（`#error_code=otp_expired&error_description=...` 等）が含まれている場合、supabase-kt は
  `AuthEvent.OtpError` を `auth.events`（`@SupabaseExperimental`、replay=1）に流す。`consumeMagicLinkCallbackError()` は
  その `replayCache` を1回だけ読む。**`errorDescription` はURLを書き換えれば任意の文字列にできるため、UIに表示しない**
  （`errorCode` だけで `MagicLinkCallbackError` に変換する）。
  利用停止中（`auth.users.banned_until` が未来）のユーザーは、マジックリンクの要求（`signInWith(OTP)`）は成功しメールも届くが、
  リンクを開くと `#error_code=user_banned` で戻る（`AuthErrorCode.UserBanned` → `MagicLinkCallbackError.SUSPENDED`）。
  そのため、利用停止のエラーはマジックリンク送信時ではなく、リンクを開いた後のログイン画面に表示される。
- `SessionStatus.RefreshFailure`（トークン更新のネットワークエラー等。supabase-kt が自動で再試行する）は
  `AuthState.Unknown` に変換する。`SignedOut` にすると、一時的な通信断でログイン画面に戻されてしまう。
- `auth.signOut()`（既定の LOCAL スコープ）は、Supabase がエラーを返した場合（`RestException`）はローカルのセッションを
  消してから例外を投げるが、ネットワークエラー等はセッションを消さずに例外を投げる（ログインしたままになる）。
  どちらも `GenericAuthFailureException` に変換し、ログイン状態の変化は `observeAuthState()` 側で判断する。

## 招待制アカウント作成（クライアント側）

アカウント作成は招待制（招待リスト＋Before User Created Hook）。仕組みの全体像とサーバー側（SQL）の規約は
[supabase.instructions.md](supabase.instructions.md) の「招待制アカウント作成（Before User Created Hook）」を参照。

- 未招待メールアドレスの拒否はHookで**サーバー側に強制している**。クライアントの `OTP.Config.createUser` は
  この方式では `true` にする（`false` だと招待済みユーザーが初回ログインできない）。
  「未招待の拒否をクライアント設定で実現しようとしない」こと。クライアントは改ざん可能であり、防御にならない。
- Hookは拒否時に `{"error": {"http_code": 403, "message": "email_not_invited"}}` を返す。
  Supabase Authはこれを `error_code: "unknown"`・`msg: "email_not_invited"` のHTTP 403として
  クライアントに返し、supabase-ktでは `AuthRestException.errorDescription == "email_not_invited"` になる。
  data層（`SupabaseAuthRepository`）はこれを `EmailNotInvitedException` に変換する。
  **SQL側のメッセージとKotlin側の定数 `EMAIL_NOT_INVITED_HOOK_MESSAGE` は必ず一致させること。**
- あわせて `AuthErrorCode.SignupDisabled`（Supabase側で新規登録自体がOFFの場合）と
  `AuthErrorCode.OtpDisabled`（`createUser = false` 時の未登録）も同じ例外に変換している。
- ログイン画面は「入力したメールアドレスが招待済みかどうか」を区別できるエラーを返す。
  これは**意図的に許容しているトレードオフ**である（原理的に避けられないわけではない）。
  社内チーム向けツールであり、入力ミス・未招待をその場で本人に伝えるUXを優先した。
  なお、UIの文言を統一しても、Supabase Auth APIのレスポンス自体（HTTP 200 / 403）で区別できるため、
  完全に防ぐにはAuth APIの前にサーバー側の中継エンドポイントを置いてレスポンスを正規化する必要がある
  （Edge Function等が必要になるため採用していない）。

## 全ユーザー分の一括取得（PostgREST の `max_rows`）

- PostgREST は1回のレスポンスで返す行数を `max_rows`（`supabase/config.toml` の `[api] max_rows`、
  クラウドの既定値も 1000）で打ち切る。**打ち切られてもエラーにならない**ため、全ユーザー分の稼働記録のように
  件数が 1000 行を超えうる一括取得では、`range()` でページングして取得する
  （例: `SupabaseWorkRecordRepository.getWorkRecordsOfAllUsers`）。
- ページングは並び順を一意に固定し（例: `user_id`・`work_date` の順）、取得件数がページサイズ未満になったら終える。
  ページサイズはサーバーの `max_rows` 以下にすること（超えると1ページ目で打ち切られた件数が
  ページサイズ未満になり、続きがあるのに取得を終えてしまう）。
- 画面ごとにユーザー単位で取得する（N+1）のではなく、テーブルごとに1回（＋ページング）で取得し、
  ドメイン層でユーザーIDごとにまとめる。
