package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.WorkCalendar
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TeamMonthlySummaryTest {
    private val october = YearMonth(2026, 10)
    private val alice = profile("alice")
    private val bob = profile("bob")
    private val carol = profile("carol", isSuspended = true)
    private val dave = profile("dave", isSuspended = true)

    private val projectA = Project(id = "p-a", name = "案件A", isActive = true)
    private val projectB = Project(id = "p-b", name = "案件B", isActive = false)
    private val projectC = Project(id = "p-c", name = "案件C", isActive = true)

    private fun profile(
        name: String,
        isSuspended: Boolean = false,
    ) = UserProfile(id = "id-$name", email = "$name@example.com", displayName = name, role = UserRole.MEMBER, isSuspended = isSuspended)

    private fun record(
        day: Int,
        vararg allocations: Pair<String, Int>,
    ) = WorkRecord(
        date = LocalDate(2026, 10, day),
        allocations = allocations.associate { (projectId, hundredths) -> projectId to Hours.ofHundredths(hundredths) },
    )

    private fun create(
        profiles: List<UserProfile> = listOf(alice, bob, carol, dave),
        records: Map<String, List<WorkRecord>> = emptyMap(),
        shiftSettings: Map<String, ShiftSettings> = emptyMap(),
        today: LocalDate = LocalDate(2026, 12, 31),
        projects: List<Project> = listOf(projectA, projectB, projectC),
    ) = TeamMonthlySummary.create(october, profiles, records, shiftSettings, WorkCalendar(emptyList()), today, projects)

    @Test
    fun `suspended users are included only when they have records and are placed after active users`() {
        val summary = create(profiles = listOf(alice, carol, bob, dave), records = mapOf(carol.id to listOf(record(1))))

        assertEquals(listOf(alice, bob, carol), summary.members.map { it.profile })
        assertEquals(october, summary.yearMonth)
    }

    @Test
    fun `each member sheet is built from the member records and shift settings`() {
        val shift = ShiftSettings.DEFAULT.copy(minHours = Hours.ofHundredths(10_000), maxHours = Hours.ofHundredths(12_000))
        val aliceRecords = listOf(record(1), WorkRecord(LocalDate(2026, 10, 2), flag = DayFlag.ABSENCE))
        val summary = create(records = mapOf(alice.id to aliceRecords), shiftSettings = mapOf(alice.id to shift))

        val aliceSheet = summary.members.single { it.profile == alice }.sheet
        val expected = MonthlyWorkSheet.create(october, aliceRecords, shift, WorkCalendar(emptyList()), LocalDate(2026, 12, 31))
        assertEquals(shift, aliceSheet.shiftSettings)
        assertEquals(expected.totalWorkingHours, aliceSheet.totalWorkingHours)
        assertEquals(expected.businessDayCount, aliceSheet.businessDayCount)
        assertEquals(WorkingHoursRangeStatus.ABOVE_MAX, aliceSheet.rangeStatus)
        // 設定を保存していないメンバーは初期値で計算する
        assertEquals(
            ShiftSettings.DEFAULT,
            summary.members
                .single { it.profile == bob }
                .sheet.shiftSettings,
        )
    }

    @Test
    fun `project hours are summed over members and sorted by hours then name`() {
        val summary =
            create(
                records =
                    mapOf(
                        alice.id to listOf(record(1, "p-a" to 300, "p-b" to 200), record(2, "p-c" to 500)),
                        bob.id to listOf(record(1, "p-b" to 300, "p-c" to 100)),
                        // 利用停止中でも記録があれば集計に含める
                        carol.id to listOf(record(5, "p-a" to 200)),
                    ),
            )

        // 無効な案件 (案件B) も実績があれば含める。同じ時間なら案件名の順
        assertEquals(
            listOf(
                TeamProjectHours("p-c", projectC, Hours.ofHundredths(600)),
                TeamProjectHours("p-a", projectA, Hours.ofHundredths(500)),
                TeamProjectHours("p-b", projectB, Hours.ofHundredths(500)),
            ),
            summary.projectHours,
        )
    }

    @Test
    fun `project hours exclude days after today and projects without hours`() {
        val summary =
            create(
                records = mapOf(alice.id to listOf(record(1, "p-a" to 300), record(2, "p-b" to 400, "p-c" to 0))),
                today = LocalDate(2026, 10, 1),
            )

        assertEquals(listOf(TeamProjectHours("p-a", projectA, Hours.ofHundredths(300))), summary.projectHours)
    }

    @Test
    fun `unknown projects are kept without the project`() {
        val summary = create(records = mapOf(alice.id to listOf(record(1, "p-x" to 100))), projects = emptyList())

        val hours = summary.projectHours.single()
        assertEquals("p-x", hours.projectId)
        assertNull(hours.project)
    }

    @Test
    fun `no members results in empty summary`() {
        val summary = create(profiles = emptyList())

        assertTrue(summary.members.isEmpty())
        assertTrue(summary.projectHours.isEmpty())
    }
}
