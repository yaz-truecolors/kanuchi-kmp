package jp.co.yaz.kanuchi.presentation.dailyinput

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.work.AllocatableProject
import jp.co.yaz.kanuchi.domain.work.DayFlag
import jp.co.yaz.kanuchi.domain.work.WorkDayEntry
import jp.co.yaz.kanuchi.domain.work.WorkRecordField
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import jp.co.yaz.kanuchi.presentation.common.formatHours
import jp.co.yaz.kanuchi.presentation.common.formatSignedHours
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.daily_input_allocated_hours_preview
import kanuchi.presentation.generated.resources.daily_input_allocation_hint
import kanuchi.presentation.generated.resources.daily_input_balance_preview
import kanuchi.presentation.generated.resources.daily_input_balance_warning
import kanuchi.presentation.generated.resources.daily_input_break_hours_label
import kanuchi.presentation.generated.resources.daily_input_cancel_button
import kanuchi.presentation.generated.resources.daily_input_clock_in_label
import kanuchi.presentation.generated.resources.daily_input_clock_out_label
import kanuchi.presentation.generated.resources.daily_input_day_date_format
import kanuchi.presentation.generated.resources.daily_input_delete_button
import kanuchi.presentation.generated.resources.daily_input_delete_confirm_button
import kanuchi.presentation.generated.resources.daily_input_delete_confirm_message
import kanuchi.presentation.generated.resources.daily_input_delete_confirm_title
import kanuchi.presentation.generated.resources.daily_input_deleting_button
import kanuchi.presentation.generated.resources.daily_input_fill_shift_button
import kanuchi.presentation.generated.resources.daily_input_flag_absence_option
import kanuchi.presentation.generated.resources.daily_input_flag_label
import kanuchi.presentation.generated.resources.daily_input_flag_none_option
import kanuchi.presentation.generated.resources.daily_input_flag_note
import kanuchi.presentation.generated.resources.daily_input_flag_vacation_option
import kanuchi.presentation.generated.resources.daily_input_hours_hint
import kanuchi.presentation.generated.resources.daily_input_negative_working_hours_warning
import kanuchi.presentation.generated.resources.daily_input_not_calculable
import kanuchi.presentation.generated.resources.daily_input_note_label
import kanuchi.presentation.generated.resources.daily_input_project_unassigned_note
import kanuchi.presentation.generated.resources.daily_input_projects_empty_message
import kanuchi.presentation.generated.resources.daily_input_projects_header
import kanuchi.presentation.generated.resources.daily_input_save_button
import kanuchi.presentation.generated.resources.daily_input_saving_button
import kanuchi.presentation.generated.resources.daily_input_shift_placeholder
import kanuchi.presentation.generated.resources.daily_input_time_hint
import kanuchi.presentation.generated.resources.daily_input_weekend_time_note
import kanuchi.presentation.generated.resources.daily_input_working_hours_preview
import kotlinx.datetime.LocalDate
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 日次入力画面 (1日分の入力)。ログイン中のユーザー自身の [date] の出勤・退勤・休憩・印・案件ごとの工数・備考を入力する。
 * 入力中は稼働時間・配分の合計・過不足をその場で計算して表示する (過不足があっても保存できる)。
 *
 * @param onBack 「戻る」「キャンセル」が押されたとき
 * @param onFinished 保存・削除が終わったとき (一覧へ戻る)
 */
@Composable
fun DailyInputDayScreen(
    date: LocalDate,
    onBack: () -> Unit,
    onFinished: () -> Unit,
    viewModel: DailyInputDayViewModel = koinViewModel { parametersOf(date) },
) {
    val uiState by viewModel.uiState.collectAsState()
    LaunchedEffect(uiState.isFinished) {
        if (uiState.isFinished) onFinished()
    }

    // 保存・削除の途中で戻ると ViewModel の処理がキャンセルされ、途中まで保存された状態になり得るため、戻れないようにする
    val backIfIdle = { if (!viewModel.uiState.value.isBusy) onBack() }
    SubScreenScaffold(title = formatDate(date), onBack = backIfIdle, backEnabled = !uiState.isBusy) {
        val entry = uiState.entry
        when {
            entry != null -> {
                Text(text = dayKindLabel(entry.day.dayKind), color = dayColor(date, entry.day.dayKind))
                DayForm(uiState, entry, viewModel, onCancel = backIfIdle)
            }
            uiState.loadFailed -> {
                Text(stringResource(Res.string.common_data_load_error_message))
                TextButton(onClick = viewModel::onRetryClicked) { Text(stringResource(Res.string.common_retry_button)) }
            }
            else -> CircularProgressIndicator()
        }
    }

    if (uiState.showsDeleteConfirmation) {
        DeleteConfirmDialog(onConfirm = viewModel::onDeleteConfirmed, onDismiss = viewModel::onDeleteDismissed)
    }
}

