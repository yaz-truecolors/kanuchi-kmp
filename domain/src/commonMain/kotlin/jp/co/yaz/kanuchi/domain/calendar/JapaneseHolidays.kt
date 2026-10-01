package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus

/**
 * 日本の祝日・休日の判定 (Domain Service)。外部の API や年ごとのデータに頼らず、
 * 「国民の祝日に関する法律」(祝日法) のルールで計算する。
 *
 * 対象は 2000〜2099 年 ([SUPPORTED_YEARS])。範囲外の年も同じルールで計算するが、法改正の経緯
 * (ハッピーマンデー制度の導入前など) は反映しないため正確ではない。
 *
 * - 固定日の祝日 (元日、建国記念の日 等) と、ハッピーマンデー (成人の日・海の日・敬老の日・スポーツの日) は、
 *   2000 年以降の改正 (海の日・敬老の日は 2003 年から、昭和の日・5/4 のみどりの日は 2007 年から、山の日は 2016 年から、
 *   天皇誕生日の変更 (2019 年は無し)、体育の日からスポーツの日への改称 (2020 年)) を反映する。
 * - 春分の日・秋分の日は、国立天文台の暦象年表に基づく近似式 (1980〜2099 年で有効) で求める
 *   (実際の日付は前年 2 月の官報で公表される。公表済みの年の日付とは一致することをテストで確認している)。
 * - 東京オリンピック・パラリンピックに伴う 2020・2021 年の移動 (海の日・スポーツの日・山の日) と、
 *   2019 年の天皇の即位に伴う祝日 (5/1・10/22) を反映する。
 * - 振替休日: 祝日が日曜日に当たるときは、その後の最も近い祝日でない日 (2006 年以前は翌日の月曜日のみ)。
 * - 国民の休日: 前日と翌日が祝日である祝日でない日 (2006 年以前は日曜日を除く)。
 *
 * 祝日の名前は内閣府の「国民の祝日について」の一覧に合わせる。ただし、一覧で「休日」とされている日は、
 * 理由が分かるよう [SUBSTITUTE_HOLIDAY] (振替休日) と [CITIZENS_HOLIDAY] (国民の休日) に分けている。
 */
object JapaneseHolidays {
    /** 祝日法のルールを正しく反映している年の範囲。 */
    val SUPPORTED_YEARS: IntRange = 2000..2099

    /** 振替休日の名前。 */
    const val SUBSTITUTE_HOLIDAY: String = "振替休日"

    /** 国民の休日 (前日と翌日が祝日である日) の名前。 */
    const val CITIZENS_HOLIDAY: String = "国民の休日"

    /** [date] が祝日・休日 (振替休日・国民の休日を含む) ならその名前、そうでなければ null。 */
    fun holidayNameOf(date: LocalDate): String? = holidaysOf(date.year)[date]

    /** [date] が祝日・休日 (振替休日・国民の休日を含む) か。 */
    fun isHoliday(date: LocalDate): Boolean = holidayNameOf(date) != null

    /** [year] 年の祝日・休日 (振替休日・国民の休日を含む) の日付と名前。日付の順に並ぶ。 */
    fun holidaysOf(year: Int): Map<LocalDate, String> {
        val nationalHolidays = nationalHolidaysOf(year)
        val result = nationalHolidays.toMutableMap()
        addCitizensHolidays(year, nationalHolidays, result)
        addSubstituteHolidays(year, nationalHolidays, result)
        return result.toList().sortedBy { it.first }.toMap()
    }

    /**
     * 「国民の祝日」(祝日法第2条の祝日と、2019 年の即位に伴う祝日)。振替休日・国民の休日は含まない。
     * 祝日法の条文と突き合わせやすいよう、月日・年はリテラルのまま書く (MagicNumber を抑制)。
     */
    @Suppress("CyclomaticComplexMethod", "MagicNumber")
    private fun nationalHolidaysOf(year: Int): Map<LocalDate, String> =
        buildMap {
            fun add(
                month: Int,
                day: Int,
                name: String,
            ) {
                put(LocalDate(year, month, day), name)
            }

            fun addMonday(
                month: Int,
                nth: Int,
                name: String,
            ) {
                put(nthDayOfWeek(year, month, nth, DayOfWeek.MONDAY), name)
            }

            add(1, 1, "元日")
            addMonday(1, 2, "成人の日")
            add(2, 11, "建国記念の日")
            when {
                year >= 2020 -> add(2, 23, "天皇誕生日")
                year <= 2018 -> add(12, 23, "天皇誕生日")
            }
            add(3, vernalEquinoxDay(year), "春分の日")
            add(4, 29, if (year >= 2007) "昭和の日" else "みどりの日")
            add(5, 3, "憲法記念日")
            if (year >= 2007) add(5, 4, "みどりの日")
            add(5, 5, "こどもの日")
            when {
                year == 2020 -> add(7, 23, "海の日")
                year == 2021 -> add(7, 22, "海の日")
                year >= 2003 -> addMonday(7, 3, "海の日")
                else -> add(7, 20, "海の日")
            }
            when {
                year == 2020 -> add(8, 10, "山の日")
                year == 2021 -> add(8, 8, "山の日")
                year >= 2016 -> add(8, 11, "山の日")
            }
            if (year >= 2003) addMonday(9, 3, "敬老の日") else add(9, 15, "敬老の日")
            add(9, autumnalEquinoxDay(year), "秋分の日")
            when {
                year == 2020 -> add(7, 24, "スポーツの日")
                year == 2021 -> add(7, 23, "スポーツの日")
                year >= 2020 -> addMonday(10, 2, "スポーツの日")
                else -> addMonday(10, 2, "体育の日")
            }
            add(11, 3, "文化の日")
            add(11, 23, "勤労感謝の日")
            if (year == 2019) {
                add(5, 1, "天皇の即位の日")
                add(10, 22, "即位礼正殿の儀の行われる日")
            }
        }

