package jp.co.yaz.kanuchi.domain.shift

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay

/**
 * 利用者が入力した勤務時間設定 (入力欄の文字列そのまま)。
 * [toShiftSettings] で形式と値の組み合わせを検証して [ShiftSettings] に変換する。
 *
 * - 時刻 ([startTime] / [endTime]): `HH:mm` 形式 ([TimeOfDay.parse])
 * - 時間数 ([breakHours] / [minHours] / [maxHours]): 0以上の数値、小数第2位まで ([Hours.parse])
 */
data class ShiftSettingsInput(
    val startTime: String,
    val endTime: String,
    val breakHours: String,
    val minHours: String,
    val maxHours: String,
) {
    /**
     * 入力を検証して [ShiftSettings] に変換する。条件を満たさない場合は、満たしていない条件をすべて持つ
     * [InvalidShiftSettingsException] で失敗する (形式が正しくない欄があっても、残りの欄どうしの条件も確認する)。
     */
    fun toShiftSettings(): Result<ShiftSettings> {
        val start = TimeOfDay.parse(startTime)
        val end = TimeOfDay.parse(endTime)
        val breakHoursValue = Hours.parse(breakHours)
        val minHoursValue = Hours.parse(minHours)
        val maxHoursValue = Hours.parse(maxHours)

        val violations =
            buildSet {
                if (start == null) add(ShiftSettingsViolation.START_TIME_INVALID_FORMAT)
                if (end == null) add(ShiftSettingsViolation.END_TIME_INVALID_FORMAT)
                if (breakHoursValue == null) add(ShiftSettingsViolation.BREAK_HOURS_INVALID_FORMAT)
                if (minHoursValue == null) add(ShiftSettingsViolation.MIN_HOURS_INVALID_FORMAT)
                if (maxHoursValue == null) add(ShiftSettingsViolation.MAX_HOURS_INVALID_FORMAT)
                addAll(ShiftSettings.violationsOf(start, end, breakHoursValue, minHoursValue, maxHoursValue))
            }
        if (violations.isNotEmpty()) return Result.failure(InvalidShiftSettingsException(violations))
        // 違反が無い = すべての欄の形式が正しい (null でない)
        return Result.success(
            ShiftSettings(
                startTime = checkNotNull(start),
                endTime = checkNotNull(end),
                breakHours = checkNotNull(breakHoursValue),
                minHours = checkNotNull(minHoursValue),
                maxHours = checkNotNull(maxHoursValue),
            ),
        )
    }

    companion object {
        /** 保存済みの設定を入力欄に表示する文字列に変換する。 */
        fun from(settings: ShiftSettings): ShiftSettingsInput =
            ShiftSettingsInput(
                startTime = settings.startTime.toString(),
                endTime = settings.endTime.toString(),
                breakHours = settings.breakHours.toString(),
                minHours = settings.minHours.toString(),
                maxHours = settings.maxHours.toString(),
            )
    }
}
