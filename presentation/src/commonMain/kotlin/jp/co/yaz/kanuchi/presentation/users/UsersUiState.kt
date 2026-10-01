package jp.co.yaz.kanuchi.presentation.users

import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.user.UserManagementOverview

/**
 * ユーザー管理画面 (招待リスト・利用停止) のUI状態。
 */
data class UsersUiState(
    /** 表示中の内容 (全ユーザー・招待リスト)。初回の読み込み中・読み込み失敗時は null。 */
    val overview: UserManagementOverview? = null,
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    /** 招待するメールアドレスの入力欄の内容。 */
    val inviteEmail: String = "",
    /** 招待・取り消し・利用停止・復帰のいずれかを実行中か (実行中は他の操作ボタンを無効にする)。 */
    val isSubmitting: Boolean = false,
    val inviteError: InviteError? = null,
    /** 直前に招待リストへ追加したメールアドレス (追加した旨の表示用)。 */
    val invitedEmail: String? = null,
    /** 取り消し・利用停止・復帰の失敗。ダイアログで表示する。 */
    val actionError: UserActionError? = null,
    /** 利用停止の確認ダイアログの対象。ダイアログを表示していなければ null。 */
    val suspendConfirmationTarget: UserProfile? = null,
) {
    /** 操作ボタンを押せるか。読み込み中・実行中は押せない。 */
    val canOperate: Boolean get() = overview != null && !isLoading && !isSubmitting
}

/** 招待の失敗の種類。 */
enum class InviteError {
    /** メールアドレスの形式が正しくない */
    INVALID_EMAIL,

    /** 既に招待リストに登録されている */
    ALREADY_INVITED,

    /** 通信エラー等 */
    SAVE_FAILED,
}

/** 取り消し・利用停止・復帰の失敗の種類。 */
enum class UserActionError {
    /** 利用中の admin が1人もいなくなるため拒否された (他の admin が同時に操作した場合等) */
    LAST_ACTIVE_ADMIN_REQUIRED,

    /** 通信エラー等 */
    SAVE_FAILED,
}
