package jp.co.yaz.kanuchi.domain.role

import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException

/**
 * ユーザーの権限 (`profiles.role`) を変更するRepositoryインターフェース。
 * 実装はdata層 (SupabaseUserRoleRepository) が提供する。
 *
 * 権限の変更は admin のみ可能で、DB 側 (RLS・トリガー) で強制される。
 */
interface UserRoleRepository {
    /**
     * ユーザー [userId] の権限を [role] に変更する (member → admin への昇格、admin → member への降格)。
     *
     * 失敗時は以下の例外を返す (presentation 層が文言に変換して表示する)。
     * - 降格によって利用中の admin が1人もいなくなる場合: [LastActiveAdminRequiredException]
     * - それ以外 (通信エラー、ログイン中のユーザーが admin でなくなっていて変更できなかった場合、
     *   自分自身の降格 (UI で操作を出さないため個別の例外にしない) 等):
     *   [jp.co.yaz.kanuchi.domain.common.GenericDataFailureException]
     */
    suspend fun changeRole(
        userId: String,
        role: UserRole,
    ): Result<Unit>
}
