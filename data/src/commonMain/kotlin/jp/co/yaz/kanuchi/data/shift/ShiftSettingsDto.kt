package jp.co.yaz.kanuchi.data.shift

import jp.co.yaz.kanuchi.data.common.hoursOf
import jp.co.yaz.kanuchi.data.common.parseDbTime
import jp.co.yaz.kanuchi.data.common.toDbValue
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

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
