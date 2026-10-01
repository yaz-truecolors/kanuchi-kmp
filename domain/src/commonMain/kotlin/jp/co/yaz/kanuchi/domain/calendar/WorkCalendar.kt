package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate

/**
 * 稼働日の判定 (Domain Service)。土日・日本の祝日 ([JapaneseHolidays])・会社の休業日 ([companyHolidays]) 以外の日を
 * 稼働日とする。
 *
 * @param companyHolidays 判定に使う会社の休業日 (判定する期間の分だけ渡せばよい)
 */
class WorkCalendar(
    companyHolidays: Collection<CompanyHoliday>,
) {
    private val companyHolidayNames: Map<LocalDate, String> = companyHolidays.associate { it.date to it.name }
    private val nationalHolidaysByYear = mutableMapOf<Int, Map<LocalDate, String>>()

    /** [date] の日の種類。 */
    fun dayKindOf(date: LocalDate): DayKind {
        val nationalHolidays = nationalHolidaysByYear.getOrPut(date.year) { JapaneseHolidays.holidaysOf(date.year) }
        val nationalHolidayName = nationalHolidays[date]
        val companyHolidayName = companyHolidayNames[date]
        return when {
            nationalHolidayName != null -> DayKind.NationalHoliday(nationalHolidayName)
            companyHolidayName != null -> DayKind.CompanyHoliday(companyHolidayName)
            date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY -> DayKind.Weekend
            else -> DayKind.Weekday
        }
    }

    /** [date] が稼働日 (平日かつ祝日・会社の休業日でない日) か。 */
    fun isWorkingDay(date: LocalDate): Boolean = dayKindOf(date).isWorkingDay
}
