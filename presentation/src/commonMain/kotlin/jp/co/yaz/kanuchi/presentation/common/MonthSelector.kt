package jp.co.yaz.kanuchi.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_year_month_format
import kanuchi.presentation.generated.resources.ic_chevron_left
import kanuchi.presentation.generated.resources.ic_chevron_right
import kotlinx.datetime.YearMonth
import kotlinx.datetime.minusMonth
import kotlinx.datetime.plusMonth
import org.jetbrains.compose.resources.DrawableResource
import org.jetbrains.compose.resources.painterResource
import org.jetbrains.compose.resources.stringResource

/**
 * 表示する月の切り替え部品 (「<」「YYYY年M月」「>」。矢印はアイコン)。日次入力・案件別集計・管理者ダッシュボードで共通に使う。
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
        ArrowButton(
            icon = Res.drawable.ic_chevron_left,
            onClick = { onYearMonthChange(previous) },
            enabled = enabled && (minYearMonth == null || previous >= minYearMonth),
        )
        Text(text = formatYearMonth(yearMonth), style = MaterialTheme.typography.titleMedium)
        ArrowButton(
            icon = Res.drawable.ic_chevron_right,
            onClick = { onYearMonthChange(next) },
            enabled = enabled && (maxYearMonth == null || next <= maxYearMonth),
        )
    }
}

/** 前・次への切り替えの矢印ボタン (月の切り替えのほか、休業日管理の年の切り替えでも使う)。 */
@Composable
fun ArrowButton(
    icon: DrawableResource,
    onClick: () -> Unit,
    enabled: Boolean,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(painterResource(icon), contentDescription = null)
    }
}

/** 月の表示 (例: 「2026年10月」)。 */
@Composable
fun formatYearMonth(yearMonth: YearMonth): String =
    stringResource(Res.string.common_year_month_format, yearMonth.year.toString(), (yearMonth.month.ordinal + 1).toString())
