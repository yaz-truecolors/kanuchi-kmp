# Kanuchi（鍛冶）要件定義書 / 設計書

## 1. 背景・目的

Excelファイル「受託作業時間内訳管理」で個人が手作業で行っていた、日次の勤怠入力・案件別工数配分・月次集計を、
チームで使える簡易Webアプリに置き換える。

副次的な目的として、Kotlin Multiplatform (KMP) を用いたアプリ開発のサンプルとして社内チームへ還元する
（アーキテクチャ・規約・CI/CDを含め、教育的な完成度を意識する）。

### 移行元Excelの構成（参考）

- シート「稼働記録」
  - ヘッダー：作業年月、営業日数（自動計算）、予定/実績の月稼働時間
  - 案件別（最大8件）：予定割合・予定時間・実績割合・実績時間
  - 日別テーブル：出勤/退勤/休憩の変更値、稼働時間（自動計算）、過不足チェック、案件別工数配分、備考
- シート「設定」
  - 標準シフト（始業・終業・休憩）
  - 稼働範囲の上限・下限（月間稼働時間の目安）
  - 休暇記号・不良記号

## 2. アプリ概要

- アプリ名：**Kanuchi（鍛冶）**
- 用途：チーム内での日次稼働時間・案件別工数配分の入力、および管理者による集計確認
- 対象プラットフォーム：Webブラウザのみ（Kotlin/Wasm）
- 対象ブラウザ：**Google Chrome を推奨環境とする**（他ブラウザでの動作は保証しない）
- 利用形態：サーバー費用ゼロで運用（無料枠内）

### UI/UX方針

- **言語**：日本語のみ。多言語対応（i18n）は行わない。
- **フォント**：**Noto Sans JP**（Google製、日本語を含む標準的なゴシック体、SIL Open Font License 1.1）を
  全画面で使用する。日本語限定利用のため、他言語フォントの選定・フォールバック設計は不要と判断した。
- **アクセシビリティ**：スクリーンリーダー対応（`semantics`指定、`liveRegion`等）を含む
  アクセシビリティ対応は本アプリでは行わない方針とする。関連コードは原則含めない。
  （社内の限定利用ツールであり、対応コストに見合わないと判断したため）
- **文言・テキスト定数の管理**：Compose Multiplatformの公式リソース機構
  （`composeResources/values/strings.xml` → 生成される `Res.string.*`）を用いて一元管理する。
  多言語対応は行わないが、文言の可読性・保守性・将来の多言語化の余地を考慮しこの仕組みに従う。
  KMP/Compose Multiplatformプロジェクトにおける文字列・フォント等静的リソースの管理方法として
  公式に提供されている唯一の標準機構であるため採用した（詳細は `.github/instructions/presentation.instructions.md` 参照）。

## 3. 技術スタック

| 項目 | 選定 |
|---|---|
| 言語・UI | Kotlin Multiplatform + Compose Multiplatform（`wasmJs` ターゲットのみ） |
| バックエンド | Supabase（PostgreSQL + Auth） |
| クライアントSDK | supabase-kt（`auth-kt`, `postgrest-kt`） |
| ホスティング | GitHub Pages（静的ファイル配信、`wasmJs` ビルド成果物） |
| DI | Koin |
| Navigation | Compose Multiplatform 公式 Navigation |
| フォント | Noto Sans JP（Compose Multiplatformリソース機構でバンドル配信） |
| 文言管理 | Compose Multiplatformリソース機構（`composeResources/values/strings.xml`） |
| Lint / フォーマッタ | ktlint + detekt 併用 |
| テスト | kotlin.test（domain / data / ViewModel ロジックを対象）、Playwright（本番ビルドの起動・描画スモークテストのみ） |
| CI/CD | GitHub Actions |
| バージョン管理 | Gradle Version Catalog（`libs.versions.toml`）で一元管理 |
| パッケージ名 | `jp.co.yaz.kanuchi` |
| リポジトリ | `yaz-truecolors/kanuchi-kmp`（Public） |

### 技術選定の背景

