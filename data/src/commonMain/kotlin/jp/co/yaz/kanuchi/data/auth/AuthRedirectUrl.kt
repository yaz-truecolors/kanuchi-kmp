package jp.co.yaz.kanuchi.data.auth

/**
 * メール内のマジックリンクを開いた後に戻ってくる先のURL (アプリ自身のURL)。
 *
 * 実行環境 (ブラウザで開かれているURL) に依存するため、data 層では決めずに
 * エントリポイント (app-wasmjs) が組み立てて Koin 経由で注入する。
 *
 * supabase-kt の既定値 (`window.location.origin`) はパスを含まないため使わない。
 * GitHub Pages ではアプリが `https://<owner>.github.io/<repo>/` のようにパスの下で配信されており、
 * 既定値のままだとリンクを開いた後に `https://<owner>.github.io/` (別のページ) へ戻ってしまう。
 *
 * このURLは Supabase の Auth 設定「Redirect URLs」に登録されている必要がある
 * (未登録の場合、Supabase は「Site URL」へリダイレクトする)。手順は supabase/README.md を参照。
 */
class AuthRedirectUrl(
    val value: String,
)
