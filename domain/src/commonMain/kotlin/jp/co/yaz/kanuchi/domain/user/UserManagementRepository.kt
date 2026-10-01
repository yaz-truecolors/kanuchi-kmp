package jp.co.yaz.kanuchi.domain.user

import jp.co.yaz.kanuchi.domain.auth.EmailAddress

/**
 * ユーザー管理 (招待リスト・利用停止) の操作を扱うRepositoryインターフェース。
 * 実装はdata層 (SupabaseUserManagementRepository) が提供する。
 *
 * いずれの操作も admin のみ可能で、DB 側 (RLS・関数・トリガー) で強制される。
 * 失敗時は、各メソッドに記載した例外か [jp.co.yaz.kanuchi.domain.common.GenericDataFailureException] を返す
 * (presentation 層が文言に変換して表示する)。
 */
interface UserManagementRepository {
    /**
     * 招待リストのメールアドレスを、新しく招待した順に取得する。
     */
    suspend fun getInvitedEmails(): Result<List<String>>

    /**
     * メールアドレスを招待リストに追加する。招待メールは送信されない (アプリのURLを本人へ別途伝える運用)。
     * 招待された本人がログイン画面からログインリンクを要求した時点でアカウントが作成される。
     *
     * 既に招待済みの場合は [AlreadyInvitedException] を返す。
     */
    suspend fun invite(email: EmailAddress): Result<Unit>

    /**
     * 招待を取り消す (招待リストから削除する)。既に作成済みのアカウントは削除されない
     * (ログインできないようにするには [setUserSuspended] で利用停止する)。
     *
     * 削除できなかった場合 (既に取り消されていた・admin でなくなった等) も失敗を返す。
     */
    suspend fun revokeInvitation(email: String): Result<Unit>

    /**
     * ユーザーを利用停止する ([suspended] = true)、または復帰させる (false)。
     *
     * 利用停止したユーザーはログインできなくなる (アカウントと過去の記録は残る)。
     * 自分自身の利用停止はできない (DB 側で拒否される)。
     * 利用中の admin が1人もいなくなる場合は [LastActiveAdminRequiredException] を返す。
     */
    suspend fun setUserSuspended(
        userId: String,
        suspended: Boolean,
    ): Result<Unit>
}
