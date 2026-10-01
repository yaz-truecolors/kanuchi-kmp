package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours

/**
 * 割合 (Value Object。Excel の実績割合に相当)。0.1% 単位の整数で保持する (例: 12.5% なら 125)。
 * 案件ごとの実績時間 ÷ 月の稼働時間合計 のように、部分 ÷ 全体 で求める ([of])。
 *
 * 0.1% 未満は四捨五入する (0.05% ちょうどは 0 から遠い方へ)。そのため、案件ごとの割合と未配分の割合の合計は
 * 100% から ±0.1% 程度ずれることがある。未配分の時間が負 (配分しすぎ) の場合は負の割合になる。
 */
value class AllocationRatio private constructor(
    /** 0.1% 単位の値 (例: 12.5% なら 125、100% なら 1000)。 */
    val tenthsOfPercent: Int,
) : Comparable<AllocationRatio> {
    override fun compareTo(other: AllocationRatio): Int = tenthsOfPercent.compareTo(other.tenthsOfPercent)

    /** パーセントの10進表記 (`%` は付けない)。小数第1位まで常に表示する (例: `12.5`、`100.0`、`-3.0`)。 */
    override fun toString(): String {
        val absolute = if (tenthsOfPercent < 0) -tenthsOfPercent else tenthsOfPercent
        val sign = if (tenthsOfPercent < 0) "-" else ""
        return "$sign${absolute / TEN}.${absolute % TEN}"
    }

    companion object {
        private const val TEN = 10
        private const val TENTHS_OF_PERCENT_PER_WHOLE = 1000L

        /** 0.1% 単位の値から生成する。 */
        fun ofTenthsOfPercent(tenthsOfPercent: Int): AllocationRatio = AllocationRatio(tenthsOfPercent)

        /** [part] ÷ [whole] の割合。[whole] が 0 の場合は割合を求められないため null。 */
        fun of(
            part: Hours,
            whole: Hours,
        ): AllocationRatio? = of(part.toSignedHours(), whole)

        /** [part] ÷ [whole] の割合 ([part] は負でもよい)。[whole] が 0 の場合は割合を求められないため null。 */
        fun of(
            part: SignedHours,
            whole: Hours,
        ): AllocationRatio? {
            if (whole.hundredths == 0) return null
            val numerator = part.hundredths.toLong() * TENTHS_OF_PERCENT_PER_WHOLE
            val denominator = whole.hundredths.toLong()
            // 絶対値で四捨五入してから符号を戻す (0.05% ちょうどは 0 から遠い方へ)
            val absoluteRounded = (kotlin.math.abs(numerator) * 2 + denominator) / (denominator * 2)
            return AllocationRatio((if (numerator < 0) -absoluteRounded else absoluteRounded).toInt())
        }
    }
}
