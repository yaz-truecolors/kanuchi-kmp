package jp.co.yaz.kanuchi.domain.shift

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs

class ShiftSettingsInputTest {
    private val validInput =
        ShiftSettingsInput(startTime = "9:00", endTime = "17:45", breakHours = "0.75", minHours = "120.5", maxHours = "160")

    private fun violationsOf(input: ShiftSettingsInput): Set<ShiftSettingsViolation> =
        assertIs<InvalidShiftSettingsException>(input.toShiftSettings().exceptionOrNull()).violations

    @Test
    fun `valid input is converted to shift settings`() {
        val settings = validInput.toShiftSettings().getOrThrow()

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
    fun `boundary values allowed by the db are accepted`() {
        val input = validInput.copy(breakHours = "0", minHours = "9999.99", maxHours = "9999.99")

        assertEquals(
            input.minHours,
            input
                .toShiftSettings()
                .getOrThrow()
                .minHours
                .toString(),
        )
        assertEquals(
            "99.99",
            validInput
                .copy(breakHours = "99.99")
                .toShiftSettings()
                .getOrThrow()
                .breakHours
                .toString(),
        )
        assertEquals(
            "0",
            validInput
                .copy(minHours = "0", maxHours = "0")
                .toShiftSettings()
                .getOrThrow()
                .maxHours
                .toString(),
        )
    }

    @Test
    fun `invalid formats are reported for each field`() {
        val input = ShiftSettingsInput(startTime = "9時", endTime = "", breakHours = "-1", minHours = "1.234", maxHours = "abc")

        assertEquals(
            setOf(
                ShiftSettingsViolation.START_TIME_INVALID_FORMAT,
                ShiftSettingsViolation.END_TIME_INVALID_FORMAT,
                ShiftSettingsViolation.BREAK_HOURS_INVALID_FORMAT,
                ShiftSettingsViolation.MIN_HOURS_INVALID_FORMAT,
                ShiftSettingsViolation.MAX_HOURS_INVALID_FORMAT,
            ),
            violationsOf(input),
        )
    }

    @Test
    fun `end time must be after start time`() {
        assertEquals(
            setOf(ShiftSettingsViolation.END_TIME_NOT_AFTER_START_TIME),
            violationsOf(validInput.copy(startTime = "18:30", endTime = "18:30")),
        )
        assertEquals(
            setOf(ShiftSettingsViolation.END_TIME_NOT_AFTER_START_TIME),
            violationsOf(validInput.copy(startTime = "18:30", endTime = "09:30")),
        )
    }

    @Test
    fun `min hours must not exceed max hours`() {
        assertEquals(
            setOf(ShiftSettingsViolation.MIN_HOURS_EXCEEDS_MAX_HOURS),
            violationsOf(validInput.copy(minHours = "180.01", maxHours = "180")),
        )
    }

    @Test
    fun `values exceeding the db column types are rejected`() {
        assertEquals(setOf(ShiftSettingsViolation.BREAK_HOURS_TOO_LARGE), violationsOf(validInput.copy(breakHours = "100")))
        assertEquals(
            setOf(ShiftSettingsViolation.MIN_HOURS_TOO_LARGE, ShiftSettingsViolation.MAX_HOURS_TOO_LARGE),
            violationsOf(validInput.copy(minHours = "10000", maxHours = "10000")),
        )
    }

    @Test
    fun `combination of valid fields is checked even if another field has an invalid format`() {
        val input = validInput.copy(startTime = "18:00", endTime = "09:00", breakHours = "x")

        assertEquals(
            setOf(ShiftSettingsViolation.BREAK_HOURS_INVALID_FORMAT, ShiftSettingsViolation.END_TIME_NOT_AFTER_START_TIME),
            violationsOf(input),
        )
    }

    @Test
    fun `shift settings violating the constraints cannot be constructed`() {
        assertFailsWith<IllegalArgumentException> {
            ShiftSettings.DEFAULT.copy(endTime = ShiftSettings.DEFAULT.startTime)
        }
        assertFailsWith<IllegalArgumentException> {
            ShiftSettings.DEFAULT.copy(minHours = Hours.ofHundredths(20_000))
        }
    }

    @Test
    fun `default settings match the db column defaults and are formatted for input fields`() {
        assertEquals(
            ShiftSettingsInput(startTime = "09:30", endTime = "18:30", breakHours = "1", minHours = "140", maxHours = "180"),
            ShiftSettingsInput.from(ShiftSettings.DEFAULT),
        )
    }

    @Test
    fun `formatted settings are parsed back to the same settings`() {
        val settings = validInput.toShiftSettings().getOrThrow()

        assertEquals(settings, ShiftSettingsInput.from(settings).toShiftSettings().getOrThrow())
    }

    @Test
    fun `violations are shown on the related field`() {
        assertEquals(ShiftSettingsField.END_TIME, ShiftSettingsViolation.END_TIME_NOT_AFTER_START_TIME.field)
        assertEquals(ShiftSettingsField.MAX_HOURS, ShiftSettingsViolation.MIN_HOURS_EXCEEDS_MAX_HOURS.field)
    }
}
