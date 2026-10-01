package jp.co.yaz.kanuchi.domain.calendar

/**
 * 日の種類。稼働日 (平日) か、休み (土日・日本の祝日・会社の休業日) か。[WorkCalendar.dayKindOf] で求める。
 *
 * 1日が複数に当たる場合は、日本の祝日 > 会社の休業日 > 土日 の順に優先する (例: 日曜日の祝日は [NationalHoliday])。
 */
sealed interface DayKind {
    /** 稼働日 (平日かつ祝日・会社の休業日でない日) か。 */
    val isWorkingDay: Boolean

    /** 平日 (月〜金) で、祝日・会社の休業日でない日。稼働日。 */
    data object Weekday : DayKind {
        override val isWorkingDay: Boolean = true
    }

    /** 土曜日・日曜日 (祝日・会社の休業日でない日)。 */
    data object Weekend : DayKind {
        override val isWorkingDay: Boolean = false
    }

    /** 日本の祝日・休日 (振替休日・国民の休日を含む)。[name] は [JapaneseHolidays.holidayNameOf] の名前。 */
    data class NationalHoliday(
        val name: String,
    ) : DayKind {
        override val isWorkingDay: Boolean = false
    }

    /** 会社の休業日。[name] は [CompanyHoliday.name]。 */
    data class CompanyHoliday(
        val name: String,
    ) : DayKind {
        override val isWorkingDay: Boolean = false
    }
}
