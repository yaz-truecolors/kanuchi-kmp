package jp.co.yaz.kanuchi.data.shift

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class ShiftSettingsDtoTest {
    @Test
    fun `postgrest response is decoded and mapped to the domain model`() {
        val json =
            """
            {"user_id":"user-1","start_time":"09:00:00","end_time":"17:45:00",
             "break_hours":0.75,"min_hours":120.50,"max_hours":160.00}
            """.trimIndent()

        val settings = Json.decodeFromString<ShiftSettingsDto>(json).toDomain()

        assertEquals(
            ShiftSettings(
                startTime = TimeOfDay.of(9, 0),
                endTime = TimeOfDay.of(17, 45),
                breakHours = Hours.ofHundredths(75),
                minHours = Hours.ofHundredths(12_050),
                maxHours = Hours.ofHundredths(16_000),
            ),
            settings,
        )
    }

    @Test
    fun `seconds of the db time are truncated`() {
        val dto = ShiftSettingsDto("user-1", "09:30:59.123", "18:30:00", 1.0, 140.0, 180.0)

        assertEquals(TimeOfDay.of(9, 30), dto.toDomain().startTime)
    }

    @Test
    fun `binary floating point errors are rounded to hundredths`() {
        val dto = ShiftSettingsDto("user-1", "09:30:00", "18:30:00", 0.1 + 0.2, 140.0, 180.0)

        assertEquals(Hours.ofHundredths(30), dto.toDomain().breakHours)
    }

    @Test
    fun `domain model is converted to the request body with the user id`() {
        val settings = ShiftSettings.DEFAULT.copy(breakHours = Hours.ofHundredths(125))

        val dto = settings.toDto(userId = "user-1")

        assertEquals(ShiftSettingsDto("user-1", "09:30", "18:30", 1.25, 140.0, 180.0), dto)
        assertEquals(settings, dto.toDomain())
    }

    @Test
    fun `request body uses the db column names`() {
        val json = Json.encodeToString(ShiftSettingsDto.serializer(), ShiftSettings.DEFAULT.toDto(userId = "user-1"))

        assertEquals(
            """{"user_id":"user-1","start_time":"09:30","end_time":"18:30","break_hours":1.0,"min_hours":140.0,"max_hours":180.0}""",
            json,
        )
    }
}
