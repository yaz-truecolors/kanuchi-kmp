---
applyTo: "domain/**"
---

# domain 層（`domain/`）の規約

Entity / Value Object / UseCase / Domain Service / Repository インターフェースを変更するときの規約です。

## アーキテクチャ原則

domain 層の設計原則は領域共通の原則として [copilot-construction.md](../../copilot-construction.md) の
「1. アーキテクチャ原則」に書かれているので、必ず参照すること。対象となる主な規約は以下のとおり
（内容の正は `copilot-construction.md` 側）。

- `domain` は他のどのモジュールにも依存しない
- 新しいビジネスロジックは `domain` に置き、UI都合やSupabase都合のロジックを混ぜない
- Repository はインターフェースのみ `domain` に置く（実装は `data`）
- Excelの数式相当のロジックは Domain Service として実装し、DBに保存せず都度計算する
- Value Object は不変・自己検証にする

## 文言・例外

- `domain` 層はUI表示用の文言を一切持たない。エラー等で「具体的な理由を提示できない」場合は、
  ドメイン層に無メッセージのマーカー例外（例: `GenericAuthFailureException`）を定義し、
  `presentation` 層がそれを `strings.xml` 管理下の汎用メッセージにマッピングする
  （詳細は [presentation.instructions.md](presentation.instructions.md) の「文言・テキスト定数」）。
- `AuthRepository` インターフェースのKDocには、失敗時の例外ごとの表示方法を契約として明記している:
  `EmailNotInvitedException` と `GenericAuthFailureException` は `presentation` 層が文言に変換して表示し、
  それ以外の例外で message が非nullの場合は「UIにそのまま表示してよい（実装側が安全なメッセージに変換する
  責任を持つ）」。実装側の変換ルールは [data.instructions.md](data.instructions.md) の「例外の扱い」を参照。
- DB へのアクセスの失敗で具体的な理由を表示できない場合は `GenericDataFailureException`（`domain/common/`）を使う。
  入力内容に問題がある等、ユーザーに理由を伝えたい失敗は、専用の例外（文言は持たない）を domain に定義し、
  presentation 層で `strings.xml` の文言に変換する。
- マジックリンクから戻った際のエラーは `MagicLinkCallbackError`（種別のみの enum）で表す。URLに含まれる説明文は
  誰でも書き換えられるため、domain にメッセージとして持ち込まない。

## 時刻・時間数・入力の検証

- DB の `time` 型（時刻）は `TimeOfDay`（分単位）、`numeric(p, 2)` の時間数（休憩時間・稼働時間等）は `Hours`
  （0.01 時間単位の整数で保持し、2進小数の誤差を避ける。負の値は作れない）で表す（いずれも `domain/common/`）。
  利用者の入力文字列の解釈は `TimeOfDay.parse`（`HH:mm`）・`Hours.parse`（0以上、小数第2位まで）を使う。
- 入力の検証は domain に置き、DB の check 制約と同じ条件と、列の型に収まる範囲（例: `numeric(4, 2)` なら 99.99 以下）を
  確認する（実例: `ShiftSettingsInput.toShiftSettings()`、`DisplayName.of()`）。満たしていない条件は文言を持たない
  enum（例: `ShiftSettingsViolation`）で返し、presentation 層が入力欄ごとの文言に変換する。

## 日付・祝日・稼働時間の計算

- 日付・月は kotlinx-datetime の `LocalDate` / `YearMonth` を使う（domain は `api` で公開しているので、data・presentation からも使える）。
  月の初日・末日・日数は `YearMonth.firstDay` / `lastDay` / `numberOfDays`、前月・翌月は `minusMonth()` / `plusMonth()`。
- 「今日」は日本時間（Asia/Tokyo）の日付で、`kotlin.time.Clock` を注入して `Clock.todayInJapan()`（`domain/calendar/JapanTime.kt`）で求める。
  テストでは固定の `Clock` を渡す（実例: `GetTodayUseCaseTest` の `FixedClock`）。`Clock.System` は `DomainModule` で登録している。
  - 日本時間は `TimeZone.of("Asia/Tokyo")` ではなく固定の UTC+9（`JAPAN_TIME_ZONE`）で表す。wasmJs ではタイムゾーンのデータベース
    （tzdb）を別途組み込まないと地域名のタイムゾーンを使えないため。日本は夏時間が無いので固定オフセットで同じ結果になる。
- 日本の祝日は `JapaneseHolidays`（`domain/calendar/`）で判定する（外部 API・年ごとのデータは使わない）。祝日法の改正や特例
  （2019年の即位、2020・2021年のオリンピック等）があった年は、年ごとの条件分岐として追加し、内閣府の祝日一覧
  （https://www8.cao.go.jp/chosei/shukujitsu/syukujitsu.csv、Shift_JIS）と照合するテスト（`JapaneseHolidaysTest`）にその年の一覧を加える。
  一覧では振替休日・国民の休日が「休日」と表記される点に注意。
  - 振替休日の判定は国民の休日の後に行う（両方に当たる日は振替休日になる）。2006年以前は「翌日のみ」・「日曜日は国民の休日にならない」と
    ルールが違う。春分・秋分の日は浮動小数の誤差を避けるため整数演算の近似式で求めている（2000〜2099年で有効）。
- 稼働日・稼働時間・過不足・月次の集計は `WorkCalendar` / `WorkingHoursCalculator` / `AllocationBalancer` / `MonthlyWorkSheet`
  （`domain/work/`）に集約している。画面側で同じ計算を書かず、`MonthlyWorkSheet` の値（`WorkDay` の各値・月の集計値）を表示する。
  仕様は `docs/requirements.md` の「9. 日次入力・集計の仕様」。
- 負になり得る時間（過不足・未配分）は `SignedHours`（`Hours` と同じく 0.01 時間単位の整数）で表す。`Hours - Hours` は `SignedHours` を返す。
  割合は `AllocationRatio`（0.1% 単位の整数、分母が 0 なら null）で表す。
