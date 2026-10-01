package jp.co.yaz.kanuchi.presentation.dailyinput

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.work.AllocationHoursViolation
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import jp.co.yaz.kanuchi.domain.work.WorkRecordField
import jp.co.yaz.kanuchi.domain.work.WorkRecordViolation
import jp.co.yaz.kanuchi.presentation.dailyinput.DailyInputTestFixture.Companion.USER_ID
import jp.co.yaz.kanuchi.presentation.testing.FakeProjectRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.datetime.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class DailyInputDayViewModelTest : MainDispatcherTest() {
    private val fixture = DailyInputTestFixture()
    private val date = LocalDate(2026, 10, 1)
    private val projectId = FakeProjectRepository.ACTIVE.id

    @Test
    fun `saved record is shown in the form with assigned active projects`() {
        fixture.workRecordRepository.records[USER_ID] =
            listOf(WorkRecord(date, clockIn = TimeOfDay.of(9, 0), note = "メモ", allocations = mapOf(projectId to Hours.ofHundredths(750))))

        val state = fixture.createDayViewModel(date).uiState.value

        assertTrue(state.showsForm)
        assertTrue(state.hasSavedRecord)
        assertEquals("09:00", state.input.clockIn)
        assertEquals("", state.input.clockOut)
        assertEquals("メモ", state.input.note)
        assertEquals("7.5", state.input.allocations[projectId])
        // 無効な案件 (INACTIVE) は、この日に配分が無いため表示しない
        assertEquals(listOf(projectId), state.entry?.projects?.map { it.project.id })
    }

    @Test
    fun `day without a record shows a blank form without delete`() {
        val state = fixture.createDayViewModel(date).uiState.value

        assertTrue(state.showsForm)
        assertFalse(state.hasSavedRecord)
        assertEquals("", state.input.clockIn)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        fixture.projectRepository.projectsResult = Result.failure(GenericDataFailureException())
        val viewModel = fixture.createDayViewModel(date)

        assertTrue(viewModel.uiState.value.loadFailed)
        assertFalse(viewModel.uiState.value.showsForm)

        fixture.projectRepository.projectsResult = Result.success(listOf(FakeProjectRepository.ACTIVE))
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertTrue(viewModel.uiState.value.showsForm)
    }

    @Test
    fun `working hours, allocated hours and balance are recalculated while typing`() {
        val viewModel = fixture.createDayViewModel(date)
        assertEquals(
            Hours.ofHundredths(800),
            viewModel.uiState.value.preview
                ?.workingHours
                ?.hours,
        )

        viewModel.onClockOutChanged("19:30")
        viewModel.onAllocationChanged(projectId, "8.5")

        val preview = viewModel.uiState.value.preview
        assertEquals(Hours.ofHundredths(900), preview?.workingHours?.hours)
        assertEquals(Hours.ofHundredths(850), preview?.allocatedHours)
        assertEquals(SignedHours.ofHundredths(50), preview?.balance)

        viewModel.onClockInChanged("9:")
        assertNull(
            viewModel.uiState.value.preview
                ?.workingHours,
        )
        assertNull(
            viewModel.uiState.value.preview
                ?.balance,
        )
    }

    @Test
    fun `fill shift puts the shift settings into the time fields`() {
        val viewModel = fixture.createDayViewModel(date)

        viewModel.onFillShiftClicked()

        val input = viewModel.uiState.value.input
        assertEquals("09:30", input.clockIn)
        assertEquals("18:30", input.clockOut)
        assertEquals("1", input.breakHours)
    }

    @Test
    fun `valid input is saved and the screen finishes even with an imbalance`() {
        val viewModel = fixture.createDayViewModel(date)
        viewModel.onClockInChanged("9:00")
        viewModel.onAllocationChanged(projectId, "7")
        viewModel.onNoteChanged("  打合せ  ")

        viewModel.onSaveClicked()

        assertTrue(viewModel.uiState.value.isFinished)
        assertEquals(
            listOf(WorkRecord(date, clockIn = TimeOfDay.of(9, 0), note = "打合せ", allocations = mapOf(projectId to Hours.ofHundredths(700)))),
            fixture.workRecordRepository.savedRecords,
        )
    }

    @Test
    fun `saving state is shown until saving finishes`() {
        val gate = CompletableDeferred<Unit>()
        fixture.workRecordRepository.gate = gate
        val viewModel = fixture.createDayViewModel(date)

        viewModel.onSaveClicked()
        assertTrue(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isBusy)
        // 保存中は入力を受け付けない
        viewModel.onNoteChanged("x")
        assertEquals("", viewModel.uiState.value.input.note)

        gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isSaving)
        assertTrue(viewModel.uiState.value.isFinished)
    }

    @Test
    fun `invalid input is not saved and violations are shown until the input changes`() {
        val viewModel = fixture.createDayViewModel(date)
        viewModel.onClockInChanged("25:00")
        viewModel.onAllocationChanged(projectId, "abc")

        viewModel.onSaveClicked()

        val state = viewModel.uiState.value
        assertFalse(state.isFinished)
        assertEquals(WorkRecordViolation.CLOCK_IN_INVALID_FORMAT, state.violationOf(WorkRecordField.CLOCK_IN))
        assertEquals(mapOf(projectId to AllocationHoursViolation.INVALID_FORMAT), state.allocationViolations)
        assertTrue(fixture.workRecordRepository.savedRecords.isEmpty())

        viewModel.onClockInChanged("9:00")
        assertTrue(
            viewModel.uiState.value.violations
                .isEmpty(),
        )
        assertTrue(
            viewModel.uiState.value.allocationViolations
                .isEmpty(),
        )
    }

    @Test
    fun `negative working hours are rejected`() {
        val viewModel = fixture.createDayViewModel(date)
        viewModel.onBreakHoursChanged("10")

        viewModel.onSaveClicked()

        assertEquals(WorkRecordViolation.WORKING_HOURS_NEGATIVE, viewModel.uiState.value.violationOf(WorkRecordField.WORKING_HOURS))
        assertTrue(fixture.workRecordRepository.savedRecords.isEmpty())
    }

    @Test
    fun `save failure is shown and the screen stays`() {
        fixture.workRecordRepository.saveResult = Result.failure(GenericDataFailureException())
        val viewModel = fixture.createDayViewModel(date)

        viewModel.onSaveClicked()

        assertTrue(viewModel.uiState.value.saveFailed)
        assertFalse(viewModel.uiState.value.isFinished)

        viewModel.onNoteChanged("a")
        assertFalse(viewModel.uiState.value.saveFailed)
    }

    @Test
    fun `flagged day saves only the flag and note`() {
        val viewModel = fixture.createDayViewModel(date)
        viewModel.onClockInChanged("9:00")
        viewModel.onAllocationChanged(projectId, "8")
        viewModel.onFlagChanged(DayFlag.ABSENCE)

        assertFalse(viewModel.uiState.value.input.acceptsWorkInput)
        assertEquals(
            Hours.ZERO,
            viewModel.uiState.value.preview
                ?.workingHours
                ?.hours,
        )

        viewModel.onSaveClicked()

        assertEquals(listOf(WorkRecord(date, flag = DayFlag.ABSENCE)), fixture.workRecordRepository.savedRecords)
    }

    @Test
    fun `removing the flag brings back the work input`() {
        val viewModel = fixture.createDayViewModel(date)
        viewModel.onClockInChanged("9:00")
        viewModel.onFlagChanged(DayFlag.VACATION)
        viewModel.onFlagChanged(null)

        assertEquals("9:00", viewModel.uiState.value.input.clockIn)
        assertTrue(viewModel.uiState.value.input.acceptsWorkInput)
    }

    @Test
    fun `record is deleted after confirmation`() {
        fixture.workRecordRepository.records[USER_ID] = listOf(WorkRecord(date, note = "消す"))
        val viewModel = fixture.createDayViewModel(date)

        viewModel.onDeleteClicked()
        assertTrue(viewModel.uiState.value.showsDeleteConfirmation)
        assertTrue(fixture.workRecordRepository.deletedDates.isEmpty())

        viewModel.onDeleteConfirmed()

        assertFalse(viewModel.uiState.value.showsDeleteConfirmation)
        assertTrue(viewModel.uiState.value.isFinished)
        assertEquals(listOf(date), fixture.workRecordRepository.deletedDates)
    }

    @Test
    fun `delete can be cancelled`() {
        fixture.workRecordRepository.records[USER_ID] = listOf(WorkRecord(date, note = "消す"))
        val viewModel = fixture.createDayViewModel(date)

        viewModel.onDeleteClicked()
        viewModel.onDeleteDismissed()

        assertFalse(viewModel.uiState.value.showsDeleteConfirmation)
        assertTrue(fixture.workRecordRepository.deletedDates.isEmpty())
    }

    @Test
    fun `delete failure is shown and the screen stays`() {
        fixture.workRecordRepository.records[USER_ID] = listOf(WorkRecord(date, note = "消す"))
        fixture.workRecordRepository.deleteResult = Result.failure(GenericDataFailureException())
        val viewModel = fixture.createDayViewModel(date)

        viewModel.onDeleteClicked()
        viewModel.onDeleteConfirmed()

        assertTrue(viewModel.uiState.value.deleteFailed)
        assertFalse(viewModel.uiState.value.isFinished)
    }

    @Test
    fun `delete is not offered when nothing is saved`() {
        val viewModel = fixture.createDayViewModel(date)

        viewModel.onDeleteClicked()

        assertFalse(viewModel.uiState.value.showsDeleteConfirmation)
    }
}
