package jp.co.yaz.kanuchi.domain.common

/**
 * 0以上の時間数 (単位: 時間、小数第2位 = 0.01時間 まで) を表す Value Object。
 * DB の `numeric(p, 2)` の時間数の列 (休憩時間・稼働時間の下限/上限等) に対応する。
 *
 * 2進小数による誤差を避けるため、内部では 0.01 時間単位の整数で保持する。
 * 負の値を作れないよう、コンストラクタは private にし [ofHundredths] / [parse] 経由でのみ生成する。
 */
value class Hours private constructor(
    /** 0.01 時間単位の値 (例: 1.5 時間なら 150)。 */
    val hundredths: Int,
) : Comparable<Hours> {
    override fun compareTo(other: Hours): Int = hundredths.compareTo(other.hundredths)

    operator fun plus(other: Hours): Hours = Hours(hundredths + other.hundredths)

    /** 差 (`this - other`)。負になり得るため [SignedHours] で返す。 */
    operator fun minus(other: Hours): SignedHours = SignedHours.ofHundredths(hundredths - other.hundredths)

    fun toSignedHours(): SignedHours = SignedHours.ofHundredths(hundredths)

    /** 時間数の10進表記。小数部の末尾の0は付けない (例: `1`、`1.5`、`1.25`)。 */
    override fun toString(): String {
        val integerPart = hundredths / HUNDRED
        val fractionPart = hundredths % HUNDRED
        if (fractionPart == 0) return integerPart.toString()
        return "$integerPart.${fractionPart.toString().padStart(2, '0').trimEnd('0')}"
    }

    companion object {
        private const val HUNDRED = 100

        val ZERO: Hours = Hours(0)
        private const val MAX_INTEGER_DIGITS = 7
        private val TEXT_PATTERN = Regex("^(\\d{1,$MAX_INTEGER_DIGITS})(?:\\.(\\d{1,2}))?$")

        /** 0.01 時間単位の値から生成する。負の値の場合は [IllegalArgumentException] を投げる。 */
        fun ofHundredths(hundredths: Int): Hours {
            require(hundredths >= 0) { "hours must not be negative: $hundredths" }
            return Hours(hundredths)
        }

        /**
         * 利用者が入力した時間数 (0以上の10進数。小数は第2位まで。例: `1`、`1.5`、`7.75`) を解釈する。
         * 前後の空白は無視する。形式が違う (負の数・小数第3位以下がある・数字以外を含む等) 場合は null。
         */
        fun parse(text: String): Hours? {
            val match = TEXT_PATTERN.matchEntire(text.trim()) ?: return null
            val integerPart = match.groupValues[1].toInt()
            val fractionPart = match.groupValues[2].padEnd(2, '0').toInt()
            return Hours(integerPart * HUNDRED + fractionPart)
        }
    }
}
