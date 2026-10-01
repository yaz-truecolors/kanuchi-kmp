package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.common.Hours

/**
 * 月の案件ごとの実績 (Excel の実績時間・実績割合に相当)。
 *
 * @property projectId 案件ID
 * @property hours 実績時間 (合計に含める日の配分の合計)
 * @property ratio 実績割合 ([hours] ÷ 月の稼働時間合計)。月の稼働時間合計が 0 の場合は null
 */
data class ProjectWorkHours(
    val projectId: String,
    val hours: Hours,
    val ratio: AllocationRatio?,
)
