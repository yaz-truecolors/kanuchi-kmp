package jp.co.yaz.kanuchi.domain.shift

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ShiftSettingsUseCasesTest {
    private val repository = FakeShiftSettingsRepository()

    @Test
    fun `default settings are returned when nothing is saved yet`() =
        runTest {
            assertEquals(ShiftSettings.DEFAULT, GetShiftSettingsUseCase(repository)().getOrThrow())
        }

    @Test
    fun `saved settings are returned`() =
        runTest {
            val saved = ShiftSettings.DEFAULT.copy(startTime = TimeOfDay.of(9, 0))
            repository.getResult = Result.success(saved)

            assertEquals(saved, GetShiftSettingsUseCase(repository)().getOrThrow())
        }

    @Test
    fun `failure of loading is propagated as-is`() =
        runTest {
            repository.getResult = Result.failure(GenericDataFailureException())

            assertIs<GenericDataFailureException>(GetShiftSettingsUseCase(repository)().exceptionOrNull())
        }

    @Test
    fun `valid input is saved`() =
        runTest {
            val input = ShiftSettingsInput.from(ShiftSettings.DEFAULT).copy(startTime = "10:00")

            val saved = SaveShiftSettingsUseCase(repository)(input).getOrThrow()

            assertEquals(TimeOfDay.of(10, 0), saved.startTime)
            assertEquals(listOf(saved), repository.savedSettings)
        }

    @Test
    fun `invalid input is not saved`() =
        runTest {
            val input = ShiftSettingsInput.from(ShiftSettings.DEFAULT).copy(endTime = "08:00")

            val error = assertIs<InvalidShiftSettingsException>(SaveShiftSettingsUseCase(repository)(input).exceptionOrNull())

            assertEquals(setOf(ShiftSettingsViolation.END_TIME_NOT_AFTER_START_TIME), error.violations)
            assertTrue(repository.savedSettings.isEmpty())
        }

    @Test
    fun `failure of saving is propagated as-is`() =
        runTest {
            repository.saveResult = Result.failure(GenericDataFailureException())

            val result = SaveShiftSettingsUseCase(repository)(ShiftSettingsInput.from(ShiftSettings.DEFAULT))

            assertIs<GenericDataFailureException>(result.exceptionOrNull())
        }
}
