package jp.co.yaz.kanuchi.data.work

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * 複数のユーザーの記録をまとめて取得するときの `work_records` テーブルの1行 ([WorkRecordDto] にユーザーIDを加えたもの)。
 * 値の形式は [WorkRecordDto] と同じ。
 */
@Serializable
internal data class UserWorkRecordDto(
    @SerialName("user_id") val userId: String,
    @SerialName("work_date") val workDate: String,
    @SerialName("clock_in") val clockIn: String? = null,
    @SerialName("clock_out") val clockOut: String? = null,
    @SerialName("break_hours") val breakHours: Double? = null,
    val flag: String? = null,
    val note: String? = null,
    val allocations: List<AllocationDto> = emptyList(),
) {
    fun toWorkRecordDto(): WorkRecordDto = WorkRecordDto(workDate, clockIn, clockOut, breakHours, flag, note, allocations)

    companion object {
        /** PostgREST で取得する列。 */
        const val COLUMNS = "user_id, ${WorkRecordDto.COLUMNS}"
    }
}

/** ユーザーIDごとの記録 (日付の順) に変換する。 */
internal fun List<UserWorkRecordDto>.toWorkRecordsByUserId() =
    groupBy({ it.userId }, { it.toWorkRecordDto().toDomain() }).mapValues { (_, records) -> records.sortedBy { it.date } }
