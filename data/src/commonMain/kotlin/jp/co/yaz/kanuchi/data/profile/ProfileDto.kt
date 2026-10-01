package jp.co.yaz.kanuchi.data.profile

import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `profiles` テーブルの1行 (PostgREST のレスポンス)。
 */
@Serializable
internal data class ProfileDto(
    val id: String,
    val email: String,
    @SerialName("display_name") val displayName: String,
    val role: String,
) {
    companion object {
        /** PostgREST で取得する列。テーブルに列が増えても、必要な列だけを取得する。 */
        val COLUMNS = listOf("id", "email", "display_name", "role")
    }
}

/** DB の `profiles.role` の値。DB の check 制約 (`member` / `admin`) と一致させること。 */
internal const val ROLE_ADMIN = "admin"
internal const val ROLE_MEMBER = "member"

internal fun ProfileDto.toDomain(): UserProfile =
    UserProfile(
        id = id,
        email = email,
        displayName = displayName,
        // DB の check 制約により member / admin 以外は入らないが、万一の場合は権限の小さい member として扱う
        role = if (role == ROLE_ADMIN) UserRole.ADMIN else UserRole.MEMBER,
    )

internal fun UserRole.toDbValue(): String =
    when (this) {
        UserRole.ADMIN -> ROLE_ADMIN
        UserRole.MEMBER -> ROLE_MEMBER
    }