    /** 国民の休日: 前日と翌日が「国民の祝日」である、「国民の祝日」でない日 (2006 年以前は日曜日を除く)。 */
    private fun addCitizensHolidays(
        year: Int,
        nationalHolidays: Map<LocalDate, String>,
        result: MutableMap<LocalDate, String>,
    ) {
        for (holiday in nationalHolidays.keys) {
            val next = holiday.plus(1, DateTimeUnit.DAY)
            val afterNext = holiday.plus(2, DateTimeUnit.DAY)
            val isSandwiched = next !in nationalHolidays && afterNext in nationalHolidays && next.year == year
            val isExcludedSunday = year < NEW_RULE_YEAR && next.dayOfWeek == DayOfWeek.SUNDAY
            if (isSandwiched && !isExcludedSunday) result[next] = CITIZENS_HOLIDAY
        }
    }

    /**
     * 振替休日: 「国民の祝日」が日曜日に当たるときは、その後の最も近い「国民の祝日」でない日
     * (2006 年以前は翌日が「国民の祝日」でなければ翌日のみ)。
     */
    private fun addSubstituteHolidays(
        year: Int,
        nationalHolidays: Map<LocalDate, String>,
        result: MutableMap<LocalDate, String>,
    ) {
        for (holiday in nationalHolidays.keys.filter { it.dayOfWeek == DayOfWeek.SUNDAY }) {
            var substitute = holiday.plus(1, DateTimeUnit.DAY)
            if (year >= NEW_RULE_YEAR) {
                while (substitute in nationalHolidays) substitute = substitute.plus(1, DateTimeUnit.DAY)
            } else if (substitute in nationalHolidays) {
                continue
            }
            if (substitute.year == year) result[substitute] = SUBSTITUTE_HOLIDAY
        }
    }

    /** [year] 年 [month] 月の第 [nth] [dayOfWeek] 曜日。 */
    private fun nthDayOfWeek(
        year: Int,
        month: Int,
        nth: Int,
        dayOfWeek: DayOfWeek,
    ): LocalDate {
        val first = LocalDate(year, month, 1)
        val offset = (dayOfWeek.ordinal - first.dayOfWeek.ordinal + DAYS_PER_WEEK) % DAYS_PER_WEEK
        return first.plus(offset + (nth - 1) * DAYS_PER_WEEK, DateTimeUnit.DAY)
    }

    /**
     * 春分日 (3 月の日)。近似式 `floor(20.8431 + 0.242194 × (年 − 1980)) − floor((年 − 1980) / 4)` を、
     * 2進小数の誤差を避けるため 10^-6 単位の整数で計算する。
     */
    private fun vernalEquinoxDay(year: Int): Int = equinoxDay(year, VERNAL_EQUINOX_BASE)

    /** 秋分日 (9 月の日)。近似式 `floor(23.2488 + 0.242194 × (年 − 1980)) − floor((年 − 1980) / 4)`。 */
    private fun autumnalEquinoxDay(year: Int): Int = equinoxDay(year, AUTUMNAL_EQUINOX_BASE)

    private fun equinoxDay(
        year: Int,
        baseMicros: Long,
    ): Int {
        val elapsedYears = (year - EQUINOX_BASE_YEAR).toLong()
        val day = (baseMicros + EQUINOX_YEARLY_DRIFT_MICROS * elapsedYears).floorDiv(MICROS) - elapsedYears.floorDiv(4L)
        return day.toInt()
    }

    /** 振替休日・国民の休日の現行ルール (2007 年施行の改正祝日法) が適用される最初の年。 */
    private const val NEW_RULE_YEAR = 2007
    private const val DAYS_PER_WEEK = 7
    private const val EQUINOX_BASE_YEAR = 1980
    private const val MICROS = 1_000_000L
    private const val VERNAL_EQUINOX_BASE = 20_843_100L
    private const val AUTUMNAL_EQUINOX_BASE = 23_248_800L
    private const val EQUINOX_YEARLY_DRIFT_MICROS = 242_194L
}
