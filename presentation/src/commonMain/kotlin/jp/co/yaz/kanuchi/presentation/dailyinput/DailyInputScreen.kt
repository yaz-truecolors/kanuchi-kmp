package jp.co.yaz.kanuchi.presentation.dailyinput

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.work.MonthlyWorkSheet
import jp.co.yaz.kanuchi.domain.work.WorkingHoursRangeStatus
import jp.co.yaz.kanuchi.presentation.common.MonthSelector
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import jp.co.yaz.kanuchi.presentation.common.formatHours
import jp.co.yaz.kanuchi.presentation.common.formatSignedHours
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.daily_input_business_day_count
import kanuchi.presentation.generated.resources.daily_input_column_balance
import kanuchi.presentation.generated.resources.daily_input_column_break_hours
import kanuchi.presentation.generated.resources.daily_input_column_clock_in
import kanuchi.presentation.generated.resources.daily_input_column_clock_out
import kanuchi.presentation.generated.resources.daily_input_column_date
import kanuchi.presentation.generated.resources.daily_input_column_day_kind
import kanuchi.presentation.generated.resources.daily_input_column_flag
import kanuchi.presentation.generated.resources.daily_input_column_note
import kanuchi.presentation.generated.resources.daily_input_column_working_hours
import kanuchi.presentation.generated.resources.daily_input_counted_days_note
import kanuchi.presentation.generated.resources.daily_input_imbalance_day_count
import kanuchi.presentation.generated.resources.daily_input_list_hint
import kanuchi.presentation.generated.resources.daily_input_negative_day_count
import kanuchi.presentation.generated.resources.daily_input_note_mark
import kanuchi.presentation.generated.resources.daily_input_range_above_max
import kanuchi.presentation.generated.resources.daily_input_range_below_min
import kanuchi.presentation.generated.resources.daily_input_row_date_format
import kanuchi.presentation.generated.resources.daily_input_title
import kanuchi.presentation.generated.resources.daily_input_total_working_hours
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 日次入力画面 (月の一覧)。ログイン中のユーザー自身の、選んだ月の日ごとの出勤・退勤・休憩・稼働時間・過不足を表示する。
 * 行を押すと、その日の入力画面を開く。
 *
 * @param onOpenDay 日付の行が押されたときに、その日付を受け取る
 */
@Composable
fun DailyInputScreen(
    onBack: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    viewModel: DailyInputViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // 日の入力画面から戻ったときも、この画面は作り直されるため再度実行される
    LaunchedEffect(viewModel) { viewModel.onScreenShown() }

    SubScreenScaffold(title = stringResource(Res.string.daily_input_title), onBack = onBack) {
        MonthSelector(
            yearMonth = uiState.yearMonth,
            onYearMonthChange = viewModel::onYearMonthChanged,
        )
        val sheet = uiState.sheet?.takeIf { it.yearMonth == uiState.yearMonth }
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed || sheet == null -> {
                Text(stringResource(Res.string.common_data_load_error_message))
                TextButton(onClick = viewModel::onRetryClicked) { Text(stringResource(Res.string.common_retry_button)) }
            }
            else -> {
                MonthSummary(sheet, hasFutureDays = uiState.hasFutureDays)
                Text(text = stringResource(Res.string.daily_input_list_hint), style = MaterialTheme.typography.bodySmall)
                DayTable(uiState.rows, onOpenDay)
            }
        }
    }
}

