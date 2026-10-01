package jp.co.yaz.kanuchi.presentation.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.width
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.work.AllocationRatio
import jp.co.yaz.kanuchi.domain.work.MonthlyWorkSheet
import jp.co.yaz.kanuchi.domain.work.WorkingHoursRangeStatus
import jp.co.yaz.kanuchi.presentation.common.MonthSelector
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import jp.co.yaz.kanuchi.presentation.common.formatHours
import jp.co.yaz.kanuchi.presentation.common.formatRatio
import jp.co.yaz.kanuchi.presentation.common.formatSignedHours
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.summary_above_max_message
import kanuchi.presentation.generated.resources.summary_below_min_message
import kanuchi.presentation.generated.resources.summary_business_days_label
import kanuchi.presentation.generated.resources.summary_days_value
import kanuchi.presentation.generated.resources.summary_hours_value
import kanuchi.presentation.generated.resources.summary_imbalance_days_label
import kanuchi.presentation.generated.resources.summary_overview_header
import kanuchi.presentation.generated.resources.summary_project_inactive
import kanuchi.presentation.generated.resources.summary_project_unknown
import kanuchi.presentation.generated.resources.summary_projects_description
import kanuchi.presentation.generated.resources.summary_projects_empty
import kanuchi.presentation.generated.resources.summary_projects_header
import kanuchi.presentation.generated.resources.summary_range_label
import kanuchi.presentation.generated.resources.summary_range_value
import kanuchi.presentation.generated.resources.summary_ratio_unavailable
import kanuchi.presentation.generated.resources.summary_ratio_value
import kanuchi.presentation.generated.resources.summary_target_user
import kanuchi.presentation.generated.resources.summary_title
import kanuchi.presentation.generated.resources.summary_total_working_hours_label
import kanuchi.presentation.generated.resources.summary_unallocated_label
import kanuchi.presentation.generated.resources.summary_user_not_viewable_message
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 案件別集計画面 (全員)。1人分の月の集計 (営業日数・稼働時間合計と下限/上限・案件ごとの実績時間と割合・未配分の時間・
 * 過不足がある日数) を表示する。月を切り替えられる。
 *
 * @param userId 集計の対象のユーザーID。null ならログイン中のユーザー自身 (ホーム画面から開いた場合)。
 *   管理者ダッシュボードから開いた場合はそのユーザーのIDで、画面上部に誰の集計かを表示する
 * @param yearMonth 初めに表示する月 (`yyyy-MM` 形式)。null なら今月
 */
@Composable
fun ProjectSummaryScreen(
    userId: String?,
    yearMonth: String?,
    onBack: () -> Unit,
    viewModel: ProjectSummaryViewModel = koinViewModel { parametersOf(userId, yearMonth) },
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.summary_title), onBack = onBack) {
        val targetUser = uiState.targetUser
        if (uiState.showsTargetUser && targetUser != null) {
            Text(
                text = stringResource(Res.string.summary_target_user, targetUser.displayName, targetUser.email),
                style = MaterialTheme.typography.titleMedium,
            )
        }
        if (uiState.loadError == ProjectSummaryLoadError.USER_NOT_VIEWABLE) {
            Text(
                text = stringResource(Res.string.summary_user_not_viewable_message),
                color = MaterialTheme.colorScheme.error,
            )
            return@SubScreenScaffold
        }
        MonthSelector(
            yearMonth = uiState.yearMonth,
            onYearMonthChange = viewModel::onMonthChanged,
            minYearMonth = uiState.minYearMonth,
            maxYearMonth = uiState.maxYearMonth,
        )
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadError == ProjectSummaryLoadError.LOAD_FAILED -> {
                Text(stringResource(Res.string.common_data_load_error_message))
                TextButton(onClick = viewModel::onRetryClicked) { Text(stringResource(Res.string.common_retry_button)) }
            }
            else -> uiState.summary?.let { ProjectSummaryContent(it) }
        }
    }
}

