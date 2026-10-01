package jp.co.yaz.kanuchi.data.work

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class WorkRecordDtoTest {
    @Test
    fun `postgrest response with embedded allocations is mapped to the domain model`() {
        val json =
            """
            [{"work_date":"2026-10-01","clock_in":"09:00:00","clock_out":"18:15:00","break_hours":0.75,
              "flag":null,"note":"メモ",
              "allocations":[{"project_id":"p-1","hours":5.50},{"project_id":"p-2","hours":2}]},
             {"work_date":"2026-10-02","clock_in":null,"clock_out":null,"break_hours":null,
              "flag":"holiday","note":null,"allocations":[]}]
            """.trimIndent()

        val records = Json.decodeFromString<List<WorkRecordDto>>(json).map { it.toDomain() }

        assertEquals(
            listOf(
                WorkRecord(
                    date = LocalDate(2026, 10, 1),
                    clockIn = TimeOfDay.of(9, 0),
                    clockOut = TimeOfDay.of(18, 15),
                    breakHours = Hours.ofHundredths(75),
                    note = "メモ",
                    allocations = mapOf("p-1" to Hours.ofHundredths(550), "p-2" to Hours.ofHundredths(200)),
                ),
                WorkRecord(date = LocalDate(2026, 10, 2), flag = DayFlag.VACATION),
            ),
            records,
        )
    }

    @Test
    fun `zero break hours is kept as an input`() {
        val dto = WorkRecordDto(workDate = "2026-10-03", breakHours = 0.0)

        assertEquals(Hours.ZERO, dto.toDomain().breakHours)
    }

    @Test
    fun `flags are converted between the db values and the domain`() {
        assertEquals(DayFlag.VACATION, dayFlagOf("holiday"))
        assertEquals(DayFlag.ABSENCE, dayFlagOf("absence"))
        assertEquals("holiday", DayFlag.VACATION.toDbValue())
        assertEquals("absence", DayFlag.ABSENCE.toDbValue())
        DayFlag.entries.forEach { assertEquals(it, dayFlagOf(it.toDbValue())) }
        assertFailsWith<IllegalArgumentException> { dayFlagOf("unknown") }
    }

    @Test
    fun `select columns embed allocations`() {
        assertEquals(
            "work_date, clock_in, clock_out, break_hours, flag, note, allocations(project_id, hours)",
            WorkRecordDto.COLUMNS,
        )
    }
}
