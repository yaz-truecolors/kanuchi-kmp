package jp.co.yaz.kanuchi.data.role

import jp.co.yaz.kanuchi.data.user.toUserManagementFailure
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException

/**
 * PostgREST が返したエラー (SQLSTATE の [code] とメッセージ [message]) を、権限変更の domain の例外に変換する。
 * 判別はユーザー管理 (利用停止) と共通の [toUserManagementFailure] で行い、権限変更で起こり得る
 * [LastActiveAdminRequiredException] だけを返す。それ以外は null (呼び出し元で汎用のエラーとして扱う)。
 *
 * 自分自身の降格 (`cannot_demote_self`) は UI でボタンを出さないため、利用停止の `cannot_suspend_self` と同じく個別の文言にしない。
 */
internal fun toRoleChangeFailure(
    code: String?,
    message: String?,
    cause: Throwable? = null,
): Exception? = toUserManagementFailure(code = code, message = message, cause = cause) as? LastActiveAdminRequiredException

/**
 * 権限の update で更新された行数 [updatedRowCount] から、変更の成否を判定する。
 *
 * RLS で更新できない行 (ログイン中のユーザーが admin でなくなった場合等) は、エラーにならず0行の更新として扱われる
 * (data.instructions.md の「DB アクセス（PostgREST）の実装パターン」)。そのため0行なら失敗とする。
 */
internal fun roleChangeResult(updatedRowCount: Int): Result<Unit> =
    if (updatedRowCount > 0) {
        Result.success(Unit)
    } else {
        Result.failure(GenericDataFailureException(IllegalStateException("no profile row was updated")))
    }