@Composable
private fun MonthSummary(
    sheet: MonthlyWorkSheet,
    hasFutureDays: Boolean,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val shift = sheet.shiftSettings
        Text(
            stringResource(
                Res.string.daily_input_total_working_hours,
                formatHours(sheet.totalWorkingHours),
                formatHours(shift.minHours),
                formatHours(shift.maxHours),
            ),
        )
        when (sheet.rangeStatus) {
            WorkingHoursRangeStatus.BELOW_MIN -> WarningText(stringResource(Res.string.daily_input_range_below_min))
            WorkingHoursRangeStatus.ABOVE_MAX -> WarningText(stringResource(Res.string.daily_input_range_above_max))
            WorkingHoursRangeStatus.WITHIN_RANGE -> Unit
        }
        Text(stringResource(Res.string.daily_input_business_day_count, sheet.businessDayCount.toString()))
        val imbalance = stringResource(Res.string.daily_input_imbalance_day_count, sheet.imbalanceDayCount.toString())
        if (sheet.imbalanceDayCount > 0) WarningText(imbalance) else Text(imbalance)
        if (sheet.negativeWorkingHoursDayCount > 0) {
            WarningText(stringResource(Res.string.daily_input_negative_day_count, sheet.negativeWorkingHoursDayCount.toString()))
        }
        if (hasFutureDays) {
            Text(text = stringResource(Res.string.daily_input_counted_days_note), style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun WarningText(text: String) {
    Text(text = text, color = MaterialTheme.colorScheme.error)
}

/** 一覧の列の幅。1行に収めるため固定幅にする。 */
private object ColumnWidth {
    val DATE: Dp = 84.dp
    val DAY_KIND: Dp = 104.dp
    val FLAG: Dp = 28.dp
    val TIME: Dp = 52.dp
    val HOURS: Dp = 52.dp
    val BALANCE: Dp = 60.dp
    val NOTE: Dp = 40.dp
}

@Composable
private fun DayTable(
    rows: List<DailyInputRow>,
    onOpenDay: (LocalDate) -> Unit,
) {
    Column {
        TableRow(modifier = Modifier.padding(vertical = 4.dp)) {
            HeaderCell(Res.string.daily_input_column_date, ColumnWidth.DATE, TextAlign.Start)
            HeaderCell(Res.string.daily_input_column_day_kind, ColumnWidth.DAY_KIND, TextAlign.Start)
            HeaderCell(Res.string.daily_input_column_flag, ColumnWidth.FLAG, TextAlign.Center)
            HeaderCell(Res.string.daily_input_column_clock_in, ColumnWidth.TIME, TextAlign.End)
            HeaderCell(Res.string.daily_input_column_clock_out, ColumnWidth.TIME, TextAlign.End)
            HeaderCell(Res.string.daily_input_column_break_hours, ColumnWidth.HOURS, TextAlign.End)
            HeaderCell(Res.string.daily_input_column_working_hours, ColumnWidth.HOURS, TextAlign.End)
            HeaderCell(Res.string.daily_input_column_balance, ColumnWidth.BALANCE, TextAlign.End)
            HeaderCell(Res.string.daily_input_column_note, ColumnWidth.NOTE, TextAlign.Center)
        }
        HorizontalDivider()
        rows.forEach { row ->
            DayRow(row, onClick = { onOpenDay(row.date) })
            HorizontalDivider()
        }
    }
}

@Composable
private fun TableRow(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) { content() }
}

@Composable
private fun HeaderCell(
    text: StringResource,
    width: Dp,
    align: TextAlign,
) {
    Text(
        text = stringResource(text),
        modifier = Modifier.width(width),
        textAlign = align,
        style = MaterialTheme.typography.labelMedium,
    )
}

@Composable
private fun DayRow(
    row: DailyInputRow,
    onClick: () -> Unit,
) {
    val background = if (row.isToday) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
    // 今日より後の日の稼働・過不足は、合計に含めないため薄く表示する
    val uncountedColor = MaterialTheme.colorScheme.onSurface.copy(alpha = DIMMED_ALPHA)
    TableRow(modifier = Modifier.background(background).clickable(onClick = onClick).padding(vertical = 8.dp)) {
        Cell(
            text = stringResource(Res.string.daily_input_row_date_format, row.date.day.toString(), weekdayLabel(row.date.dayOfWeek)),
            width = ColumnWidth.DATE,
            align = TextAlign.Start,
            color = dayColor(row.date, row.dayKind),
        )
        Cell(
            text = holidayNameOf(row.dayKind).orEmpty(),
            width = ColumnWidth.DAY_KIND,
            align = TextAlign.Start,
            color = dayColor(row.date, row.dayKind),
        )
        Cell(text = row.flag?.let { flagLabel(it) }.orEmpty(), width = ColumnWidth.FLAG, align = TextAlign.Center)
        ShownValueCell(row.clockIn, ColumnWidth.TIME)
        ShownValueCell(row.clockOut, ColumnWidth.TIME)
        ShownValueCell(row.breakHours, ColumnWidth.HOURS)
        Cell(
            text = formatHours(row.workingHours),
            width = ColumnWidth.HOURS,
            align = TextAlign.End,
            color =
                when {
                    !row.isCounted -> uncountedColor
                    row.isWorkingHoursNegative -> MaterialTheme.colorScheme.error
                    else -> Color.Unspecified
                },
        )
        Cell(
            text = row.balance?.let(::formatSignedHours).orEmpty(),
            width = ColumnWidth.BALANCE,
            align = TextAlign.End,
            color =
                when {
                    !row.isCounted -> uncountedColor
                    row.hasImbalance -> MaterialTheme.colorScheme.error
                    else -> Color.Unspecified
                },
        )
        Cell(
            text = if (row.hasNote) stringResource(Res.string.daily_input_note_mark) else "",
            width = ColumnWidth.NOTE,
            align = TextAlign.Center,
        )
    }
}

@Composable
private fun ShownValueCell(
    value: ShownValue?,
    width: Dp,
) {
    Cell(
        text = value?.text.orEmpty(),
        width = width,
        align = TextAlign.End,
        // 空欄 (定時で計算している) の値は薄く表示する
        color = if (value?.isDefault == true) MaterialTheme.colorScheme.onSurface.copy(alpha = DIMMED_ALPHA) else Color.Unspecified,
    )
}

@Composable
private fun Cell(
    text: String,
    width: Dp,
    align: TextAlign,
    color: Color = Color.Unspecified,
) {
    Text(
        text = text,
        modifier = Modifier.width(width),
        textAlign = align,
        color = color,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        style = MaterialTheme.typography.bodyMedium,
    )
}

private const val DIMMED_ALPHA = 0.4f
