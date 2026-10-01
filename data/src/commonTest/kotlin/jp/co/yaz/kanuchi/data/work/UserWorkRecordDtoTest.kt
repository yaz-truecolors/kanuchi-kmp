package jp.co.yaz.kanuchi.data.work

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class UserWorkRecordDtoTest {
    @Test
    fun `postgrest response of multiple users is grouped by user in date order`() {
        val json =
            """
            [{"user_id":"u-2","work_date":"2026-10-02","clock_in":null,"clock_out":null,"break_hours":null,
              "flag":"absence","note":null,"allocations":[]},
             {"user_id":"u-1","work_date":"2026-10-02","clock_in":"09:00:00","clock_out":"18:00:00","break_hours":1,
              "flag":null,"note":"メモ","allocations":[{"project_id":"p-1","hours":8}]},
             {"user_id":"u-1","work_date":"2026-10-01","clock_in":null,"clock_out":null,"break_hours":null,
              "flag":null,"note":null,"allocations":[{"project_id":"p-2","hours":7.5}]}]
            """.trimIndent()

        val recordsByUserId = Json.decodeFromString<List<UserWorkRecordDto>>(json).toWorkRecordsByUserId()

        assertEquals(
            mapOf(
                "u-1" to
                    listOf(
                        WorkRecord(date = LocalDate(2026, 10, 1), allocations = mapOf("p-2" to Hours.ofHundredths(750))),
                        WorkRecord(
                            date = LocalDate(2026, 10, 2),
                            clockIn = TimeOfDay.of(9, 0),
                            clockOut = TimeOfDay.of(18, 0),
                            breakHours = Hours.ofHundredths(100),
                            note = "メモ",
                            allocations = mapOf("p-1" to Hours.ofHundredths(800)),
                        ),
                    ),
                "u-2" to listOf(WorkRecord(date = LocalDate(2026, 10, 2), flag = DayFlag.ABSENCE)),
            ),
            recordsByUserId,
        )
    }

    @Test
    fun `empty response results in no users`() {
        assertTrue(emptyList<UserWorkRecordDto>().toWorkRecordsByUserId().isEmpty())
    }

    @Test
    fun `columns include the user id and the embedded allocations`() {
        assertEquals("user_id, ${WorkRecordDto.COLUMNS}", UserWorkRecordDto.COLUMNS)
    }
}
