package jp.co.yaz.kanuchi.presentation.holiday

import jp.co.yaz.kanuchi.domain.calendar.AddCompanyHolidaysUseCase
import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayInput
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayViolation
import jp.co.yaz.kanuchi.domain.calendar.DeleteCompanyHolidayUseCase
import jp.co.yaz.kanuchi.domain.calendar.GetCompanyHolidaysOfYearUseCase
import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.presentation.testing.FakeCompanyHolidayRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Instant

class CompanyHolidaysViewModelTest : MainDispatcherTest() {
    private val repository =
        FakeCompanyHolidayRepository().apply {
            holidays += HOLIDAY_2026
            holidays += HOLIDAY_2027
        }

    private fun createViewModel(now: String = "2026-10-01T03:00:00Z") =
        CompanyHolidaysViewModel(
            GetTodayUseCase(FixedClock(now)),
            GetCompanyHolidaysOfYearUseCase(repository),
            AddCompanyHolidaysUseCase(repository),
            DeleteCompanyHolidayUseCase(repository),
        )

    @Test
    fun `holidays of this year are loaded with national holidays`() {
        val viewModel = createViewModel()
        val state = viewModel.uiState.value

        assertEquals(2026, state.year)
        assertEquals(listOf(HOLIDAY_2026), state.companyHolidays)
        assertEquals(NationalHolidayItem(LocalDate(2026, 1, 1), "元日"), state.nationalHolidays.first())
        assertTrue(state.nationalHolidays.all { it.date.year == 2026 })
        assertFalse(state.isLoading)
        assertEquals(listOf(LocalDate(2026, 1, 1) to LocalDate(2026, 12, 31)), repository.requestedRanges)
    }

    @Test
    fun `this year is decided in Japan time`() {
        // UTC 2026-12-31 15:00 は日本時間 2027-01-01 0:00
        assertEquals(2027, createViewModel(now = "2026-12-31T15:00:00Z").uiState.value.year)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        repository.getResult = CompletableDeferred(Result.failure(GenericDataFailureException()))
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertFalse(viewModel.uiState.value.canEdit)

        repository.getResult = null
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(listOf(HOLIDAY_2026), viewModel.uiState.value.companyHolidays)
    }

    @Test
    fun `year can be switched to the previous and next year`() {
        val viewModel = createViewModel()

        viewModel.onNextYearClicked()

        assertEquals(2027, viewModel.uiState.value.year)
        assertEquals(listOf(HOLIDAY_2027), viewModel.uiState.value.companyHolidays)
        assertTrue(
            viewModel.uiState.value.nationalHolidays
                .all { it.date.year == 2027 },
        )

        viewModel.onPreviousYearClicked()
        viewModel.onPreviousYearClicked()

        assertEquals(2025, viewModel.uiState.value.year)
        assertTrue(
            viewModel.uiState.value.companyHolidays
                .isEmpty(),
        )
    }

    @Test
    fun `year is limited to the supported years`() {
        val viewModel = createViewModel(now = "2100-06-01T00:00:00Z")

        assertEquals(2099, viewModel.uiState.value.year)
        assertFalse(viewModel.uiState.value.canGoToNextYear)

        viewModel.onNextYearClicked()

        assertEquals(2099, viewModel.uiState.value.year)
    }

