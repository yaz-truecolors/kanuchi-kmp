package jp.co.yaz.kanuchi.data.work

import jp.co.yaz.kanuchi.data.common.toDbValue
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.work.WorkRecord
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `work_records` に保存 (upsert) する1行。空欄の値も null として送り、保存済みの値を消す
 * (既定値の無いプロパティにしているため、null も JSON に含まれる)。
 */
@Serializable
internal data class WorkRecordSaveDto(
    @SerialName("user_id") val userId: String,
    @SerialName("work_date") val workDate: String,
    @SerialName("clock_in") val clockIn: String?,
    @SerialName("clock_out") val clockOut: String?,
    @SerialName("break_hours") val breakHours: Double?,
    val flag: String?,
    val note: String?,
)

internal fun WorkRecord.toSaveDto(userId: String): WorkRecordSaveDto =
    WorkRecordSaveDto(
        userId = userId,
        workDate = date.toString(),
        clockIn = clockIn?.toString(),
        clockOut = clockOut?.toString(),
        breakHours = breakHours?.toDbValue(),
        flag = flag?.toDbValue(),
        note = note,
    )

/** 保存した `work_records` の行のID (配分の保存に使う)。 */
@Serializable
internal data class WorkRecordIdDto(
    val id: String,
)

/** `allocations` に保存 (upsert) する1行。 */
@Serializable
internal data class AllocationSaveDto(
    @SerialName("work_record_id") val workRecordId: String,
    @SerialName("project_id") val projectId: String,
    val hours: Double,
)

/**
 * 配分の変更内容。保存済みの配分 (current) を保存したい配分 (desired) にするための差分。
 *
 * @property toUpsert 追加または時間を変更する配分 (案件ID → 時間)
 * @property toDelete 削除する配分の案件ID (desired に無い、または時間が 0 の案件)
 */
internal data class AllocationChanges(
    val toUpsert: Map<String, Hours>,
    val toDelete: Set<String>,
)

/** [current] (保存済み) を [desired] (時間が 0 の案件は保存しない) にするための差分。 */
internal fun allocationChangesOf(
    current: Map<String, Hours>,
    desired: Map<String, Hours>,
): AllocationChanges {
    val nonZero = desired.filterValues { it > Hours.ZERO }
    return AllocationChanges(
        toUpsert = nonZero.filter { (projectId, hours) -> current[projectId] != hours },
        toDelete = current.keys - nonZero.keys,
    )
}
