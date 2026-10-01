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

- **Entity**：`WorkRecord`（日次稼働記録）、`Project`、`CompanyHoliday`（会社の休業日）
- **Value Object**：`Hours`（時間数）、`SignedHours`（符号付きの時間数。過不足）、`TimeOfDay`（時刻）、
  `AllocationRatio`（割合）、`ShiftSettings`（勤務時間設定）、`DayKind`（日の種類：平日/土日/祝日/会社の休業日）
- **Aggregate Root**：`MonthlyWorkSheet`（1人・1か月分の稼働記録のシート。日ごとの行 `WorkDay` と月の集計値）
- **Domain Service**：`WorkingHoursCalculator`（Excelの稼働時間数式相当）、`AllocationBalancer`（過不足計算相当）、
  `JapaneseHolidays`（日本の祝日の判定）、`WorkCalendar`（稼働日の判定）
- **Repository**（interfaceはdomain、実装はdata）：`WorkRecordRepository`、`ProjectRepository`、
  `ShiftSettingsRepository`、`CompanyHolidayRepository` 等

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
    - アプリ内の管理者用「ユーザー管理」画面から登録・取り消し（RLSによりadminのみ）
    - （初期adminの登録など、アプリを使えない場合）Supabase管理画面（Supabase Studio）のTable Editorから`invitations`に行を追加
  - ログイン画面は「入力されたメールアドレスが招待済みかどうか」を区別するエラーを返す。
    メール列挙（招待済みアドレスの推測）に悪用され得るが、社内チーム向けツールであり、
    入力ミス・未招待をその場で本人に伝えるUXを優先して**意図的に許容する**。
    完全に防ぐにはAuth APIの前にサーバー側の中継エンドポイントが必要になるため、採用しない。
- ログイン状態（セッション）の扱い：
  - メール内のマジックリンクを開くと、アプリ（GitHub PagesのURL）に戻ってきた時点でログイン済みになり、
    ホーム画面が表示される。リンクは**1時間で期限切れ・1回限り有効**（Supabase Authの既定値）。
    期限切れ・使用済みのリンクを開いた場合は、ログイン画面にその旨を表示する。
    - 会社のメールセキュリティ（リンクの事前スキャン）がリンクを先に開いてしまうと、本人が開いたときには
      使用済み扱いになる。その場合もログイン画面で「無効か期限切れ」と表示されるので、送り直してもらう
  - ログイン状態はブラウザ（localStorage）に保存され、再読み込み・ブラウザの再起動後も維持される
    （有効期限の短いアクセストークンは、ログイン中に自動で更新される）
  - ホーム画面の「ログアウト」で、その端末（ブラウザ）のログインだけを解除する（他の端末はログインしたまま）
  - 起動直後はログイン状態を確認する間だけ読み込み中の画面を表示し、確認後にログイン画面かホーム画面を表示する。
    未ログインの状態でログイン後の画面を表示することはない
- 画面構成（ログイン後）：ホーム画面のメニューから各画面を開く。管理者用の画面はadminにだけメニューを表示する
  （表示の制御は使い勝手のためで、アクセス制御はRLS等のDB側で行う）
  - 全員：日次入力、案件別集計、個人設定（表示名・勤務時間設定）
  - adminのみ：管理者ダッシュボード、案件管理、ユーザー管理（招待・利用停止）、権限管理（昇格・降格）、休業日管理
  - 案件別集計は、adminが管理者ダッシュボードから他のメンバーの分も開ける
- 権限管理：アプリ内の「権限管理」画面で、adminが**既存の**他ユーザーをadminに昇格・memberに降格させる
  （新規ユーザーの招待とは別の機能・別の画面）
  - 自分自身の降格と、admin が1人もいなくなる降格はできない（DB側で強制する）
  - 利用停止中のユーザーの権限は画面から変更できない（利用停止中のadminはadminとしての権限を持たず、変更しても意味が無いため。
    必要ならユーザー管理画面で復帰させてから変更する。DB側では制限していない）
- 利用停止（除名）：アプリ内の「ユーザー管理」画面で、adminがメンバーを利用停止・復帰させる
  - 利用停止したメンバーはログインできなくなる。アカウントと過去の稼働記録は削除せず、集計に使えるよう残す
  - 復帰させると、再びログインできるようになる
  - 自分自身の利用停止と、利用中のadminが1人もいなくなる利用停止はできない（DB側で強制する）
  - 利用停止中のユーザーがログインしようとすると、マジックリンクのメールは届くが、リンクを開いた時点でログイン画面に
    利用停止中である旨を表示する（Supabase Authの仕様上、メールの送信時点では拒否できない）
- 初期admin：運用開始時、Supabase管理画面から手動で最初の1人のみ`profiles.role`を`admin`に設定
- RLS（Row Level Security）方針：
  - `work_records` / `allocations`：本人データのみ参照・編集可、adminは全員分を参照可
  - `projects` / `shift_settings`：`projects`は全員参照可・編集はadminのみ、`shift_settings`は本人のみ編集可・
    参照は本人と、adminは全員分（管理者ダッシュボードで各メンバーの定時・稼働時間の下限/上限を使うため）
  - `company_holidays`（会社の休業日）：全員参照可（稼働日の判定に使う）、追加・削除はadminのみ（変更は不可。
    直したい場合は削除して追加し直す）

## 6. DBスキーマ（v1）