    @Test
    fun `switching the year while loading ignores the previous result`() {
        val firstLoad = CompletableDeferred<Result<List<CompanyHoliday>>>()
        repository.getResult = firstLoad
        val viewModel = createViewModel()
        assertTrue(viewModel.uiState.value.isLoading)

        repository.getResult = null
        viewModel.onNextYearClicked()
        firstLoad.complete(Result.success(listOf(HOLIDAY_2026)))

        assertEquals(2027, viewModel.uiState.value.year)
        assertEquals(listOf(HOLIDAY_2027), viewModel.uiState.value.companyHolidays)
        assertFalse(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `holiday cannot be added while loading`() {
        val load = CompletableDeferred<Result<List<CompanyHoliday>>>()
        repository.getResult = load
        val viewModel = createViewModel()

        viewModel.onInputChanged(CompanyHolidayInput("2026-08-14", "", "夏季休業"))
        viewModel.onAddClicked()

        assertTrue(repository.added.isEmpty())
        assertFalse(viewModel.uiState.value.isAdding)
    }

    @Test
    fun `added holiday is inserted in date order and the input is cleared`() {
        val viewModel = createViewModel()
        val result = CompletableDeferred<Result<CompanyHoliday>>()
        repository.addResult = result

        viewModel.onInputChanged(CompanyHolidayInput("2026-08-14", "", " 夏季休業 "))
        viewModel.onAddClicked()

        assertTrue(viewModel.uiState.value.isAdding)
        assertFalse(viewModel.uiState.value.canGoToNextYear)
        assertFalse(viewModel.uiState.value.canGoToPreviousYear)

        val added = CompanyHoliday(LocalDate(2026, 8, 14), "夏季休業")
        result.complete(Result.success(added))

        val state = viewModel.uiState.value
        assertFalse(state.isAdding)
        assertEquals(listOf(added, HOLIDAY_2026), state.companyHolidays)
        assertEquals(CompanyHolidaysUiState.EMPTY_INPUT, state.input)
        assertEquals(1, state.addedDayCount)
    }

    @Test
    fun `period across years adds every day and shows only this year's days`() {
        repository.holidays.remove(HOLIDAY_2026)
        repository.holidays.remove(HOLIDAY_2027)
        val viewModel = createViewModel()

        viewModel.onInputChanged(CompanyHolidayInput("2026-12-29", "2027-01-03", "年末年始休業"))
        viewModel.onAddClicked()

        assertEquals(6, repository.added.size)
        assertEquals(
            listOf(LocalDate(2026, 12, 29), LocalDate(2026, 12, 30), LocalDate(2026, 12, 31)),
            viewModel.uiState.value.companyHolidays
                .map { it.date },
        )
        assertEquals(6, viewModel.uiState.value.addedDayCount)
    }

    @Test
    fun `invalid input is shown and nothing is added`() {
        val viewModel = createViewModel()

        viewModel.onInputChanged(CompanyHolidayInput("2026-02-30", "", "休業"))
        viewModel.onAddClicked()

        assertEquals(setOf(CompanyHolidayViolation.START_DATE_INVALID), viewModel.uiState.value.inputViolations)
        assertTrue(repository.added.isEmpty())

        viewModel.onInputChanged(CompanyHolidayInput("2026-02-28", "", "休業"))

        assertTrue(
            viewModel.uiState.value.inputViolations
                .isEmpty(),
        )
    }

    @Test
    fun `duplicate date is shown, the input is kept and the list is reloaded`() {
        val viewModel = createViewModel()
        val input = CompanyHolidayInput("2026-12-30", "2026-12-31", "年末休業")

        viewModel.onInputChanged(input)
        viewModel.onAddClicked()

        val state = viewModel.uiState.value
        assertEquals(CompanyHolidayAddError.DUPLICATE, state.addError)
        assertEquals(input, state.input)
        assertTrue(repository.added.isEmpty())
        // 初回の読み込み・追加前の確認・失敗後の読み込み直し
        assertEquals(3, repository.requestedRanges.size)
        assertEquals(listOf(HOLIDAY_2026), state.companyHolidays)
    }

    @Test
    fun `save failure is shown and the list is reloaded`() {
        val viewModel = createViewModel()
        repository.addResult = CompletableDeferred(Result.failure(GenericDataFailureException()))

        viewModel.onInputChanged(CompanyHolidayInput("2026-08-14", "", "夏季休業"))
        viewModel.onAddClicked()

        assertEquals(CompanyHolidayAddError.SAVE_FAILED, viewModel.uiState.value.addError)
        assertEquals(3, repository.requestedRanges.size)

        viewModel.onInputChanged(CompanyHolidayInput("2026-08-14", "", "夏季休業 "))

        assertNull(viewModel.uiState.value.addError)
    }

    @Test
    fun `holiday is deleted after confirmation`() {
        val viewModel = createViewModel()
        val result = CompletableDeferred<Result<Unit>>()
        repository.deleteResult = result

        viewModel.onDeleteClicked(HOLIDAY_2026)
        assertEquals(HOLIDAY_2026, viewModel.uiState.value.deleteTarget)
        assertTrue(repository.deletedDates.isEmpty())

        viewModel.onDeleteConfirmed()

        assertNull(viewModel.uiState.value.deleteTarget)
        assertEquals(HOLIDAY_2026.date, viewModel.uiState.value.deletingDate)
        assertFalse(viewModel.uiState.value.canEdit)
        assertEquals(listOf(HOLIDAY_2026.date), repository.deletedDates)

        result.complete(Result.success(Unit))

        assertNull(viewModel.uiState.value.deletingDate)
        assertTrue(
            viewModel.uiState.value.companyHolidays
                .isEmpty(),
        )
    }

    @Test
    fun `delete is cancelled when the confirmation is dismissed`() {
        val viewModel = createViewModel()

        viewModel.onDeleteClicked(HOLIDAY_2026)
        viewModel.onDeleteDismissed()
        viewModel.onDeleteConfirmed()

        assertNull(viewModel.uiState.value.deleteTarget)
        assertTrue(repository.deletedDates.isEmpty())
        assertEquals(listOf(HOLIDAY_2026), viewModel.uiState.value.companyHolidays)
    }

    @Test
    fun `delete failure is shown and the list is reloaded`() {
        val viewModel = createViewModel()
        repository.deleteResult = CompletableDeferred(Result.failure(GenericDataFailureException()))
        // 他の admin が先に削除していた場合を想定し、再読み込みでは一覧から消えている
        repository.holidays.clear()

        viewModel.onDeleteClicked(HOLIDAY_2026)
        viewModel.onDeleteConfirmed()

        assertEquals(HOLIDAY_2026, viewModel.uiState.value.deleteFailedHoliday)
        assertNull(viewModel.uiState.value.deletingDate)
        assertEquals(emptyList(), viewModel.uiState.value.companyHolidays)
    }

    private class FixedClock(
        instant: String,
    ) : Clock {
        private val instant = Instant.parse(instant)

        override fun now(): Instant = instant
    }

    private companion object {
        val HOLIDAY_2026 = CompanyHoliday(LocalDate(2026, 12, 31), "大晦日")
        val HOLIDAY_2027 = CompanyHoliday(LocalDate(2027, 1, 4), "年始休業")
    }
}
