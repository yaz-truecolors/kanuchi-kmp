// wasmJsBrowserDistribution の成果物を静的配信する最小限のサーバー（スモークテスト専用）。
// 依存を増やさないため Node.js 標準モジュールのみで実装し、127.0.0.1 にだけ bind する。
import { createReadStream } from 'node:fs';
import { stat } from 'node:fs/promises';
import { createServer } from 'node:http';
import path from 'node:path';

const rootDir = path.resolve(process.env.DIST_DIR ?? '');
const port = Number(process.env.PORT ?? '0');
if (!process.env.DIST_DIR || !port) {
  console.error('DIST_DIR と PORT を環境変数で指定してください');
  process.exit(1);
}

// .wasm は application/wasm でないと WebAssembly.instantiateStreaming が失敗する
const contentTypes = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.mjs': 'text/javascript; charset=utf-8',
  '.wasm': 'application/wasm',
  '.json': 'application/json; charset=utf-8',
  '.map': 'application/json; charset=utf-8',
  '.txt': 'text/plain; charset=utf-8',
  '.ttf': 'font/ttf',
  '.otf': 'font/otf',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.xml': 'application/xml; charset=utf-8',
};

createServer(async (req, res) => {
  let filePath;
  try {
    const { pathname } = new URL(req.url ?? '/', 'http://localhost');
    filePath = path.join(rootDir, decodeURIComponent(pathname).replace(/\/$/, '/index.html'));
  } catch {
    res.writeHead(400).end();
    return;
  }
  if (!filePath.startsWith(rootDir + path.sep)) {
    res.writeHead(403).end();
    return;
  }
  try {
    if (!(await stat(filePath)).isFile()) throw new Error('not a file');
  } catch {
    res.writeHead(404).end();
    return;
  }
  res.writeHead(200, {
    'Content-Type': contentTypes[path.extname(filePath)] ?? 'application/octet-stream',
    'Cache-Control': 'no-store',
  });
  createReadStream(filePath).pipe(res);
}).listen(port, '127.0.0.1', () => {
  console.log(`serving ${rootDir} at http://127.0.0.1:${port}/`);
});
