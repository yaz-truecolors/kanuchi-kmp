import { writeFile } from 'node:fs/promises';
import { inflateSync } from 'node:zlib';
import { expect, test } from '@playwright/test';

// 起動直後の「何かが描画されたか」の判定しきい値。canvas のうち最頻色（背景色）以外のピクセルが
// この割合以上あれば描画済みとみなす（ログイン画面は約 2%。真っ白・単色なら 0%）。
// フォント描画差で壊れないよう、ピクセル単位の比較やスナップショット比較はしない。
const MIN_NON_BACKGROUND_RATIO = 0.005;
const RENDER_TIMEOUT_MS = 45_000;

// 許容する console.error（ここに無いものは1件でも失敗させる）。追加する場合は理由を書き、条件をできるだけ限定すること。
const allowedConsoleErrors = [
  {
    // Kotlin/Wasm の生成 JS が、依存ライブラリ（Compose/Skiko）の非推奨 API 利用を console.error で警告する。動作には影響しない。
    reason: 'Kotlin/Wasm ランタイムの非推奨 API 警告',
    matches: (message) => message.text().startsWith('Accessing `memory` via `wasmExports` is deprecated.'),
  },
];

test('本番ビルドが外部通信なしで起動し、canvas に描画される', async ({ page, context, baseURL }, testInfo) => {
  const appOrigin = new URL(baseURL).origin;
  const blockedUrls = new Set();
  const logLines = [];
  const unexpectedErrors = [];
  let failFast;
  const firstUnexpectedError = new Promise((_, reject) => {
    failFast = reject;
  });
  firstUnexpectedError.catch(() => {});
  const recordUnexpected = (text) => {
    unexpectedErrors.push(text);
    failFast(new Error(`予期しないエラーが発生しました:\n${unexpectedErrors.join('\n')}`));
  };

  // localhost（テスト用サーバー）以外への通信はすべて遮断する。本番 Supabase 等へリクエストを投げないため。
  await context.route('**/*', (route) => {
    const url = route.request().url();
    if (new URL(url).origin === appOrigin) {
      return route.continue();
    }
    blockedUrls.add(url);
    logLines.push(`[blocked] ${route.request().method()} ${url}`);
    return route.abort('blockedbyclient');
  });
  await context.routeWebSocket(/.*/, (ws) => {
    logLines.push(`[blocked] WebSocket ${ws.url()}`);
    ws.close();
  });

  page.on('console', (message) => {
    const { url, lineNumber } = message.location();
    logLines.push(`[console.${message.type()}] ${message.text()} (${url}:${lineNumber})`);
    if (message.type() !== 'error') return;
    // 遮断した外部リソースの読み込み失敗は、遮断した URL 由来のものに限って許容する
    const isBlockedResource =
      message.text().startsWith('Failed to load resource: net::ERR_BLOCKED_BY_CLIENT') && blockedUrls.has(url);
    const allowed = allowedConsoleErrors.find((candidate) => candidate.matches(message));
    if (isBlockedResource || allowed) {
      logLines.push(`  -> 許容: ${isBlockedResource ? '遮断した外部リソースの読み込み失敗' : allowed.reason}`);
    } else {
      recordUnexpected(`console.error: ${message.text()} (${url}:${lineNumber})`);
    }
  });
  page.on('pageerror', (error) => {
    logLines.push(`[pageerror] ${error.stack ?? error}`);
    recordUnexpected(`pageerror: ${error.message}`);
  });
  page.on('response', (response) => {
    if (response.status() >= 400) logLines.push(`[http ${response.status()}] ${response.url()}`);
  });

  let lastRatio = 0;
  try {
    await page.goto('/');

    const canvas = page.locator('canvas');
    const rendered = expect
      .poll(
        async () => {
          if ((await canvas.count()) === 0) return false;
          lastRatio = nonBackgroundRatio(await canvas.first().screenshot());
          return lastRatio >= MIN_NON_BACKGROUND_RATIO;
        },
        {
          message: `canvas に描画されること（背景色以外のピクセル割合が ${MIN_NON_BACKGROUND_RATIO} 以上）`,
          timeout: RENDER_TIMEOUT_MS,
          intervals: [250, 500, 1_000],
        },
      )
      .toBe(true);
    await Promise.race([rendered, firstUnexpectedError]);
    // 描画後に読み込まれるリソース（文字列・フォント等）の処理中に出るエラーも検知するため、通信が落ち着くまで待つ
    await Promise.race([page.waitForLoadState('networkidle'), firstUnexpectedError]);

    expect(unexpectedErrors, 'pageerror / 許容外の console.error が発生しないこと').toEqual([]);
  } finally {
    logLines.push(`[smoke] 最後に計測した canvas の背景色以外のピクセル割合: ${lastRatio.toFixed(4)}`);
    const logPath = testInfo.outputPath('console.log');
    await writeFile(logPath, `${logLines.join('\n')}\n`);
    await testInfo.attach('console.log', { path: logPath, contentType: 'text/plain' });
  }
});

