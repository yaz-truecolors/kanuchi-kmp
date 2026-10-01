package jp.co.yaz.kanuchi.presentation.settings

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.profile.DisplayNameViolation
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import jp.co.yaz.kanuchi.domain.profile.UpdateDisplayNameUseCase
import jp.co.yaz.kanuchi.domain.shift.GetShiftSettingsUseCase
import jp.co.yaz.kanuchi.domain.shift.SaveShiftSettingsUseCase
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsField
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsInput
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsViolation
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeShiftSettingsRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SettingsViewModelTest : MainDispatcherTest() {
    private val profileRepository = FakeProfileRepository()
    private val shiftSettingsRepository = FakeShiftSettingsRepository()

    private fun createViewModel() =
        SettingsViewModel(
            GetCurrentUserProfileUseCase(profileRepository),
            UpdateDisplayNameUseCase(profileRepository),
            GetShiftSettingsUseCase(shiftSettingsRepository),
            SaveShiftSettingsUseCase(shiftSettingsRepository),
        )

    @Test
    fun `display name and default shift settings are shown when nothing is saved yet`() {
        val viewModel = createViewModel()

        val state = viewModel.uiState.value
        assertTrue(state.showsForm)
        assertEquals(FakeProfileRepository.MEMBER.displayName, state.displayName)
        assertEquals(ShiftSettingsInput.from(ShiftSettings.DEFAULT), state.shiftSettings)
    }

    @Test
    fun `saved shift settings are shown`() {
        val saved = ShiftSettings.DEFAULT.copy(startTime = TimeOfDay.of(9, 0))
        shiftSettingsRepository.getResult = Result.success(saved)

        val viewModel = createViewModel()

        assertEquals("09:00", viewModel.uiState.value.shiftSettings.startTime)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        shiftSettingsRepository.getResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertFalse(viewModel.uiState.value.showsForm)

        shiftSettingsRepository.getResult = Result.success(null)
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertTrue(viewModel.uiState.value.showsForm)
        assertEquals(2, shiftSettingsRepository.getCallCount)
    }

    @Test
    fun `profile load failure is also shown as a load failure`() {
        profileRepository.currentUserProfileResult = Result.failure(GenericDataFailureException())

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
    }

    @Test
    fun `display name is trimmed and saved`() {
        val viewModel = createViewModel()

        viewModel.onDisplayNameChanged("  山田 太郎 ")
        viewModel.onSaveDisplayNameClicked()

        val state = viewModel.uiState.value
        assertEquals(listOf("山田 太郎"), profileRepository.updatedDisplayNames)
        assertEquals("山田 太郎", state.displayName)
        assertTrue(state.displayNameSaved)
        assertFalse(state.isSavingDisplayName)
    }

    @Test
    fun `invalid display name is shown and not saved`() {
        val viewModel = createViewModel()

        viewModel.onDisplayNameChanged(" ")
        viewModel.onSaveDisplayNameClicked()

        assertEquals(DisplayNameViolation.BLANK, viewModel.uiState.value.displayNameViolation)
        assertFalse(viewModel.uiState.value.displayNameSaved)
        assertTrue(profileRepository.updatedDisplayNames.isEmpty())

        viewModel.onDisplayNameChanged("山田")

        assertNull(viewModel.uiState.value.displayNameViolation)
    }

    @Test
    fun `display name save failure is shown`() {
        profileRepository.updateDisplayNameResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        viewModel.onSaveDisplayNameClicked()

        assertTrue(viewModel.uiState.value.displayNameSaveFailed)
        assertFalse(viewModel.uiState.value.isSavingDisplayName)
    }

    @Test
    fun `shift settings are saved and normalized`() {
        val viewModel = createViewModel()

        viewModel.onShiftSettingsChanged(
            viewModel.uiState.value.shiftSettings
                .copy(startTime = "9:00", breakHours = "0.50"),
        )
        viewModel.onSaveShiftSettingsClicked()

        val state = viewModel.uiState.value
        assertEquals(TimeOfDay.of(9, 0), shiftSettingsRepository.savedSettings.single().startTime)
        assertEquals("09:00", state.shiftSettings.startTime)
        assertEquals("0.5", state.shiftSettings.breakHours)
        assertTrue(state.shiftSettingsSaved)
    }

    @Test
    fun `invalid shift settings are shown on each field and not saved`() {
        val viewModel = createViewModel()

        viewModel.onShiftSettingsChanged(
            viewModel.uiState.value.shiftSettings
                .copy(endTime = "09:00", breakHours = "abc", minHours = "200"),
        )
        viewModel.onSaveShiftSettingsClicked()

        val state = viewModel.uiState.value
        assertTrue(shiftSettingsRepository.savedSettings.isEmpty())
        assertFalse(state.shiftSettingsSaved)
        assertNull(state.shiftSettingsViolationOf(ShiftSettingsField.START_TIME))
        assertEquals(ShiftSettingsViolation.END_TIME_NOT_AFTER_START_TIME, state.shiftSettingsViolationOf(ShiftSettingsField.END_TIME))
        assertEquals(ShiftSettingsViolation.BREAK_HOURS_INVALID_FORMAT, state.shiftSettingsViolationOf(ShiftSettingsField.BREAK_HOURS))
        assertEquals(ShiftSettingsViolation.MIN_HOURS_EXCEEDS_MAX_HOURS, state.shiftSettingsViolationOf(ShiftSettingsField.MAX_HOURS))

        viewModel.onShiftSettingsChanged(state.shiftSettings.copy(endTime = "18:30"))

        assertTrue(
            viewModel.uiState.value.shiftSettingsViolations
                .isEmpty(),
        )
    }

    @Test
    fun `shift settings save failure is shown and the input is kept`() {
        shiftSettingsRepository.saveResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        viewModel.onShiftSettingsChanged(
            viewModel.uiState.value.shiftSettings
                .copy(minHours = "150"),
        )
        viewModel.onSaveShiftSettingsClicked()

        assertTrue(viewModel.uiState.value.shiftSettingsSaveFailed)
        assertFalse(viewModel.uiState.value.isSavingShiftSettings)
        assertEquals("150", viewModel.uiState.value.shiftSettings.minHours)
    }

    @Test
    fun `shift settings are not saved twice while saving`() {
        val gate = CompletableDeferred<Unit>()
        shiftSettingsRepository.saveGate = gate
        val viewModel = createViewModel()

        viewModel.onSaveShiftSettingsClicked()
        viewModel.onSaveShiftSettingsClicked()

        assertTrue(viewModel.uiState.value.isSavingShiftSettings)
        assertEquals(1, shiftSettingsRepository.savedSettings.size)

        gate.complete(Unit)

        assertFalse(viewModel.uiState.value.isSavingShiftSettings)
        assertTrue(viewModel.uiState.value.shiftSettingsSaved)
    }
}
