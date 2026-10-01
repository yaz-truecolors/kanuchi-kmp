package jp.co.yaz.kanuchi.domain.common

/**
 * 符号付きの時間数 (単位: 時間、小数第2位 = 0.01時間 まで) を表す Value Object。
 * 過不足 (稼働時間 − 案件配分の合計) や未配分の時間など、負にもなる差を表す。
 *
 * [Hours] と同じく、2進小数による誤差を避けるため内部では 0.01 時間単位の整数で保持する。
 */
value class SignedHours private constructor(
    /** 0.01 時間単位の値 (例: -1.5 時間なら -150)。 */
    val hundredths: Int,
) : Comparable<SignedHours> {
    val isZero: Boolean get() = hundredths == 0
    val isNegative: Boolean get() = hundredths < 0
    val isPositive: Boolean get() = hundredths > 0

    override fun compareTo(other: SignedHours): Int = hundredths.compareTo(other.hundredths)

    operator fun plus(other: SignedHours): SignedHours = SignedHours(hundredths + other.hundredths)

    operator fun minus(other: SignedHours): SignedHours = SignedHours(hundredths - other.hundredths)

    operator fun unaryMinus(): SignedHours = SignedHours(-hundredths)

    /** 絶対値。 */
    fun absolute(): Hours = Hours.ofHundredths(if (hundredths < 0) -hundredths else hundredths)

    /** 時間数の10進表記。負の場合は先頭に `-` を付け、小数部の末尾の0は付けない (例: `-1.5`、`0`、`1.25`)。 */
    override fun toString(): String = if (hundredths < 0) "-${absolute()}" else absolute().toString()

    companion object {
        val ZERO: SignedHours = SignedHours(0)

        /** 0.01 時間単位の値から生成する。 */
        fun ofHundredths(hundredths: Int): SignedHours = SignedHours(hundredths)
    }
}