| テーブル | 主な列 | 説明 |
|---|---|---|
| `profiles` | id, email, display_name, role(`member`/`admin`), suspended_at（利用停止日時。NULLなら利用中） | `auth.users` と1:1のユーザー情報 |
| `projects` | id, name, is_active | 案件マスタ（チーム共有、admin管理） |
| `user_projects` | user_id, project_id | ユーザーごとの担当案件割当（多対多） |
| `shift_settings` | id, user_id(unique), start_time, end_time, break_hours, min_hours, max_hours | ユーザーごとの勤務時間設定 |
| `work_records` | id, user_id, work_date, clock_in, clock_out, break_hours, flag(`holiday`=休/`absence`=欠/null), note | 日次実績（unique: user_id + work_date）。flag の意味は「9. 日次入力・集計の仕様」 |
| `allocations` | id, work_record_id, project_id, hours | 日×案件の工数配分 |
| `invitations` | email(PK), invited_by, created_at | 招待リスト（ここに登録されたメールアドレスのみアカウント作成可、admin管理） |
| `company_holidays` | holiday_date(PK), name, created_by, created_at | 会社の休業日（土日・祝日以外の休み。admin管理） |

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
5. presentation層の実装（画面：ログイン、ホーム、個人設定、日次入力、案件別集計、管理者ダッシュボード、案件管理、ユーザー管理、権限管理、休業日管理）
6. GitHub Pagesへのデプロイ確認・動作検証

## 9. 日次入力・集計の仕様

Excel「稼働記録」シートの自動計算を、domain層（`WorkingHoursCalculator`・`AllocationBalancer`・`MonthlyWorkSheet`）で
同じ考え方で計算する。計算結果はDBに保存しない（「4. アーキテクチャ」の導出値）。

### 稼働日（営業日）

- 土日は休み。**日本の祝日はアプリが自動で判定する**（`JapaneseHolidays`）。外部APIや年ごとの祝日データには頼らず、
  「国民の祝日に関する法律」のルール（固定日、ハッピーマンデー、春分の日・秋分の日の計算式、振替休日、国民の休日）と、
  2019年（天皇の即位）・2020/2021年（東京オリンピック・パラリンピック）の特例を実装している。対象は2000〜2099年。
  内閣府が公表している祝日一覧と一致することをテストで確認している（2000〜2027年）
  - 法改正や新たな特例があった場合は、`JapaneseHolidays` を修正する
- **会社の休業日**（年末年始・創立記念日等）は、adminが休業日管理画面から追加・削除する（`company_holidays`）
- 稼働日 ＝ 平日（月〜金）で、祝日・会社の休業日でない日

### 日ごとの印（`work_records.flag`）

| 印 | DBの値 | 意味 | 稼働時間 | 営業日数 |
|---|---|---|---|---|
| 休 | `holiday` | 個人の休暇（有給休暇等） | 0 | 数えない |
| 欠 | `absence` | 欠勤 | 0 | 数える |

### 稼働時間（Excel の G 列に相当）

- 稼働日で印が無い日：`(退勤 ?: 定時終業) − (出勤 ?: 定時始業) − (休憩 ?: 定時休憩)`。
  **何も入力していない稼働日も、定時どおり働いたとみなす**（Excelと同じ）。定時は本人の勤務時間設定
  （未保存なら初期値の 9:30〜18:30・休憩1時間）
- 休み（土日・祝日・会社の休業日）：出勤か退勤が入力されている場合だけ稼働として上の式で計算する（片方だけなら他方は定時）。
  どちらも入力されていなければ 0
- 印（休・欠）の日は 0
- 計算結果が負になる場合（入力ミス）は 0 とし、日次入力画面で警告できるよう日の状態として区別する
- 時刻の差（分）は 0.01時間単位に四捨五入してから休憩時間を引く
- **今日より後の日は月の合計に含めない**（実績のみ）。「今日」は日本時間（Asia/Tokyo）の日付

### 過不足（Excel の H 列に相当）

- 日ごとに `稼働時間 − 案件への配分の合計`。0 でない日を「過不足あり」とする（負＝配分しすぎ）
- 月の合計に含める日（今日まで）だけを判定する

### 月次の集計（案件別集計・管理者ダッシュボード）

- **営業日数**：その月の稼働日数 − 「休」の日数（月全体で数える。休みの日に付けた「休」は数に影響しない）
- **月の稼働時間合計**：今日までの日の稼働時間の合計
- **下限・上限との比較**：月の稼働時間合計を、本人の勤務時間設定の稼働時間の下限・上限と比べる（下限未満／範囲内／上限超過）
- **案件ごとの実績時間と割合**：今日までの日の配分の合計。割合の分母は月の稼働時間合計で、0.1%単位に四捨五入する
  （端数のため、割合の合計が100.0%から±0.1%程度ずれることがある）。配分されていない時間は「未配分」として別に表示する
- **過不足がある日数**
- Excelの予定割合・予定時間は対象外（「6. DBスキーマ」の「v1のスコープ外」）


### 管理者ダッシュボード（admin専用）

- 月を切り替えて、メンバーごとの稼働（月の稼働時間合計・下限／上限との比較・営業日数・過不足がある日数）と、
  案件ごとのチーム合計（全メンバーの案件への配分の合計。今日まで）を表示する。メンバーを選ぶと、その人のその月の案件別集計を開く
- 計算は「月次の集計」と同じ（`MonthlyWorkSheet`）。全メンバー分のデータはテーブルごとに一括で取得する（メンバーごとに取得しない）
- 表示するメンバー：利用中のユーザーは全員（admin を含む）。**利用停止中のユーザーは、その月に稼働記録がある場合だけ**表示する
  （記録が無い月まで表示すると、何も入力していない稼働日を定時どおり働いたとみなす計算により、実態の無い稼働時間が表示されるため）
- 並び順：利用中のユーザー → 利用停止中のユーザー、それぞれ表示名の順。案件のチーム合計は時間の多い順で、
  無効になった案件も時間があれば「無効」と付けて表示する