- サーバーコストをかけられないため、Supabase（無料枠）+ GitHub Pages（静的ホスティング）の
  完全無料構成を採用。
- 開発者（本プロジェクトオーナー）はAndroid/Kotlinの知見があるため、KMP + Compose Multiplatformを採用し、
  UIをHTML/CSS/JSではなくKotlinで統一。
- Hiltは注釈処理（KAPT/KSP）とAndroidコンポーネントに強く依存するためKMPでは利用不可。
  代替としてリフレクション/注釈処理なしで動作するKoinを採用。
- GitHub PagesでのPages機能はOrganizationがFreeプランのため、Publicリポジトリでのみ無料利用可能。
  そのため本リポジトリはPublicで作成。

## 4. アーキテクチャ

Clean Architecture + DDD（戦術パターン）による層分離。Androidアプリと同様の構成を踏襲する。

### モジュール構成

| モジュール | 役割 | 依存 |
|---|---|---|
| `domain` | Entity / Value Object / UseCase / Repositoryインターフェース。プラットフォーム非依存の純Kotlin | なし |
| `data` | Repository実装、Supabase DTO・マッパー、リモートデータソース | domain |
| `presentation` | Compose Multiplatform UI、ViewModel、UI State | domain |
| `app-wasmjs` | エントリポイント、DI組み立て（Koin）、ターゲット固有設定 | 全て |

### DDD戦術パターンの適用例

- **Entity**：`WorkRecord`（日次稼働記録）、`Project`
- **Value Object**：`WorkingHours`、`AllocationRatio`、`ShiftTime`
- **Aggregate Root**：`MonthlyWorkSheet`（月次シート全体、整合性を保証）
- **Domain Service**：`WorkingHoursCalculator`（Excelの稼働時間数式相当）、`AllocationBalancer`（過不足計算相当）
- **Repository**（interfaceはdomain、実装はdata）：`WorkRecordRepository`、`ProjectRepository`

Excelで数式により自動計算していた値（稼働時間・過不足・割合など）は、DBに保存せず
Domain Service側で都度計算する「導出値」として扱う。

## 5. 認証・権限設計

- 認証方式：**マジックリンク**（メールアドレスのみ、パスワード不要）
- ロール：`member` / `admin` の2種類
- **アカウント作成方針：招待制（招待リスト方式）**。adminが招待リスト（`invitations`テーブル）に
  メールアドレスを登録し、招待された本人がログイン画面からマジックリンクを要求した時点で
  アカウントが作成される。招待リストに無いメールアドレスでは、ログイン画面から新規アカウントが
  作成されることはない。
  - 未招待メールアドレスの拒否は、Supabase Authの**Before User Created Hook**（ユーザー作成直前に
    呼ばれるPostgres関数）でサーバー側に強制する。クライアント側の実装や設定には依存しない。
  - サーバーレス関数（Edge Function）＋Admin API（`inviteUserByEmail`）による招待方式も検討したが、
    TypeScript/Denoの導入・secret keyの取り扱い・デプロイ経路の追加が必要になるため採用しなかった。
    本方式はSQL（マイグレーション）とRLSのみで完結する。
  - 招待メールは自動送信されない。admin は招待リスト登録後、アプリのURLを本人へ別途（Slack等で）伝える。
  - 招待リストへの登録方法：
    - （招待画面の実装までの暫定運用）Supabase管理画面（Supabase Studio）のTable Editorから`invitations`に行を追加
    - （v1で実装予定）アプリ内の管理者用「ユーザー招待」画面から登録（RLSによりadminのみ登録可）。
      ログイン後のセッション管理の実装後に追加する
  - ログイン画面は「入力されたメールアドレスが招待済みかどうか」を区別するエラーを返す。
    メール列挙（招待済みアドレスの推測）に悪用され得るが、社内チーム向けツールであり、
    入力ミス・未招待をその場で本人に伝えるUXを優先して**意図的に許容する**。
    完全に防ぐにはAuth APIの前にサーバー側の中継エンドポイントが必要になるため、採用しない。
