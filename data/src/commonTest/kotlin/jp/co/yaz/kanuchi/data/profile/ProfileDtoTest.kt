package jp.co.yaz.kanuchi.data.profile

import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ProfileDtoTest {
    @Test
    fun `postgrest response is decoded and mapped to the domain model`() {
        val json = """{"id":"user-1","email":"taro@example.com","display_name":"太郎","role":"admin","suspended_at":null}"""

        val profile = Json.decodeFromString<ProfileDto>(json).toDomain()

        assertEquals(UserProfile(id = "user-1", email = "taro@example.com", displayName = "太郎", role = UserRole.ADMIN), profile)
    }

    @Test
    fun `user with suspended_at is mapped to suspended`() {
        val json = """{"id":"u","email":"a@example.com","display_name":"a","role":"member","suspended_at":"2026-10-01T09:00:00+00:00"}"""

        assertTrue(Json.decodeFromString<ProfileDto>(json).toDomain().isSuspended)
    }

    @Test
    fun `user without suspended_at is not suspended`() {
        assertFalse(ProfileDto("user-1", "a@example.com", "a", "member").toDomain().isSuspended)
    }

    @Test
    fun `member role is mapped to member`() {
        assertEquals(UserRole.MEMBER, ProfileDto("user-1", "a@example.com", "a", "member").toDomain().role)
    }

    @Test
    fun `unknown role is treated as member`() {
        assertEquals(UserRole.MEMBER, ProfileDto("user-1", "a@example.com", "a", "owner").toDomain().role)
    }

    @Test
    fun `role is converted back to the db value`() {
        assertEquals("admin", UserRole.ADMIN.toDbValue())
        assertEquals("member", UserRole.MEMBER.toDbValue())
    }
}
