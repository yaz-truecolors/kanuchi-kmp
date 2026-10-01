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
