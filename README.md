# Kanuchi（鍛冶）

社内の「受託作業時間内訳管理」Excelファイルで行っていた、日次の勤怠入力・案件別工数配分・月次集計を
チームで使える簡易Webアプリに置き換えるプロジェクトです。

副次的な目的として、Kotlin Multiplatform (KMP) を用いたアプリ開発のサンプルとして社内チームへ還元することも
念頭に置いています。アーキテクチャ・コード規約・CI/CDまで含め、教育的な完成度を意識しています。

詳細な要件・設計は [docs/requirements.md](docs/requirements.md) を参照してください。

対象ブラウザは **Google Chrome を推奨**します（他ブラウザでの動作は保証していません）。

## 技術スタック

| 項目 | 選定 |
|---|---|
| 言語・UI | Kotlin Multiplatform + Compose Multiplatform（`wasmJs` ターゲットのみ） |
| バックエンド | Supabase（PostgreSQL + Auth） |
| ホスティング | GitHub Pages（静的ファイル配信） |
| アーキテクチャ | Clean Architecture + DDD |
| DI | Koin |
| Navigation | Compose Multiplatform 公式 Navigation |
| フォント | Noto Sans JP（日本語限定利用のため） |
| Lint | ktlint + detekt |
| CI/CD | GitHub Actions |

## モジュール構成

```
kanuchi-kmp/
├── domain/         # Entity / Value Object / UseCase / Repositoryインターフェース（プラットフォーム非依存）
├── data/           # Repository実装、Supabase連携
├── presentation/   # Compose Multiplatform UI、ViewModel
├── app-wasmjs/     # wasmJs エントリポイント（実行可能ファイルを生成する唯一のモジュール）
└── docs/           # 要件定義書・設計書
```

各層の設計方針の詳細は `docs/requirements.md` の「4. アーキテクチャ」を参照してください。

## セットアップ

### 前提条件

- JDK 17以上（[Temurin](https://adoptium.net/) 推奨）
- IDE: [Android Studio](https://developer.android.com/studio) または [IntelliJ IDEA](https://www.jetbrains.com/idea/)
  （Kotlin Multiplatform プラグインが必要な場合はIDE側の指示に従ってインストールしてください）

### ビルド

```sh
./gradlew build
```

### CIと同じ検証をまとめて実行（PR作成前に推奨）

```sh
./gradlew verify
```

`ktlintCheck` / `detekt` / `allTests` / `:app-wasmjs:wasmJsBrowserDistribution` を1コマンドで実行します
（CI の `build-lint-test` ジョブも同じタスクを実行しています）。
失敗箇所をまとめて確認したい場合は `./gradlew verify --continue` を使ってください。
個別に実行したい場合は以下の各コマンドを使います。

### Lint

```sh
./gradlew ktlintCheck   # フォーマットチェック
./gradlew ktlintFormat  # 自動修正
./gradlew detekt        # 静的解析
```

### テスト

```sh
./gradlew allTests
```

`presentation` モジュールは Compose UI テストのために Chrome ブラウザが必要です
（`domain` / `data` はブラウザ不要な Node.js 上でテストされます）。
Chrome を標準の場所にインストールしていない場合は、環境変数 `CHROME_BIN` に Chrome
（Chrome for Testing 等でも可）の実行ファイルのパスを指定してください。

### ローカルでブラウザ実行（開発サーバー）

```sh
./gradlew :app-wasmjs:wasmJsBrowserDevelopmentRun
```

### 本番用ビルド（GitHub Pages配信物）

```sh
./gradlew :app-wasmjs:wasmJsBrowserDistribution
```

成果物は `app-wasmjs/build/dist/wasmJs/productionExecutable` に出力されます。
`main` ブランチへのマージ時、GitHub Actions が自動的にこの成果物をGitHub Pagesへデプロイします。

## ライセンス・その他

社内利用を想定したプロジェクトです。
