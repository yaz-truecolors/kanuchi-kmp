package jp.co.yaz.kanuchi.data.work

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkRecordSaveDtoTest {
    @Test
    fun `record is converted to the row to save`() {
        val record =
            WorkRecord(
                date = LocalDate(2026, 10, 1),
                clockIn = TimeOfDay.of(9, 5),
                clockOut = TimeOfDay.of(18, 0),
                breakHours = Hours.ofHundredths(75),
                note = "メモ",
                allocations = mapOf("p-1" to Hours.ofHundredths(100)),
            )

        assertEquals(
            WorkRecordSaveDto(
                userId = "user-1",
                workDate = "2026-10-01",
                clockIn = "09:05",
                clockOut = "18:00",
                breakHours = 0.75,
                flag = null,
                note = "メモ",
            ),
            record.toSaveDto("user-1"),
        )
    }

    @Test
    fun `blank values are sent as null to clear the saved values`() {
        val dto = WorkRecord(date = LocalDate(2026, 10, 2), flag = DayFlag.ABSENCE).toSaveDto("user-1")

        assertEquals(
            """{"user_id":"user-1","work_date":"2026-10-02","clock_in":null,"clock_out":null,""" +
                """"break_hours":null,"flag":"absence","note":null}""",
            Json.encodeToString(dto),
        )
    }

    @Test
    fun `allocation changes contain only the differences`() {
        val current = mapOf("same" to Hours.ofHundredths(100), "changed" to Hours.ofHundredths(200), "removed" to Hours.ofHundredths(300))
        val desired =
            mapOf(
                "same" to Hours.ofHundredths(100),
                "changed" to Hours.ofHundredths(250),
                "added" to Hours.ofHundredths(50),
                "zero" to Hours.ZERO,
            )

        val changes = allocationChangesOf(current, desired)

        assertEquals(mapOf("changed" to Hours.ofHundredths(250), "added" to Hours.ofHundredths(50)), changes.toUpsert)
        assertEquals(setOf("removed"), changes.toDelete)
    }

    @Test
    fun `existing allocation set to zero is deleted`() {
        val changes = allocationChangesOf(mapOf("p" to Hours.ofHundredths(100)), mapOf("p" to Hours.ZERO))

        assertEquals(emptyMap(), changes.toUpsert)
        assertEquals(setOf("p"), changes.toDelete)
    }
}
