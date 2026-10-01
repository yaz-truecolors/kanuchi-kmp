package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours

/**
 * 過不足の計算 (Domain Service。Excel の過不足チェックの列 (H 列) の数式に相当)。
 * 過不足 = 稼働時間 − 案件配分の合計。正なら配分が足りない (未配分の時間がある)、負なら配分しすぎ。
 */
object AllocationBalancer {
    fun balanceOf(
        workingHours: Hours,
        allocatedHours: Hours,
    ): SignedHours = workingHours - allocatedHours
}
