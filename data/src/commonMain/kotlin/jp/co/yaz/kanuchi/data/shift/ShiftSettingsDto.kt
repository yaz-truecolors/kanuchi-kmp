package jp.co.yaz.kanuchi.data.shift

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlin.math.roundToInt

/**
 * `shift_settings` テーブルの1行 (PostgREST のリクエスト・レスポンス)。
 *
 * - 時刻 (`time` 型) は PostgREST から `HH:mm:ss` 形式の文字列で返る。保存時は `HH:mm` 形式で送る
 * - 時間数 (`numeric(p, 2)` 型) は JSON の数値で受け渡しする
 */
@Serializable
internal data class ShiftSettingsDto(
    @SerialName("user_id") val userId: String,
    @SerialName("start_time") val startTime: String,
    @SerialName("end_time") val endTime: String,
    @SerialName("break_hours") val breakHours: Double,
    @SerialName("min_hours") val minHours: Double,
    @SerialName("max_hours") val maxHours: Double,
) {
    companion object {
        /** PostgREST で取得する列。テーブルに列が増えても、必要な列だけを取得する。 */
        val COLUMNS = listOf("user_id", "start_time", "end_time", "break_hours", "min_hours", "max_hours")
    }
}

internal fun ShiftSettingsDto.toDomain(): ShiftSettings =
    ShiftSettings(
        startTime = parseDbTime(startTime),
        endTime = parseDbTime(endTime),
        breakHours = hoursOf(breakHours),
        minHours = hoursOf(minHours),
        maxHours = hoursOf(maxHours),
    )

internal fun ShiftSettings.toDto(userId: String): ShiftSettingsDto =
    ShiftSettingsDto(
        userId = userId,
        startTime = startTime.toString(),
        endTime = endTime.toString(),
        breakHours = breakHours.toDbValue(),
        minHours = minHours.toDbValue(),
        maxHours = maxHours.toDbValue(),
    )

/**
 * DB の `time` 型の値 (`HH:mm:ss`、秒の小数部が付くこともある) を [TimeOfDay] に変換する。
 * アプリは分単位でしか扱わないため、秒以下は切り捨てる (アプリから保存した値に秒は付かない)。
 */
private fun parseDbTime(value: String): TimeOfDay {
    val parts = value.split(":")
    require(parts.size >= 2) { "unexpected time value: $value" }
    return TimeOfDay.of(hour = parts[0].toInt(), minute = parts[1].toInt())
}

// numeric(p, 2) の値は 0.01 単位なので、2進小数の誤差は四捨五入で吸収する
private fun hoursOf(value: Double): Hours = Hours.ofHundredths((value * HUNDRED).roundToInt())

private fun Hours.toDbValue(): Double = hundredths / HUNDRED.toDouble()

private const val HUNDRED = 100
