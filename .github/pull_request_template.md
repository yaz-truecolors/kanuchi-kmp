## 概要

<!-- このPRで何を実現するかを1〜3行で -->

## 変更内容

<!-- 主な変更点を箇条書きで -->

-

## 背景

<!-- なぜこの変更が必要か。関連Issue・PRがあれば記載（例: Closes #123） -->

## 確認方法

<!-- 実施した確認と結果を書く（例: `./gradlew verify` 成功、Chromeで画面表示を確認） -->

## チェックリスト

<!-- 規約の詳細は copilot-construction.md と、変更したパスに対応する .github/instructions/*.instructions.md を参照。該当しない項目は行ごと削除してよい -->

- [ ] `./gradlew verify` がローカルで成功した
- [ ] （UI変更時）実ブラウザ（Chrome）で実際に描画・操作できることを確認した
- [ ] （UI変更時）文言を `strings.xml` に集約した（Composable/ViewModel に直書きしていない）
- [ ] （新規テーブル追加時）RLS有効化・`authenticated` への grant・policy の3点をセットで追加した
- [ ] （例外処理追加時）外部ライブラリの例外メッセージをそのままUIに表示していない
- [ ] （依存追加・更新時）バージョンは `gradle/libs.versions.toml` で管理している（`build.gradle.kts` に直書きしていない）
- [ ] 新しく分かった規約・ハマりどころを該当する領域別ファイル（`.github/instructions/`、領域を問わないものは `copilot-construction.md`）に追記した（無ければ削除）
