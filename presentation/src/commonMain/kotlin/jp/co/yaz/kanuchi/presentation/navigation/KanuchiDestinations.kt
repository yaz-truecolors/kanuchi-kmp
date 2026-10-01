package jp.co.yaz.kanuchi.presentation.navigation

import kotlinx.datetime.YearMonth

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

    /** 案件の担当メンバー (案件管理画面から開く)。admin のみ。引数 [PROJECT_ID_ARG] に案件のIDを取る。 */
    const val PROJECT_MEMBERS = "projects/{projectId}/members"
    const val PROJECT_ID_ARG = "projectId"

    /** [PROJECT_MEMBERS] に案件のIDを埋め込んだルート。 */
    fun projectMembers(projectId: String): String = "projects/$projectId/members"

    /** ユーザー管理 (招待・利用停止)。admin のみ。 */
    const val USERS = "users"

    /** 権限管理 (admin への昇格・降格)。admin のみ。 */
    const val ROLES = "roles"

    /** 日次入力 (自分の稼働記録の入力)。全員が使える。 */
    const val DAILY_INPUT = "daily-input"

    /**
     * 案件別集計。全員が使える。省略できる引数を2つ取る ([summaryOf] で組み立てる)。
     * - [SUMMARY_USER_ID_ARG]: 対象のユーザーID。省略時はログイン中のユーザー自身 (管理者ダッシュボードから他のメンバーの分を開くときに指定する)
     * - [SUMMARY_YEAR_MONTH_ARG]: 表示する月 (`yyyy-MM` 形式。[YearMonth.toString] / [YearMonth.parse])。省略時は今月
     */
    const val SUMMARY = "summary?userId={userId}&yearMonth={yearMonth}"
    const val SUMMARY_USER_ID_ARG = "userId"
    const val SUMMARY_YEAR_MONTH_ARG = "yearMonth"

    /** [SUMMARY] に対象のユーザーID・月を埋め込んだルート。null の引数は省略する (自分の分・今月)。 */
    fun summaryOf(
        userId: String? = null,
        yearMonth: YearMonth? = null,
    ): String {
        val arguments =
            listOfNotNull(
                userId?.let { "$SUMMARY_USER_ID_ARG=$it" },
                yearMonth?.let { "$SUMMARY_YEAR_MONTH_ARG=$it" },
            )
        return if (arguments.isEmpty()) "summary" else "summary?" + arguments.joinToString("&")
    }

    /** 管理者ダッシュボード (メンバーごとの月の稼働状況)。admin のみ。 */
    const val ADMIN_DASHBOARD = "admin-dashboard"

    /** 休業日管理 (会社の休業日の追加・削除)。admin のみ。 */
    const val COMPANY_HOLIDAYS = "company-holidays"
}
