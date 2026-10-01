package jp.co.yaz.kanuchi.domain.profile

/**
 * ユーザーのプロフィールを扱うRepositoryインターフェース。
 * 実装はdata層 (SupabaseProfileRepository) が提供する。
 *
 * 失敗時は [jp.co.yaz.kanuchi.domain.common.GenericDataFailureException] を返す
 * (presentation 層が文言に変換して表示する)。
 */
interface ProfileRepository {
    /**
     * ログイン中のユーザー自身のプロフィールを取得する。
     */
    suspend fun getCurrentUserProfile(): Result<UserProfile>

    /**
     * 参照できるすべてのユーザーのプロフィールを、表示名の順に取得する。
     *
     * 参照できる範囲は DB のアクセス制御 (RLS) で決まる: admin は全員分、member は自分の分だけ。
     */
    suspend fun getProfiles(): Result<List<UserProfile>>

    /**
     * ログイン中のユーザー自身の表示名を変更し、変更後のプロフィールを返す。
     */
    suspend fun updateDisplayName(displayName: DisplayName): Result<UserProfile>
}
