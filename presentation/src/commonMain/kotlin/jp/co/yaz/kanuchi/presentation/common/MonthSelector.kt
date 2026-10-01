package jp.co.yaz.kanuchi.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_next_month_button
import kanuchi.presentation.generated.resources.common_previous_month_button
import kanuchi.presentation.generated.resources.common_year_month_format
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import org.jetbrains.compose.resources.stringResource

/**
 * 表示する月の切り替え部品 (「< 前月」「YYYY年M月」「翌月 >」)。日次入力・案件別集計・管理者ダッシュボードで共通に使う。
 *
 * @param yearMonth 表示中の月
 * @param onYearMonthChange 前月・翌月のボタンが押されたときに、切り替え先の月を受け取る
 * @param enabled false なら前月・翌月のボタンを押せなくする (読み込み中・保存中等)
 * @param minYearMonth これより前の月には切り替えられないようにする (null なら制限なし)
 * @param maxYearMonth これより後の月には切り替えられないようにする (null なら制限なし)
 */
@Composable
fun MonthSelector(
    yearMonth: YearMonth,
    onYearMonthChange: (YearMonth) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    minYearMonth: YearMonth? = null,
    maxYearMonth: YearMonth? = null,
) {
    val previous = yearMonth.minusMonth()
    val next = yearMonth.plusMonth()
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TextButton(
            onClick = { onYearMonthChange(previous) },
            enabled = enabled && (minYearMonth == null || previous >= minYearMonth),
        ) {
            Text(stringResource(Res.string.common_previous_month_button))
        }
        Text(text = formatYearMonth(yearMonth), style = MaterialTheme.typography.titleMedium)
        TextButton(
            onClick = { onYearMonthChange(next) },
            enabled = enabled && (maxYearMonth == null || next <= maxYearMonth),
        ) {
            Text(stringResource(Res.string.common_next_month_button))
        }
    }
}

/** 月の表示 (例: 「2026年10月」)。 */
@Composable
fun formatYearMonth(yearMonth: YearMonth): String =
    stringResource(Res.string.common_year_month_format, yearMonth.year.toString(), (yearMonth.month.ordinal + 1).toString())
