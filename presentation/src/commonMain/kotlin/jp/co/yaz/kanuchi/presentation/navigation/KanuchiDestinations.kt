package jp.co.yaz.kanuchi.presentation.navigation

/**
 * アプリ全体のNavigationグラフ ([KanuchiNavHost]) で使う遷移先ルート。
 * v1スコープでは "login" (マジックリンク送信画面) のみを持つ。
 * ログイン後の画面 (日次入力・集計等) は今後のタスクで追加する。
 */
object KanuchiDestinations {
    const val LOGIN = "login"
}
