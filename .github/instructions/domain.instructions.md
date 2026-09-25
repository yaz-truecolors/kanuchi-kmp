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