@Composable
private fun formatDate(date: LocalDate): String =
    stringResource(
        Res.string.daily_input_day_date_format,
        date.year.toString(),
        (date.month.ordinal + 1).toString(),
        date.day.toString(),
        weekdayLabel(date.dayOfWeek),
    )

@Composable
private fun DayForm(
    uiState: DailyInputDayUiState,
    entry: WorkDayEntry,
    viewModel: DailyInputDayViewModel,
    onCancel: () -> Unit,
) {
    val input = uiState.input
    val enabled = !uiState.isBusy

    FlagSelector(flag = input.flag, enabled = enabled, onFlagChange = viewModel::onFlagChanged)
    HorizontalDivider()

    val workEnabled = enabled && input.acceptsWorkInput
    TimeFields(uiState, entry, workEnabled, viewModel)
    HorizontalDivider()

    AllocationFields(uiState, entry.projects, workEnabled, onAllocationChange = viewModel::onAllocationChanged)
    HorizontalDivider()

    Preview(uiState)
    HorizontalDivider()

    OutlinedTextField(
        value = input.note,
        onValueChange = viewModel::onNoteChanged,
        label = { Text(stringResource(Res.string.daily_input_note_label)) },
        enabled = enabled,
        minLines = 2,
        modifier = Modifier.fillMaxWidth(),
    )

    if (uiState.saveFailed || uiState.deleteFailed) {
        Text(text = stringResource(Res.string.common_data_save_error_message), color = MaterialTheme.colorScheme.error)
    }
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Button(onClick = viewModel::onSaveClicked, enabled = enabled) {
            Text(stringResource(if (uiState.isSaving) Res.string.daily_input_saving_button else Res.string.daily_input_save_button))
        }
        OutlinedButton(onClick = onCancel, enabled = enabled) { Text(stringResource(Res.string.daily_input_cancel_button)) }
    }
    if (uiState.hasSavedRecord) {
        TextButton(onClick = viewModel::onDeleteClicked, enabled = enabled) {
            Text(
                text =
                    stringResource(
                        if (uiState.isDeleting) Res.string.daily_input_deleting_button else Res.string.daily_input_delete_button,
                    ),
                color =
                    if (enabled) {
                        MaterialTheme.colorScheme.error
                    } else {
                        MaterialTheme.colorScheme.onSurface.copy(
                            alpha = DISABLED_ALPHA,
                        )
                    },
            )
        }
    }
}

