package jp.co.yaz.kanuchi.presentation.navigation

/**
 * アプリ全体のNavigationグラフ ([KanuchiNavHost]) で使う遷移先ルート。
 *
 * どの画面を表示するかはログイン状態で決まり、[KanuchiNavHost] が自動で切り替える
 * (画面から直接 [LOADING] / [LOGIN] / [HOME] の間を遷移させない)。
 */
object KanuchiDestinations {
    /** 起動直後、ログイン状態 (保存済みセッションの復元・マジックリンクからの戻り) を確認している間の画面。 */
    const val LOADING = "loading"

    /** 未ログイン時の画面 (マジックリンク送信)。 */
    const val LOGIN = "login"

    /** ログイン後の画面。 */
    const val HOME = "home"
}
