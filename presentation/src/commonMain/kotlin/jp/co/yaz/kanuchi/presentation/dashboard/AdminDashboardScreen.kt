package jp.co.yaz.kanuchi.presentation.dashboard

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.work.MemberMonthlySummary
import jp.co.yaz.kanuchi.domain.work.TeamMonthlySummary
import jp.co.yaz.kanuchi.domain.work.TeamProjectHours
import jp.co.yaz.kanuchi.domain.work.WorkingHoursRangeStatus
import jp.co.yaz.kanuchi.presentation.common.MonthSelector
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import jp.co.yaz.kanuchi.presentation.common.formatHours
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.admin_dashboard_business_days
import kanuchi.presentation.generated.resources.admin_dashboard_hours
import kanuchi.presentation.generated.resources.admin_dashboard_imbalance_days
import kanuchi.presentation.generated.resources.admin_dashboard_members_empty_message
import kanuchi.presentation.generated.resources.admin_dashboard_members_header
import kanuchi.presentation.generated.resources.admin_dashboard_members_note
import kanuchi.presentation.generated.resources.admin_dashboard_open_member_button
import kanuchi.presentation.generated.resources.admin_dashboard_project_inactive_label
import kanuchi.presentation.generated.resources.admin_dashboard_project_unknown_name
import kanuchi.presentation.generated.resources.admin_dashboard_projects_empty_message
import kanuchi.presentation.generated.resources.admin_dashboard_projects_header
import kanuchi.presentation.generated.resources.admin_dashboard_projects_note
import kanuchi.presentation.generated.resources.admin_dashboard_range_above_max
import kanuchi.presentation.generated.resources.admin_dashboard_range_below_min
import kanuchi.presentation.generated.resources.admin_dashboard_range_within
import kanuchi.presentation.generated.resources.admin_dashboard_suspended_label
import kanuchi.presentation.generated.resources.admin_dashboard_title
import kanuchi.presentation.generated.resources.admin_dashboard_working_hours
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kotlinx.datetime.YearMonth
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 管理者ダッシュボード (admin のみ)。月を切り替えて、メンバーごとの月の稼働状況 (稼働時間合計と下限・上限との比較・
 * 営業日数・過不足のある日数) と、案件ごとのチーム合計時間を表示する。メンバーを選ぶと、その人の案件別集計を開く。
 *
 * @param onOpenMember メンバーが選ばれたときに、そのユーザーIDと表示中の月を受け取る
 */
@Composable
fun AdminDashboardScreen(
    onBack: () -> Unit,
    onOpenMember: (userId: String, yearMonth: YearMonth) -> Unit,
    viewModel: AdminDashboardViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.admin_dashboard_title), onBack = onBack) {
        MonthSelector(yearMonth = uiState.yearMonth, onYearMonthChange = viewModel::onMonthSelected)
        val summary = uiState.summary
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed -> {
                Text(stringResource(Res.string.common_data_load_error_message))
                TextButton(onClick = viewModel::onRetryClicked) { Text(stringResource(Res.string.common_retry_button)) }
            }
            summary != null -> SummaryContent(summary, onOpenMember = { userId -> onOpenMember(userId, summary.yearMonth) })
        }
    }
}

@Composable
private fun SummaryContent(
    summary: TeamMonthlySummary,
    onOpenMember: (userId: String) -> Unit,
) {
    Text(stringResource(Res.string.admin_dashboard_members_header), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.admin_dashboard_members_note), style = MaterialTheme.typography.bodySmall)
    if (summary.members.isEmpty()) {
        Text(stringResource(Res.string.admin_dashboard_members_empty_message))
    } else {
        summary.members.forEach { member ->
            HorizontalDivider()
            MemberRow(member, onClick = { onOpenMember(member.profile.id) })
        }
        HorizontalDivider()
    }

    Text(stringResource(Res.string.admin_dashboard_projects_header), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.admin_dashboard_projects_note), style = MaterialTheme.typography.bodySmall)
    if (summary.projectHours.isEmpty()) {
        Text(stringResource(Res.string.admin_dashboard_projects_empty_message))
    } else {
        summary.projectHours.forEach { projectHours ->
            HorizontalDivider()
            ProjectRow(projectHours)
        }
        HorizontalDivider()
    }
}

@Composable
private fun MemberRow(
    member: MemberMonthlySummary,
    onClick: () -> Unit,
) {
    val profile = member.profile
    val sheet = member.sheet
    Row(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(text = profile.displayName, style = MaterialTheme.typography.titleSmall)
                if (profile.isSuspended) {
                    Text(stringResource(Res.string.admin_dashboard_suspended_label), color = MaterialTheme.colorScheme.error)
                }
            }
            Text(text = profile.email, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    stringResource(
                        Res.string.admin_dashboard_working_hours,
                        formatHours(sheet.totalWorkingHours),
                        formatHours(sheet.shiftSettings.minHours),
                        formatHours(sheet.shiftSettings.maxHours),
                    ),
                )
                RangeStatusLabel(sheet.rangeStatus)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Text(stringResource(Res.string.admin_dashboard_business_days, sheet.businessDayCount.toString()))
                Text(
                    text = stringResource(Res.string.admin_dashboard_imbalance_days, sheet.imbalanceDayCount.toString()),
                    color = if (sheet.imbalanceDayCount > 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
        }
        TextButton(onClick = onClick) { Text(stringResource(Res.string.admin_dashboard_open_member_button)) }
    }
}

@Composable
private fun RangeStatusLabel(status: WorkingHoursRangeStatus) {
    when (status) {
        WorkingHoursRangeStatus.BELOW_MIN ->
            Text(stringResource(Res.string.admin_dashboard_range_below_min), color = MaterialTheme.colorScheme.error)
        WorkingHoursRangeStatus.ABOVE_MAX ->
            Text(stringResource(Res.string.admin_dashboard_range_above_max), color = MaterialTheme.colorScheme.error)
        WorkingHoursRangeStatus.WITHIN_RANGE -> Text(stringResource(Res.string.admin_dashboard_range_within))
    }
}

@Composable
private fun ProjectRow(projectHours: TeamProjectHours) {
    val project = projectHours.project
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(project?.name ?: stringResource(Res.string.admin_dashboard_project_unknown_name))
        if (project != null && !project.isActive) {
            Text(
                stringResource(Res.string.admin_dashboard_project_inactive_label),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline,
            )
        }
        Spacer(modifier = Modifier.weight(1f))
        Text(stringResource(Res.string.admin_dashboard_hours, formatHours(projectHours.hours)))
    }
}
