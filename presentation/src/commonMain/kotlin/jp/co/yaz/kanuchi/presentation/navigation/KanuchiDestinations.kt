package jp.co.yaz.kanuchi.presentation.navigation

/**
 * アプリ全体のNavigationグラフ ([KanuchiNavHost]) で使う遷移先ルート。
 *
 * [LOADING] / [LOGIN] / [HOME] のどれを表示するかはログイン状態で決まり、[KanuchiNavHost] が自動で切り替える
 * (画面から直接これらの間を遷移させない)。それ以外はホーム画面のメニューから開き、「戻る」でホーム画面に戻る。
 */
object KanuchiDestinations {
    /** 起動直後、ログイン状態 (保存済みセッションの復元・マジックリンクからの戻り) を確認している間の画面。 */
    const val LOADING = "loading"

    /** 未ログイン時の画面 (マジックリンク送信)。 */
    const val LOGIN = "login"

    /** ログイン後の画面 (メニュー)。 */
    const val HOME = "home"

    /** 個人設定 (表示名・勤務時間設定)。全員が使える。 */
    const val SETTINGS = "settings"

    /** 案件管理 (案件マスタ・担当割当)。admin のみ。 */
    const val PROJECTS = "projects"

    /** ユーザー管理 (招待・利用停止)。admin のみ。 */
    const val USERS = "users"

    /** 権限管理 (admin への昇格・降格)。admin のみ。 */
    const val ROLES = "roles"
}
