package jp.co.yaz.kanuchi.presentation.dailyinput

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.work.AllocationHoursViolation
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.WorkRecordInput
import jp.co.yaz.kanuchi.domain.work.WorkRecordViolation
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.daily_input_clock_out_not_after_clock_in_error
import kanuchi.presentation.generated.resources.daily_input_day_kind_company_holiday
import kanuchi.presentation.generated.resources.daily_input_day_kind_national_holiday
import kanuchi.presentation.generated.resources.daily_input_day_kind_weekday
import kanuchi.presentation.generated.resources.daily_input_day_kind_weekend
import kanuchi.presentation.generated.resources.daily_input_flag_absence
import kanuchi.presentation.generated.resources.daily_input_flag_vacation
import kanuchi.presentation.generated.resources.daily_input_hours_format_error
import kanuchi.presentation.generated.resources.daily_input_hours_too_large_error
import kanuchi.presentation.generated.resources.daily_input_negative_working_hours_warning
import kanuchi.presentation.generated.resources.daily_input_time_format_error
import kanuchi.presentation.generated.resources.daily_input_weekday_friday
import kanuchi.presentation.generated.resources.daily_input_weekday_monday
import kanuchi.presentation.generated.resources.daily_input_weekday_saturday
import kanuchi.presentation.generated.resources.daily_input_weekday_sunday
import kanuchi.presentation.generated.resources.daily_input_weekday_thursday
import kanuchi.presentation.generated.resources.daily_input_weekday_tuesday
import kanuchi.presentation.generated.resources.daily_input_weekday_wednesday
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.stringResource

/** 曜日の表示 (例: 「月」)。 */
@Composable
internal fun weekdayLabel(dayOfWeek: DayOfWeek): String =
    stringResource(
        when (dayOfWeek) {
            DayOfWeek.MONDAY -> Res.string.daily_input_weekday_monday
            DayOfWeek.TUESDAY -> Res.string.daily_input_weekday_tuesday
            DayOfWeek.WEDNESDAY -> Res.string.daily_input_weekday_wednesday
            DayOfWeek.THURSDAY -> Res.string.daily_input_weekday_thursday
            DayOfWeek.FRIDAY -> Res.string.daily_input_weekday_friday
            DayOfWeek.SATURDAY -> Res.string.daily_input_weekday_saturday
            DayOfWeek.SUNDAY -> Res.string.daily_input_weekday_sunday
        },
    )

/** 日の種類の表示 (例: 「平日」「祝日（元日）」)。 */
@Composable
internal fun dayKindLabel(dayKind: DayKind): String =
    when (dayKind) {
        DayKind.Weekday -> stringResource(Res.string.daily_input_day_kind_weekday)
        DayKind.Weekend -> stringResource(Res.string.daily_input_day_kind_weekend)
        is DayKind.NationalHoliday -> stringResource(Res.string.daily_input_day_kind_national_holiday, dayKind.name)
        is DayKind.CompanyHoliday -> stringResource(Res.string.daily_input_day_kind_company_holiday, dayKind.name)
    }

/** 日付・休日名の色。日曜・祝日・会社の休業日は赤、土曜は青。 */
@Composable
internal fun dayColor(
    date: LocalDate,
    dayKind: DayKind,
): Color =
    when {
        dayKind is DayKind.NationalHoliday || dayKind is DayKind.CompanyHoliday || date.dayOfWeek == DayOfWeek.SUNDAY ->
            MaterialTheme.colorScheme.error
        date.dayOfWeek == DayOfWeek.SATURDAY -> SATURDAY_COLOR
        else -> Color.Unspecified
    }

private val SATURDAY_COLOR = Color(0xFF1565C0)

internal fun holidayNameOf(dayKind: DayKind): String? =
    when (dayKind) {
        is DayKind.NationalHoliday -> dayKind.name
        is DayKind.CompanyHoliday -> dayKind.name
        else -> null
    }

@Composable
internal fun flagLabel(flag: DayFlag): String =
    when (flag) {
        DayFlag.VACATION -> stringResource(Res.string.daily_input_flag_vacation)
        DayFlag.ABSENCE -> stringResource(Res.string.daily_input_flag_absence)
    }

@Composable
internal fun WorkRecordViolation.message(): String =
    when (this) {
        WorkRecordViolation.CLOCK_IN_INVALID_FORMAT,
        WorkRecordViolation.CLOCK_OUT_INVALID_FORMAT,
        -> stringResource(Res.string.daily_input_time_format_error)
        WorkRecordViolation.CLOCK_OUT_NOT_AFTER_CLOCK_IN -> stringResource(Res.string.daily_input_clock_out_not_after_clock_in_error)
        WorkRecordViolation.BREAK_HOURS_INVALID_FORMAT -> stringResource(Res.string.daily_input_hours_format_error)
        WorkRecordViolation.BREAK_HOURS_TOO_LARGE ->
            stringResource(Res.string.daily_input_hours_too_large_error, WorkRecordInput.MAX_HOURS.toString())
        WorkRecordViolation.WORKING_HOURS_NEGATIVE -> stringResource(Res.string.daily_input_negative_working_hours_warning)
    }

@Composable
internal fun AllocationHoursViolation.message(): String =
    when (this) {
        AllocationHoursViolation.INVALID_FORMAT -> stringResource(Res.string.daily_input_hours_format_error)
        AllocationHoursViolation.TOO_LARGE ->
            stringResource(
                Res.string.daily_input_hours_too_large_error,
                WorkRecordInput.MAX_HOURS.toString(),
            )
    }
