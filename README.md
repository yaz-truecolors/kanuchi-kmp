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
├── e2e/            # UI 描画スモークテスト（Playwright。Gradle モジュールではない）
├── supabase/       # マイグレーション・Auth設定・DBテスト（pgTAP）
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

`ktlintCheck` / `detekt` / `allTests` / `:app-wasmjs:wasmJsBrowserDistribution` / `:app-wasmjs:smokeTest`
（UI 描画スモークテスト。後述）を1コマンドで実行します（CI の `build-lint-test` ジョブも同じタスクを実行しています）。
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

### UI 描画スモークテスト

```sh
./gradlew :app-wasmjs:smokeTest
```

本番ビルド（`wasmJsBrowserDistribution` の成果物）をローカルで静的配信し、ヘッドレス Chromium（[Playwright](https://playwright.dev/)）で開いて、
実行時エラー（未捕捉例外・`console.error`）なく `<canvas>` に描画されるかを確認します（`verify` にも含まれます）。
`index.html` の不備や起動時の例外で画面が真っ白になる、といったビルド・lint・単体テストでは検知できない事故を防ぐためのもので、
UI の見た目や操作は検証しません。テスト中は localhost 以外への通信（本番 Supabase 等）をすべて遮断します。

- Node.js は Gradle（Kotlin Gradle プラグイン）がダウンロードしたもの、ブラウザは Playwright 同梱の Chromium を使うため、
  Node.js や Chrome のインストールは不要です（初回は npm パッケージとブラウザのダウンロードのためネットワークが必要です）。
- 失敗した場合は `e2e/test-results/` にスクリーンショット・コンソールログ・Playwright トレースが出力されます
  （CI では artifact `smoke-test-results` としてアップロードされます）。
- テストコードは `e2e/` にあります。手元の Node.js で直接実行・デバッグする方法は
  [.github/instructions/e2e.instructions.md](.github/instructions/e2e.instructions.md) を参照してください。

### DBテスト（RLS・トリガー・招待制Hook）

```sh
./supabase/tests/run.sh
```

Supabase の実際の Postgres イメージをローカルの Docker で起動してマイグレーションを適用し、
アクセス制御ルール（RLS・トリガー・招待制Hook）を pgTAP で検証します（`supabase test db`）。
Docker と [Supabase CLI](https://supabase.com/docs/guides/local-development/cli/getting-started) が必要なため
`verify` には含まれていません（CI では `db-test` ジョブで実行されます）。詳細は
[supabase/README.md](supabase/README.md) の「動作検証について（DBテスト）」を参照してください。

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