// スクリーンショット（PNG）のうち、最頻色（背景色）以外のピクセルの割合を返す。
// WebGL の canvas は preserveDrawingBuffer=false のためページ内で直接ピクセルを読めず、スクリーンショットで判定する。
// 依存を増やさないため、Playwright が出力する PNG（8bit・RGB/RGBA・非インターレース）だけを Node.js 標準の zlib でデコードする。
function nonBackgroundRatio(png) {
  const { width, height, bytesPerPixel, data } = decodePng(png);
  const stride = width * bytesPerPixel;
  const rows = [];
  let previous = new Uint8Array(stride);
  for (let y = 0; y < height; y++) {
    const offset = y * (stride + 1);
    const filter = data[offset];
    const row = Uint8Array.from(data.subarray(offset + 1, offset + 1 + stride));
    for (let x = 0; x < stride; x++) {
      const left = x >= bytesPerPixel ? row[x - bytesPerPixel] : 0;
      const up = previous[x];
      const upLeft = x >= bytesPerPixel ? previous[x - bytesPerPixel] : 0;
      row[x] = (row[x] + [0, left, up, (left + up) >> 1, paeth(left, up, upLeft)][filter]) & 0xff;
    }
    rows.push(row);
    previous = row;
  }
  const counts = new Map();
  let maxCount = 0;
  for (const row of rows) {
    for (let x = 0; x < stride; x += bytesPerPixel) {
      const key = (row[x] << 16) | (row[x + 1] << 8) | row[x + 2];
      const count = (counts.get(key) ?? 0) + 1;
      counts.set(key, count);
      maxCount = Math.max(maxCount, count);
    }
  }
  const total = width * height;
  return total === 0 ? 0 : 1 - maxCount / total;
}

function decodePng(png) {
  let offset = 8;
  let header;
  const idat = [];
  while (offset < png.length) {
    const length = png.readUInt32BE(offset);
    const type = png.toString('latin1', offset + 4, offset + 8);
    const body = png.subarray(offset + 8, offset + 8 + length);
    if (type === 'IHDR') {
      header = { width: body.readUInt32BE(0), height: body.readUInt32BE(4), bitDepth: body[8], colorType: body[9], interlace: body[12] };
    } else if (type === 'IDAT') {
      idat.push(body);
    }
    offset += length + 12;
  }
  const bytesPerPixel = { 2: 3, 6: 4 }[header?.colorType];
  if (!header || header.bitDepth !== 8 || header.interlace !== 0 || !bytesPerPixel) {
    throw new Error(`未対応の PNG 形式です: ${JSON.stringify(header)}`);
  }
  return { width: header.width, height: header.height, bytesPerPixel, data: inflateSync(Buffer.concat(idat)) };
}

function paeth(left, up, upLeft) {
  const estimate = left + up - upLeft;
  const distanceLeft = Math.abs(estimate - left);
  const distanceUp = Math.abs(estimate - up);
  const distanceUpLeft = Math.abs(estimate - upLeft);
  if (distanceLeft <= distanceUp && distanceLeft <= distanceUpLeft) return left;
  return distanceUp <= distanceUpLeft ? up : upLeft;
}
