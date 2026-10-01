package jp.co.yaz.kanuchi.domain.common

/**
 * 1日の中の時刻 (分単位、00:00〜23:59) を表す Value Object。DB の `time` 型の列に対応する。
 *
 * 不正な時刻を作れないよう、コンストラクタは private にし [of] / [parse] 経由でのみ生成する。
 */
value class TimeOfDay private constructor(
    /** 00:00 からの経過分 (0〜1439)。 */
    val minuteOfDay: Int,
) : Comparable<TimeOfDay> {
    val hour: Int get() = minuteOfDay / MINUTES_PER_HOUR
    val minute: Int get() = minuteOfDay % MINUTES_PER_HOUR

    override fun compareTo(other: TimeOfDay): Int = minuteOfDay.compareTo(other.minuteOfDay)

    /** `HH:mm` 形式 (例: `09:30`)。 */
    override fun toString(): String = "${hour.toString().padStart(2, '0')}:${minute.toString().padStart(2, '0')}"

    companion object {
        private const val MINUTES_PER_HOUR = 60
        private const val HOURS_PER_DAY = 24
        private val TEXT_PATTERN = Regex("^(\\d{1,2}):(\\d{2})$")

        /**
         * 時・分から生成する。範囲外 (時が 0〜23、分が 0〜59 以外) の場合は [IllegalArgumentException] を投げる。
         */
        fun of(
            hour: Int,
            minute: Int,
        ): TimeOfDay {
            require(hour in 0 until HOURS_PER_DAY) { "hour must be in 0..23: $hour" }
            require(minute in 0 until MINUTES_PER_HOUR) { "minute must be in 0..59: $minute" }
            return TimeOfDay(hour * MINUTES_PER_HOUR + minute)
        }

        /**
         * 利用者が入力した `HH:mm` (時は1桁も可。例: `9:30`) 形式の文字列を解釈する。前後の空白は無視する。
         * 形式が違う、または範囲外の場合は null。
         */
        fun parse(text: String): TimeOfDay? {
            val match = TEXT_PATTERN.matchEntire(text.trim()) ?: return null
            val hour = match.groupValues[1].toInt()
            val minute = match.groupValues[2].toInt()
            return if (hour in 0 until HOURS_PER_DAY && minute in 0 until MINUTES_PER_HOUR) {
                TimeOfDay(hour * MINUTES_PER_HOUR + minute)
            } else {
                null
            }
        }
    }
}
