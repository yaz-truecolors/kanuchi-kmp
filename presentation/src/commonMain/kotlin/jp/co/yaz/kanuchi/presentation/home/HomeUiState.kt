package jp.co.yaz.kanuchi.presentation.home

import jp.co.yaz.kanuchi.domain.profile.UserProfile

/**
 * ホーム画面 (ログイン後の画面) のUI状態。
 */
data class HomeUiState(
    /** ログイン中のユーザーのメールアドレス (ログイン状態から取得するため、プロフィールの読み込み前から表示できる)。 */
    val email: String? = null,
    /** ログイン中のユーザーのプロフィール (表示名・権限)。読み込み中・読み込み失敗時は null。 */
    val profile: UserProfile? = null,
    val isLoadingProfile: Boolean = false,
    val profileLoadFailed: Boolean = false,
    val isSigningOut: Boolean = false,
    val signOutFailed: Boolean = false,
) {
    /** 管理者メニューを表示するか。プロフィールを読み込めて、admin の場合のみ。 */
    val showsAdminMenu: Boolean get() = profile?.isAdmin == true
}