@Composable
private fun ProjectSummaryContent(summary: ProjectSummary) {
    val sheet = summary.sheet
    Overview(sheet)
    HorizontalDivider()
    Text(text = stringResource(Res.string.summary_projects_header), style = MaterialTheme.typography.titleMedium)
    Text(
        text = stringResource(Res.string.summary_projects_description),
        style = MaterialTheme.typography.bodySmall,
    )
    if (summary.projectRows.isEmpty()) {
        Text(stringResource(Res.string.summary_projects_empty))
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        summary.projectRows.forEach { row ->
            key(row.workHours.projectId) {
                val project = row.project
                HoursRow(
                    label =
                        when {
                            project == null -> stringResource(Res.string.summary_project_unknown)
                            project.isActive -> project.name
                            else -> stringResource(Res.string.summary_project_inactive, project.name)
                        },
                    hours = formatHours(row.workHours.hours),
                    ratio = row.workHours.ratio,
                )
            }
        }
        HorizontalDivider()
        HoursRow(
            label = stringResource(Res.string.summary_unallocated_label),
            hours = formatSignedHours(sheet.unallocatedHours),
            ratio = sheet.unallocatedRatio,
            color = if (sheet.unallocatedHours.isZero) Color.Unspecified else MaterialTheme.colorScheme.error,
        )
    }
}

@Composable
private fun Overview(sheet: MonthlyWorkSheet) {
    Text(text = stringResource(Res.string.summary_overview_header), style = MaterialTheme.typography.titleMedium)
    LabeledValue(
        label = stringResource(Res.string.summary_business_days_label),
        value = stringResource(Res.string.summary_days_value, sheet.businessDayCount.toString()),
    )
    val outOfRange = sheet.rangeStatus != WorkingHoursRangeStatus.WITHIN_RANGE
    LabeledValue(
        label = stringResource(Res.string.summary_total_working_hours_label),
        value = stringResource(Res.string.summary_hours_value, formatHours(sheet.totalWorkingHours)),
        color = if (outOfRange) MaterialTheme.colorScheme.error else Color.Unspecified,
    )
    LabeledValue(
        label = stringResource(Res.string.summary_range_label),
        value =
            stringResource(
                Res.string.summary_range_value,
                formatHours(sheet.shiftSettings.minHours),
                formatHours(sheet.shiftSettings.maxHours),
            ),
    )
    when (sheet.rangeStatus) {
        WorkingHoursRangeStatus.BELOW_MIN ->
            Text(text = stringResource(Res.string.summary_below_min_message), color = MaterialTheme.colorScheme.error)
        WorkingHoursRangeStatus.ABOVE_MAX ->
            Text(text = stringResource(Res.string.summary_above_max_message), color = MaterialTheme.colorScheme.error)
        WorkingHoursRangeStatus.WITHIN_RANGE -> Unit
    }
    LabeledValue(
        label = stringResource(Res.string.summary_imbalance_days_label),
        value = stringResource(Res.string.summary_days_value, sheet.imbalanceDayCount.toString()),
        color = if (sheet.imbalanceDayCount > 0) MaterialTheme.colorScheme.error else Color.Unspecified,
    )
}

@Composable
private fun LabeledValue(
    label: String,
    value: String,
    color: Color = Color.Unspecified,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f))
        Text(text = value, color = color)
    }
}

/** 案件名 (または「未配分」)・時間・割合の1行。時間と割合は列をそろえるため幅を固定して右寄せにする。 */
@Composable
private fun HoursRow(
    label: String,
    hours: String,
    ratio: AllocationRatio?,
    color: Color = Color.Unspecified,
) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, modifier = Modifier.weight(1f), color = color)
        Text(
            text = stringResource(Res.string.summary_hours_value, hours),
            modifier = Modifier.width(112.dp),
            textAlign = TextAlign.End,
            color = color,
        )
        Text(
            text =
                if (ratio != null) {
                    stringResource(Res.string.summary_ratio_value, formatRatio(ratio))
                } else {
                    stringResource(Res.string.summary_ratio_unavailable)
                },
            modifier = Modifier.width(80.dp),
            textAlign = TextAlign.End,
            color = color,
        )
    }
}
