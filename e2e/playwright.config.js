import { existsSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { defineConfig, devices } from '@playwright/test';

const e2eDir = path.dirname(fileURLToPath(import.meta.url));
// Gradle タスク（:app-wasmjs:smokeTest）からは DIST_DIR が渡される。直接実行時は既定の出力先を使う。
const distDir = path.resolve(
  process.env.DIST_DIR ?? path.join(e2eDir, '../app-wasmjs/build/dist/wasmJs/productionExecutable'),
);
if (!existsSync(path.join(distDir, 'index.html'))) {
  throw new Error(
    `${distDir}/index.html が見つかりません。先に ./gradlew :app-wasmjs:wasmJsBrowserDistribution を実行してください。`,
  );
}
const port = Number(process.env.SMOKE_TEST_PORT ?? '4178');
const baseURL = `http://127.0.0.1:${port}/`;

export default defineConfig({
  testDir: 'tests',
  outputDir: 'test-results',
  // 成功したテストの出力（console.log 等）は残さず、失敗時のみ残す
  preserveOutput: 'failures-only',
  forbidOnly: !!process.env.CI,
  retries: 0,
  workers: 1,
  timeout: 90_000,
  reporter: process.env.CI ? [['list'], ['github']] : 'list',
  use: {
    ...devices['Desktop Chrome'],
    baseURL,
    // 失敗時の原因調査用（CI では test-results/ を artifact としてアップロードする）
    screenshot: 'only-on-failure',
    trace: 'retain-on-failure',
    // Service Worker 経由の通信は page.route で捕捉できないため無効化する
    serviceWorkers: 'block',
  },
  webServer: {
    command: `"${process.execPath}" serve.js`,
    cwd: e2eDir,
    env: { DIST_DIR: distDir, PORT: String(port) },
    url: baseURL,
    reuseExistingServer: false,
    stdout: 'pipe',
  },
});
