package jp.co.yaz.kanuchi.presentation.dailyinput

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import jp.co.yaz.kanuchi.presentation.dailyinput.DailyInputTestFixture.Companion.TODAY
import jp.co.yaz.kanuchi.presentation.dailyinput.DailyInputTestFixture.Companion.USER_ID
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyInputViewModelTest : MainDispatcherTest() {
    private val fixture = DailyInputTestFixture()

    private fun DailyInputUiState.rowOf(day: Int) = rows.first { it.date == LocalDate(2026, 10, day) }

    @Test
    fun `this month is shown first with a row for each day`() {
        val state = fixture.createListViewModel().uiState.value

        assertEquals(YearMonth(2026, 10), state.yearMonth)
        assertFalse(state.isLoading)
        assertEquals(31, state.rows.size)
        assertTrue(state.rowOf(15).isToday)
        assertFalse(state.rowOf(14).isToday)
        assertTrue(state.hasFutureDays)
    }

    @Test
    fun `blank times on working days are shown as shift defaults and entered values as they are`() {
        fixture.workRecordRepository.records[USER_ID] =
            listOf(WorkRecord(LocalDate(2026, 10, 1), clockIn = TimeOfDay.of(9, 0), breakHours = Hours.ofHundredths(50)))

        val state = fixture.createListViewModel().uiState.value

        val entered = state.rowOf(1)
        assertEquals(ShownValue("09:00", isDefault = false), entered.clockIn)
        assertEquals(ShownValue("18:30", isDefault = true), entered.clockOut)
        assertEquals(ShownValue("0.50", isDefault = false), entered.breakHours)
        assertEquals(Hours.ofHundredths(900), entered.workingHours)

        val blank = state.rowOf(2)
        assertEquals(ShownValue("09:30", isDefault = true), blank.clockIn)
        assertEquals(ShownValue("1.00", isDefault = true), blank.breakHours)
    }

    @Test
    fun `weekends and flagged days show no times`() {
        fixture.workRecordRepository.records[USER_ID] = listOf(WorkRecord(LocalDate(2026, 10, 5), flag = DayFlag.VACATION))

        val state = fixture.createListViewModel().uiState.value

        // 2026-10-03 は土曜日
        assertNull(state.rowOf(3).clockIn)
        assertEquals(Hours.ZERO, state.rowOf(3).workingHours)
        assertNull(state.rowOf(5).clockIn)
        assertEquals(DayFlag.VACATION, state.rowOf(5).flag)
    }

    @Test
    fun `imbalance is shown for counted days and hidden for future days without records`() {
        fixture.workRecordRepository.records[USER_ID] =
            listOf(
                WorkRecord(LocalDate(2026, 10, 1), allocations = mapOf("project-1" to Hours.ofHundredths(800))),
                WorkRecord(LocalDate(2026, 10, 20), note = "予定"),
            )

        val state = fixture.createListViewModel().uiState.value

        assertEquals(SignedHours.ofHundredths(0), state.rowOf(1).balance)
        assertFalse(state.rowOf(1).hasImbalance)
        assertEquals(SignedHours.ofHundredths(800), state.rowOf(2).balance)
        assertTrue(state.rowOf(2).hasImbalance)
        // 今日より後の日は、記録が無ければ過不足を表示しない。記録があれば表示するが、合計に含めないため目立たせない
        assertNull(state.rowOf(21).balance)
        assertEquals(SignedHours.ofHundredths(800), state.rowOf(20).balance)
        assertFalse(state.rowOf(20).hasImbalance)
        assertTrue(state.rowOf(20).hasNote)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        fixture.workRecordRepository.getFailure = GenericDataFailureException()
        val viewModel = fixture.createListViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertTrue(
            viewModel.uiState.value.rows
                .isEmpty(),
        )

        fixture.workRecordRepository.getFailure = null
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(31, viewModel.uiState.value.rows.size)
    }

    @Test
    fun `changing the month loads that month`() {
        val viewModel = fixture.createListViewModel()

        viewModel.onYearMonthChanged(YearMonth(2026, 11))

        val state = viewModel.uiState.value
        assertEquals(YearMonth(2026, 11), state.yearMonth)
        assertEquals(30, state.rows.size)
        assertEquals(LocalDate(2026, 11, 1), state.rows.first().date)
        assertTrue(state.rows.none { it.isToday })
    }

    @Test
    fun `month is not reloaded on first show but reloaded when shown again`() {
        val viewModel = fixture.createListViewModel()
        viewModel.onScreenShown()
        assertEquals(1, fixture.workRecordRepository.getCallCount)

        fixture.workRecordRepository.records[USER_ID] = listOf(WorkRecord(LocalDate(2026, 10, 1), note = "保存した"))
        viewModel.onScreenShown()

        assertEquals(2, fixture.workRecordRepository.getCallCount)
        assertTrue(
            viewModel.uiState.value
                .rowOf(1)
                .hasNote,
        )
    }

    @Test
    fun `failed reload keeps showing the previous month`() {
        val viewModel = fixture.createListViewModel()
        viewModel.onScreenShown()

        fixture.workRecordRepository.getFailure = GenericDataFailureException()
        viewModel.onScreenShown()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(31, viewModel.uiState.value.rows.size)
    }

    @Test
    fun `today used for highlighting comes from the injected clock`() {
        assertEquals(
            TODAY,
            fixture
                .createListViewModel()
                .uiState.value.today,
        )
    }
}
