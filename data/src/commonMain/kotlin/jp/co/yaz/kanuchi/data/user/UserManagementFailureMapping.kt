package jp.co.yaz.kanuchi.data.user

import jp.co.yaz.kanuchi.domain.user.AlreadyInvitedException
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException

/** Postgres の一意制約違反 (招待リストの email 主キーの重複) */
private const val UNIQUE_VIOLATION = "23505"

/** `raise exception` の既定の SQLSTATE (トリガー guard_active_admins が使う) */
private const val RAISE_EXCEPTION = "P0001"

/** トリガー guard_active_admins が、利用中の admin が1人もいなくなる変更を拒否したときのメッセージ */
private const val LAST_ACTIVE_ADMIN_REQUIRED = "last_active_admin_required"

/**
 * PostgREST が返したエラー (SQLSTATE の [code] とメッセージ [message]) を、ユーザー管理の domain の例外に変換する。
 * 画面で個別の文言を出す必要がないエラーは null を返す (呼び出し元で汎用のエラーとして扱う)。
 *
 * 自分自身の利用停止 (`cannot_suspend_self`) は UI でボタンを出さないため、個別の文言にしない。
 */
internal fun toUserManagementFailure(
    code: String?,
    message: String?,
    cause: Throwable? = null,
): Exception? =
    when {
        code == UNIQUE_VIOLATION -> AlreadyInvitedException(cause)
        code == RAISE_EXCEPTION && message == LAST_ACTIVE_ADMIN_REQUIRED -> LastActiveAdminRequiredException(cause)
        else -> null
    }
