package jp.co.yaz.kanuchi.data.work

import jp.co.yaz.kanuchi.data.common.hoursOf
import jp.co.yaz.kanuchi.data.common.parseDbTime
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `work_records` テーブルの1行と、その日の案件への配分 (`allocations` テーブルの行) (PostgREST のレスポンス)。
 * 配分は外部キー (`allocations.work_record_id`) を使った埋め込み (resource embedding) で、同じリクエストで取得する。
 *
 * - 日付 (`date` 型) は `yyyy-MM-dd` 形式の文字列で返る
 * - 時刻 (`time` 型)・時間数 (`numeric` 型) の扱いは `data/common/DbValues.kt` を参照
 */
@Serializable
internal data class WorkRecordDto(
    @SerialName("work_date") val workDate: String,
    @SerialName("clock_in") val clockIn: String? = null,
    @SerialName("clock_out") val clockOut: String? = null,
    @SerialName("break_hours") val breakHours: Double? = null,
    val flag: String? = null,
    val note: String? = null,
    val allocations: List<AllocationDto> = emptyList(),
) {
    companion object {
        /** PostgREST で取得する列 (`allocations(...)` は埋め込みで取得する配分の列)。 */
        const val COLUMNS = "work_date, clock_in, clock_out, break_hours, flag, note, allocations(project_id, hours)"
    }
}

/** `allocations` テーブルの1行 (日×案件の配分。[WorkRecordDto] に埋め込んで取得する)。 */
@Serializable
internal data class AllocationDto(
    @SerialName("project_id") val projectId: String,
    val hours: Double,
)

internal fun WorkRecordDto.toDomain(): WorkRecord =
    WorkRecord(
        date = LocalDate.parse(workDate),
        clockIn = clockIn?.let(::parseDbTime),
        clockOut = clockOut?.let(::parseDbTime),
        breakHours = breakHours?.let(::hoursOf),
        flag = flag?.let(::dayFlagOf),
        note = note,
        allocations = allocations.associate { it.projectId to hoursOf(it.hours) },
    )

/** [DayFlag] の DB の値 (`work_records.flag`)。「休」は `holiday`、「欠」は `absence`。 */
internal fun DayFlag.toDbValue(): String =
    when (this) {
        DayFlag.VACATION -> DB_FLAG_VACATION
        DayFlag.ABSENCE -> DB_FLAG_ABSENCE
    }

/** DB の値 (`work_records.flag`) を [DayFlag] に変換する。想定外の値なら [IllegalArgumentException] を投げる。 */
internal fun dayFlagOf(value: String): DayFlag =
    when (value) {
        DB_FLAG_VACATION -> DayFlag.VACATION
        DB_FLAG_ABSENCE -> DayFlag.ABSENCE
        else -> throw IllegalArgumentException("unexpected flag value: $value")
    }

private const val DB_FLAG_VACATION = "holiday"
private const val DB_FLAG_ABSENCE = "absence"