- 権限昇格：アプリ内に「管理者権限付与」画面を用意し、adminが**既存の**他ユーザーをadminに昇格させる
  （新規ユーザーの招待とは別の機能）
- 初期admin：運用開始時、Supabase管理画面から手動で最初の1人のみ`profiles.role`を`admin`に設定
- RLS（Row Level Security）方針：
  - `work_records` / `allocations`：本人データのみ参照・編集可、adminは全員分を参照可
  - `projects` / `shift_settings`：`projects`は全員参照可・編集はadminのみ、`shift_settings`は本人のみ参照・編集可

## 6. DBスキーマ（v1）

| テーブル | 主な列 | 説明 |
|---|---|---|
| `profiles` | id, email, display_name, role(`member`/`admin`) | `auth.users` と1:1のユーザー情報 |
| `projects` | id, name, is_active | 案件マスタ（チーム共有、admin管理） |
| `user_projects` | user_id, project_id | ユーザーごとの担当案件割当（多対多） |
| `shift_settings` | id, user_id(unique), start_time, end_time, break_hours, min_hours, max_hours | ユーザーごとの勤務時間設定 |
| `work_records` | id, user_id, work_date, clock_in, clock_out, break_hours, flag(祝/欠/null), note | 日次実績（unique: user_id + work_date） |
| `allocations` | id, work_record_id, project_id, hours | 日×案件の工数配分 |
| `invitations` | email(PK), invited_by, created_at | 招待リスト（ここに登録されたメールアドレスのみアカウント作成可、admin管理） |

### v1のスコープ外（将来検討）

- 案件別「予定割合・予定時間」による計画値管理（Excelの(予)割合・(予)時間に相当）。
  v1では日々の実績入力と集計のみを提供し、将来的に追加を検討する。

## 7. 開発環境・規約

- **Lint/フォーマッタ**：ktlint（フォーマット）+ detekt（静的解析・複雑度/命名規則チェック）を併用
- **テスト方針**：domain層・data層（Repositoryのフェイク実装含む）・ViewModelのロジックまでを
  kotlin.testでカバー。UI描画テスト（見た目・操作の検証）はスコープ外（KMPでの成熟度が低いため）
  - ただし、ビルド・lint・単体テストでは検知できない「本番ビルドが起動せず画面が真っ白になる」事故を防ぐため、
    本番ビルドをヘッドレスブラウザで開いて描画されるかだけを確認するスモークテスト（Playwright）を行う
  - データのアクセス制御は RLS・トリガー・Auth Hook（DB側）で強制しているため、これらは Supabase の実際の
    Postgres にマイグレーションを適用した状態で pgTAP によるDBテスト（`supabase test db`）でカバーする
    （「5. 認証・権限設計」の権限表の各セル、ロール昇格の制限、招待制Hookの許可/拒否など）
- **CI/CD**（GitHub Actions）：
  - PR時：`build` + `ktlint` + `detekt` + `test` + スモークテストと、DBテスト（Gradleとは独立したジョブ）を自動実行
  - `main`ブランチへのマージ時：自動ビルドし、GitHub Pagesへ自動デプロイ
- **Secrets管理**：SupabaseのURL・匿名キー（anon key）はRLSで保護される前提の公開可能な鍵として、
  GitHub Actionsのビルド変数経由でwasmJsバンドルに埋め込む
- **バージョン管理**：Kotlin / Compose Multiplatform / Ktor等は最新安定版を使用し、
  Gradle Version Catalog（`libs.versions.toml`）で一元管理

## 8. 今後の進め方

1. リポジトリ初期セットアップ（Gradleマルチモジュール構成、ktlint/detekt導入、CI設定）
2. Supabaseプロジェクト作成・DBスキーマ適用・RLSポリシー設定
3. domain層の実装（Entity/Value Object/UseCase/Domain Service）とテスト
4. data層の実装（Supabase連携）とテスト
5. presentation層の実装（画面：ログイン、日次入力、案件別集計、管理者ダッシュボード、権限付与、ユーザー招待）
6. GitHub Pagesへのデプロイ確認・動作検証
