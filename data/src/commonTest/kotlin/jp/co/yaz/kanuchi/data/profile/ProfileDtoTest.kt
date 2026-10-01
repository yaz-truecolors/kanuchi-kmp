package jp.co.yaz.kanuchi.data.profile

import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ProfileDtoTest {
    @Test
    fun `postgrest response is decoded and mapped to the domain model`() {
        val json = """{"id":"user-1","email":"taro@example.com","display_name":"太郎","role":"admin"}"""

        val profile = Json.decodeFromString<ProfileDto>(json).toDomain()

        assertEquals(UserProfile(id = "user-1", email = "taro@example.com", displayName = "太郎", role = UserRole.ADMIN), profile)
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
