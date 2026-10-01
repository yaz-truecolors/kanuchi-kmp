package jp.co.yaz.kanuchi.data.user

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class InvitationDtoTest {
    @Test
    fun `invitations response is decoded`() {
        val json = """[{"email":"new@example.com"},{"email":"taro@example.com"}]"""

        val dtos = Json.decodeFromString<List<InvitationDto>>(json)

        assertEquals(listOf("new@example.com", "taro@example.com"), dtos.map { it.email })
    }

    @Test
    fun `insert body contains only email`() {
        assertEquals("""{"email":"new@example.com"}""", Json.encodeToString(InvitationDto(email = "new@example.com")))
    }
}