@Composable
private fun FlagSelector(
    flag: DayFlag?,
    enabled: Boolean,
    onFlagChange: (DayFlag?) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(text = stringResource(Res.string.daily_input_flag_label), style = MaterialTheme.typography.titleSmall)
        Row(modifier = Modifier.selectableGroup(), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
            FlagOption(null, Res.string.daily_input_flag_none_option, flag, enabled, onFlagChange)
            FlagOption(DayFlag.VACATION, Res.string.daily_input_flag_vacation_option, flag, enabled, onFlagChange)
            FlagOption(DayFlag.ABSENCE, Res.string.daily_input_flag_absence_option, flag, enabled, onFlagChange)
        }
        Text(text = stringResource(Res.string.daily_input_flag_note), style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun FlagOption(
    option: DayFlag?,
    label: StringResource,
    selected: DayFlag?,
    enabled: Boolean,
    onFlagChange: (DayFlag?) -> Unit,
) {
    Row(
        modifier =
            Modifier.selectable(
                selected = option == selected,
                enabled = enabled,
                role = Role.RadioButton,
                onClick = { onFlagChange(option) },
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = option == selected, onClick = null, enabled = enabled)
        Text(stringResource(label))
    }
}

@Composable
private fun TimeFields(
    uiState: DailyInputDayUiState,
    entry: WorkDayEntry,
    enabled: Boolean,
    viewModel: DailyInputDayViewModel,
) {
    val input = uiState.input
    val shift = entry.shiftSettings
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        WorkField(uiState, WorkRecordField.CLOCK_IN, input.clockIn, viewModel::onClockInChanged, shift.startTime.toString(), enabled)
        WorkField(uiState, WorkRecordField.CLOCK_OUT, input.clockOut, viewModel::onClockOutChanged, shift.endTime.toString(), enabled)
        WorkField(
            uiState,
            WorkRecordField.BREAK_HOURS,
            input.breakHours,
            viewModel::onBreakHoursChanged,
            formatHours(shift.breakHours),
            enabled,
        )
    }
    if (!entry.day.dayKind.isWorkingDay) {
        Text(text = stringResource(Res.string.daily_input_weekend_time_note), style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = viewModel::onFillShiftClicked, enabled = enabled) {
        Text(stringResource(Res.string.daily_input_fill_shift_button))
    }
}

/**
 * 出勤・退勤・休憩の入力欄。
 *
 * @param shiftValue 空欄の場合に使う定時の値 (表示用)
 */
@Composable
private fun RowScope.WorkField(
    uiState: DailyInputDayUiState,
    field: WorkRecordField,
    value: String,
    onValueChange: (String) -> Unit,
    shiftValue: String,
    enabled: Boolean,
) {
    val error = uiState.violationOf(field)?.message()
    val label =
        when (field) {
            WorkRecordField.CLOCK_IN -> Res.string.daily_input_clock_in_label
            WorkRecordField.CLOCK_OUT -> Res.string.daily_input_clock_out_label
            else -> Res.string.daily_input_break_hours_label
        }
    val hint = if (field == WorkRecordField.BREAK_HOURS) Res.string.daily_input_hours_hint else Res.string.daily_input_time_hint
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        placeholder = { Text(stringResource(Res.string.daily_input_shift_placeholder, shiftValue)) },
        supportingText = { Text(error ?: stringResource(hint, shiftValue)) },
        isError = error != null,
        singleLine = true,
        enabled = enabled,
        modifier = Modifier.weight(1f),
    )
}

@Composable
private fun AllocationFields(
    uiState: DailyInputDayUiState,
    projects: List<AllocatableProject>,
    enabled: Boolean,
    onAllocationChange: (projectId: String, hours: String) -> Unit,
) {
    Text(text = stringResource(Res.string.daily_input_projects_header), style = MaterialTheme.typography.titleSmall)
    if (projects.isEmpty()) {
        Text(stringResource(Res.string.daily_input_projects_empty_message))
        return
    }
    projects.forEach { allocatable ->
        val project = allocatable.project
        val error = uiState.allocationViolations[project.id]?.message()
        val label =
            if (allocatable.isAssigned) {
                project.name
            } else {
                "${project.name} (${stringResource(Res.string.daily_input_project_unassigned_note)})"
            }
        OutlinedTextField(
            value = uiState.input.allocations[project.id].orEmpty(),
            onValueChange = { onAllocationChange(project.id, it) },
            label = { Text(label) },
            supportingText = { Text(error ?: stringResource(Res.string.daily_input_allocation_hint)) },
            isError = error != null,
            singleLine = true,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun Preview(uiState: DailyInputDayUiState) {
    val preview = uiState.preview ?: return
    val notCalculable = stringResource(Res.string.daily_input_not_calculable)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            stringResource(
                Res.string.daily_input_working_hours_preview,
                preview.workingHours?.hours?.let(::formatHours) ?: notCalculable,
            ),
        )
        Text(stringResource(Res.string.daily_input_allocated_hours_preview, preview.allocatedHours?.let(::formatHours) ?: notCalculable))
        val balance = preview.balance
        val hasImbalance = balance != null && !balance.isZero
        Text(
            text = stringResource(Res.string.daily_input_balance_preview, balance?.let(::formatSignedHours) ?: notCalculable),
            color = if (hasImbalance) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        )
        if (preview.workingHours?.isNegative == true || uiState.violationOf(WorkRecordField.WORKING_HOURS) != null) {
            Text(text = stringResource(Res.string.daily_input_negative_working_hours_warning), color = MaterialTheme.colorScheme.error)
        } else if (hasImbalance) {
            Text(text = stringResource(Res.string.daily_input_balance_warning), color = MaterialTheme.colorScheme.error)
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.daily_input_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.daily_input_delete_confirm_message)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.daily_input_delete_confirm_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.daily_input_cancel_button)) }
        },
    )
}

private const val DISABLED_ALPHA = 0.38f
