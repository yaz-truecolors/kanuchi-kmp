---
applyTo: "e2e/**"
---

# UI 描画スモークテスト（`e2e/`）の規約

`e2e/` 配下（Playwright によるスモークテスト）を変更するときの規約・ハマりどころです。
テスト全体の位置付け・実行方法は [copilot-construction.md](../../copilot-construction.md) の「4. テスト・検証（共通）」、
Gradle 側のタスク構成は [build.instructions.md](build.instructions.md) の「集約タスク `verify`」を参照してください。

## 位置付け（何を確認し、何を確認しないか）

- スモークテストが確認するのは **「本番ビルド（`wasmJsBrowserDistribution` の成果物）がブラウザで起動して、
  実行時エラーなく `<canvas>` に何かが描画されるか」だけ**。`index.html` の不備や起動時の例外で画面が真っ白になる、
  といったビルド・lint・単体テストでは検知できない事故を防ぐためのもの。
- UI の見た目（レイアウト・文言・色）や操作（入力・ボタン押下・画面遷移）のテストは引き続きスコープ外。
  スモークテストに画面ごとの検証や操作シナリオを足していかないこと。
- ピクセル完全一致のスナップショット比較はしない（フォントのラスタライズ差で環境ごとに壊れるため）。
  描画判定は「canvas のスクリーンショットのうち最頻色（背景色）以外のピクセルが一定割合以上あるか」で行う。

## 構成と実行

- 構成は `package.json` / `package-lock.json`（依存は `@playwright/test` のみ）、`playwright.config.js`、
  静的配信サーバー `serve.js`（Node.js 標準モジュールのみ）、`tests/smoke.spec.js`。
  TypeScript・lint・フォーマッタ等のツールは追加しない（ktlint / detekt の対象外の言語を最小限にとどめるため）。
- 通常は `./gradlew :app-wasmjs:smokeTest`（`verify` にも含まれる）で実行する。Node.js は Kotlin Gradle プラグインが
  ダウンロードするもの、ブラウザは Playwright 同梱の Chromium（headless shell）を使うので、Node.js・Chrome の
  インストールは不要。Gradle タスクは `npm ci` → `playwright install --only-shell chromium` → `playwright test` の順に実行する。
- 手元の Node.js で直接実行する場合は `e2e/` で `npm ci` と `npx playwright install --only-shell chromium` の後、
  `npx playwright test`（事前に `./gradlew :app-wasmjs:wasmJsBrowserDistribution` が必要。`--headed` や `--debug` で
  ブラウザを表示して調査できる）。
- 依存は `package-lock.json` で固定し、追加・更新は `npm install` で lock ファイルも更新する（Renovate が
  `e2e/package.json` を検知して更新 PR を作る）。Playwright の更新でブラウザ（Chromium）も連動して更新される。
- 待ち合わせは固定 sleep を使わず、`expect.poll` 等のポーリング＋タイムアウトで書く。
- 失敗時は `e2e/test-results/` にスクリーンショット・コンソールログ（`console.log`）・Playwright トレースが残る
  （CI では artifact `smoke-test-results` としてアップロードされる）。トレースは `npx playwright show-trace <trace.zip>` で開ける。

## 外部通信の遮断と console.error の扱い

- **テストは外部ネットワークに依存させない。** テスト用サーバー（`127.0.0.1`）以外への通信は `context.route` /
  `context.routeWebSocket` ですべて遮断している。本番 Supabase（`SupabaseConfig.kt` に本番の URL が埋め込まれている）へ
  リクエストを投げないためでもある。遮断を緩めたり、特定の外部ホストを許可したりしないこと。
- `pageerror`（未捕捉例外）は1件でも失敗させる。`console.error` も原則失敗させ、許容するのは次の2種類だけ:
  - 遮断した URL 由来の `Failed to load resource: net::ERR_BLOCKED_BY_CLIENT`（遮断した URL と一致する場合に限る）。
  - `tests/smoke.spec.js` の `allowedConsoleErrors` に理由付きで列挙したもの（現状は Kotlin/Wasm ランタイムが出す
    `Accessing \`memory\` via \`wasmExports\` is deprecated.` の警告のみ）。
  許容リストを増やす場合は、理由をコメントに書き、メッセージの前方一致など条件をできるだけ限定すること。
- 起動時、Compose の `FallbackFontDownloader` が日本語グリフのフォールバックフォントを `fonts.gstatic.com` に取りに行く
  （Noto Sans JP の読み込み完了前に描画されるため）。テストでは遮断されて失敗ログが出るが、Noto Sans JP の読み込み後に
  正しく描画されるので問題ない。

## ハマりどころ

- `ComposeViewport` の `<canvas>` は Shadow DOM の中に作られるため、`document.querySelector('canvas')` では見つからない。
  Playwright の `page.locator('canvas')` は（open な）Shadow DOM を貫通して検索できるので、こちらを使う。
- Compose for Web/Wasm は WebGL の `<canvas>` に描画し、`preserveDrawingBuffer=false` のためページ内の JavaScript から
  ピクセルを読み出せない（`drawImage` 等で読むと透明の単色になる）。描画判定は Playwright のスクリーンショット（PNG）を Node.js 側で
  デコードして行っている（`zlib` のみ使用。判定用に別ページを開くと失敗時スクリーンショットに空白ページが混ざるため）。
- 2026年9月時点の構成（Kotlin 2.4.20 の webpack 出力は UMD 形式で `import.meta` を含まない）では、`index.html` の
  `type="module"` を外しても描画されることをこのテストで確認している（詳細は
  [presentation.instructions.md](presentation.instructions.md) の「ブラウザ実行時の注意点（wasmJs）」）。
  検出力の確認には、`index.html` に `<script>import.meta.url</script>` を足す、`main()` の先頭で例外を投げる、
  描画内容を空にする、などの壊し方を使う（いずれもテストが失敗することを確認済み）。
- ポート `4178` が使用中の場合は、環境変数 `SMOKE_TEST_PORT` で変更できる。
