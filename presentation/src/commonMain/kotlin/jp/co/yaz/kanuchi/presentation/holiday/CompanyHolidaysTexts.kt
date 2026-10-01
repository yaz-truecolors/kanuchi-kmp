package jp.co.yaz.kanuchi.presentation.holiday

import androidx.compose.runtime.Composable
import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayInput
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayViolation
import jp.co.yaz.kanuchi.domain.calendar.JapaneseHolidays
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.company_holidays_date
import kanuchi.presentation.generated.resources.company_holidays_date_invalid_error
import kanuchi.presentation.generated.resources.company_holidays_day_of_week_friday
import kanuchi.presentation.generated.resources.company_holidays_day_of_week_monday
import kanuchi.presentation.generated.resources.company_holidays_day_of_week_saturday
import kanuchi.presentation.generated.resources.company_holidays_day_of_week_sunday
import kanuchi.presentation.generated.resources.company_holidays_day_of_week_thursday
import kanuchi.presentation.generated.resources.company_holidays_day_of_week_tuesday
import kanuchi.presentation.generated.resources.company_holidays_day_of_week_wednesday
import kanuchi.presentation.generated.resources.company_holidays_duplicate_error
import kanuchi.presentation.generated.resources.company_holidays_end_date_before_start_date_error
import kanuchi.presentation.generated.resources.company_holidays_name_blank_error
import kanuchi.presentation.generated.resources.company_holidays_name_too_long_error
import kanuchi.presentation.generated.resources.company_holidays_period_too_long_error
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

// 休業日管理画面で使う表示用の文言の組み立て (日付・曜日・エラー)。

/** 日付の表示 (例: 「12/29（火）」)。年は画面上部に表示しているので省く。 */
@Composable
internal fun formatDate(date: LocalDate): String =
    stringResource(
        Res.string.company_holidays_date,
        (date.month.ordinal + 1).toString(),
        date.day.toString(),
        dayOfWeekLabel(date.dayOfWeek),
    )

@Composable
internal fun dayOfWeekLabel(dayOfWeek: DayOfWeek): String =
    when (dayOfWeek) {
        DayOfWeek.MONDAY -> stringResource(Res.string.company_holidays_day_of_week_monday)
        DayOfWeek.TUESDAY -> stringResource(Res.string.company_holidays_day_of_week_tuesday)
        DayOfWeek.WEDNESDAY -> stringResource(Res.string.company_holidays_day_of_week_wednesday)
        DayOfWeek.THURSDAY -> stringResource(Res.string.company_holidays_day_of_week_thursday)
        DayOfWeek.FRIDAY -> stringResource(Res.string.company_holidays_day_of_week_friday)
        DayOfWeek.SATURDAY -> stringResource(Res.string.company_holidays_day_of_week_saturday)
        DayOfWeek.SUNDAY -> stringResource(Res.string.company_holidays_day_of_week_sunday)
    }

internal fun LocalDate.isWeekendOrNationalHoliday(): Boolean =
    dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY || JapaneseHolidays.isHoliday(this)

@Composable
internal fun CompanyHolidayViolation.message(): String =
    when (this) {
        CompanyHolidayViolation.START_DATE_INVALID,
        CompanyHolidayViolation.END_DATE_INVALID,
        -> stringResource(Res.string.company_holidays_date_invalid_error)
        CompanyHolidayViolation.END_DATE_BEFORE_START_DATE ->
            stringResource(Res.string.company_holidays_end_date_before_start_date_error)
        CompanyHolidayViolation.PERIOD_TOO_LONG ->
            stringResource(Res.string.company_holidays_period_too_long_error, CompanyHolidayInput.MAX_DAYS.toString())
        CompanyHolidayViolation.NAME_BLANK -> stringResource(Res.string.company_holidays_name_blank_error)
        CompanyHolidayViolation.NAME_TOO_LONG ->
            stringResource(Res.string.company_holidays_name_too_long_error, CompanyHoliday.MAX_NAME_LENGTH.toString())
    }

@Composable
internal fun CompanyHolidayAddError.message(): String =
    when (this) {
        CompanyHolidayAddError.DUPLICATE -> stringResource(Res.string.company_holidays_duplicate_error)
        CompanyHolidayAddError.SAVE_FAILED -> stringResource(Res.string.common_data_save_error_message)
    }
