package jp.co.yaz.kanuchi.presentation.holiday

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayField
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayInput
import jp.co.yaz.kanuchi.presentation.common.ArrowButton
import jp.co.yaz.kanuchi.presentation.common.ButtonLabel
import jp.co.yaz.kanuchi.presentation.common.EmptyMessage
import jp.co.yaz.kanuchi.presentation.common.IconText
import jp.co.yaz.kanuchi.presentation.common.LoadErrorMessage
import jp.co.yaz.kanuchi.presentation.common.SectionHeader
import jp.co.yaz.kanuchi.presentation.common.SimpleIcon
import jp.co.yaz.kanuchi.presentation.common.StatusKind
import jp.co.yaz.kanuchi.presentation.common.StatusMessage
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.company_holidays_add_button
import kanuchi.presentation.generated.resources.company_holidays_added_message
import kanuchi.presentation.generated.resources.company_holidays_adding_button
import kanuchi.presentation.generated.resources.company_holidays_cancel_button
import kanuchi.presentation.generated.resources.company_holidays_date_hint
import kanuchi.presentation.generated.resources.company_holidays_delete_button
import kanuchi.presentation.generated.resources.company_holidays_delete_confirm_button
import kanuchi.presentation.generated.resources.company_holidays_delete_confirm_message
import kanuchi.presentation.generated.resources.company_holidays_delete_confirm_title
import kanuchi.presentation.generated.resources.company_holidays_delete_error
import kanuchi.presentation.generated.resources.company_holidays_deleting_button
import kanuchi.presentation.generated.resources.company_holidays_description
import kanuchi.presentation.generated.resources.company_holidays_empty_message
import kanuchi.presentation.generated.resources.company_holidays_end_date_label
import kanuchi.presentation.generated.resources.company_holidays_header
import kanuchi.presentation.generated.resources.company_holidays_item
import kanuchi.presentation.generated.resources.company_holidays_name_hint
import kanuchi.presentation.generated.resources.company_holidays_name_label
import kanuchi.presentation.generated.resources.company_holidays_national_holidays_description
import kanuchi.presentation.generated.resources.company_holidays_national_holidays_header
import kanuchi.presentation.generated.resources.company_holidays_overlaps_day_off_note
import kanuchi.presentation.generated.resources.company_holidays_start_date_label
import kanuchi.presentation.generated.resources.company_holidays_title
import kanuchi.presentation.generated.resources.company_holidays_year
import kanuchi.presentation.generated.resources.ic_add
import kanuchi.presentation.generated.resources.ic_calendar_today
import kanuchi.presentation.generated.resources.ic_chevron_left
import kanuchi.presentation.generated.resources.ic_chevron_right
import kanuchi.presentation.generated.resources.ic_delete
import kanuchi.presentation.generated.resources.ic_event
import kanuchi.presentation.generated.resources.ic_event_busy
import kanuchi.presentation.generated.resources.ic_info
import kanuchi.presentation.generated.resources.ic_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 休業日管理画面 (admin のみ)。年を切り替えて、その年の会社の休業日の一覧・追加 (1日または期間)・削除を行う。
 * 参考として、その年の日本の祝日 (自動判定。登録不要) の一覧も表示する。
 */
@Composable
fun CompanyHolidaysScreen(
    onBack: () -> Unit,
    viewModel: CompanyHolidaysViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.company_holidays_title), onBack = onBack) {
        YearSelector(
            uiState = uiState,
            onPrevious = viewModel::onPreviousYearClicked,
            onNext = viewModel::onNextYearClicked,
        )

        SectionHeader(Res.drawable.ic_event_busy, stringResource(Res.string.company_holidays_header))
        Text(text = stringResource(Res.string.company_holidays_description), style = MaterialTheme.typography.bodySmall)
        AddCompanyHolidayForm(uiState = uiState, onInputChange = viewModel::onInputChanged, onAdd = viewModel::onAddClicked)
        HorizontalDivider()

        uiState.deleteFailedHoliday?.let { failed ->
            ErrorText(stringResource(Res.string.company_holidays_delete_error, formatDate(failed.date), failed.name))
        }
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed -> LoadErrorMessage(onRetry = viewModel::onRetryClicked)
            uiState.companyHolidays.isEmpty() -> EmptyMessage(stringResource(Res.string.company_holidays_empty_message))
            else ->
                uiState.companyHolidays.forEach { holiday ->
                    key(holiday.date) {
                        CompanyHolidayRow(holiday = holiday, uiState = uiState, onDelete = { viewModel.onDeleteClicked(holiday) })
                        HorizontalDivider()
                    }
                }
        }

        NationalHolidays(uiState.nationalHolidays)
    }

    uiState.deleteTarget?.let { target ->
        DeleteConfirmationDialog(target = target, onConfirm = viewModel::onDeleteConfirmed, onDismiss = viewModel::onDeleteDismissed)
    }
}

@Composable
private fun YearSelector(
    uiState: CompanyHolidaysUiState,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ArrowButton(icon = Res.drawable.ic_chevron_left, onClick = onPrevious, enabled = uiState.canGoToPreviousYear)
        Text(
            text = stringResource(Res.string.company_holidays_year, uiState.year.toString()),
            style = MaterialTheme.typography.titleMedium,
        )
        ArrowButton(icon = Res.drawable.ic_chevron_right, onClick = onNext, enabled = uiState.canGoToNextYear)
    }
}

@Composable
private fun AddCompanyHolidayForm(
    uiState: CompanyHolidaysUiState,
    onInputChange: (CompanyHolidayInput) -> Unit,
    onAdd: () -> Unit,
) {
    val input = uiState.input
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        InputField(
            value = input.startDate,
            onValueChange = { onInputChange(input.copy(startDate = it)) },
            field = CompanyHolidayField.START_DATE,
            uiState = uiState,
            modifier = Modifier.weight(1f),
        )
        InputField(
            value = input.endDate,
            onValueChange = { onInputChange(input.copy(endDate = it)) },
            field = CompanyHolidayField.END_DATE,
            uiState = uiState,
            modifier = Modifier.weight(1f),
        )
    }
    Text(
        text = stringResource(Res.string.company_holidays_date_hint, CompanyHolidayInput.MAX_DAYS.toString()),
        style = MaterialTheme.typography.bodySmall,
    )
    InputField(
        value = input.name,
        onValueChange = { onInputChange(input.copy(name = it)) },
        field = CompanyHolidayField.NAME,
        uiState = uiState,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(Res.string.company_holidays_name_hint, CompanyHoliday.MAX_NAME_LENGTH.toString()),
        style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onAdd, enabled = uiState.canEdit && input.startDate.isNotBlank() && input.name.isNotBlank()) {
        ButtonLabel(
            icon = Res.drawable.ic_add,
            text =
                if (uiState.isAdding) {
                    stringResource(Res.string.company_holidays_adding_button)
                } else {
                    stringResource(Res.string.company_holidays_add_button)
                },
            inProgress = uiState.isAdding,
        )
    }
    uiState.addError?.let { ErrorText(it.message()) }
    uiState.addedDayCount?.let {
        StatusMessage(stringResource(Res.string.company_holidays_added_message, it.toString()), StatusKind.SUCCESS)
    }
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    field: CompanyHolidayField,
    uiState: CompanyHolidaysUiState,
    modifier: Modifier,
) {
    val (label, icon) =
        when (field) {
            CompanyHolidayField.START_DATE -> Res.string.company_holidays_start_date_label to Res.drawable.ic_calendar_today
            CompanyHolidayField.END_DATE -> Res.string.company_holidays_end_date_label to Res.drawable.ic_calendar_today
            CompanyHolidayField.NAME -> Res.string.company_holidays_name_label to Res.drawable.ic_label
        }
    val violations = uiState.inputViolations.filter { it.field == field }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(stringResource(label)) },
            leadingIcon = { SimpleIcon(icon) },
            singleLine = true,
            enabled = !uiState.isAdding,
            isError = violations.isNotEmpty(),
            modifier = Modifier.fillMaxWidth(),
        )
        violations.forEach { ErrorText(it.message()) }
    }
}

@Composable
private fun CompanyHolidayRow(
    holiday: CompanyHoliday,
    uiState: CompanyHolidaysUiState,
    onDelete: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(stringResource(Res.string.company_holidays_item, formatDate(holiday.date), holiday.name))
            if (holiday.date.isWeekendOrNationalHoliday()) {
                IconText(
                    icon = Res.drawable.ic_info,
                    text = stringResource(Res.string.company_holidays_overlaps_day_off_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    iconSize = 16.dp,
                )
            }
        }
        TextButton(onClick = onDelete, enabled = uiState.canEdit) {
            ButtonLabel(
                icon = Res.drawable.ic_delete,
                text =
                    if (uiState.deletingDate == holiday.date) {
                        stringResource(Res.string.company_holidays_deleting_button)
                    } else {
                        stringResource(Res.string.company_holidays_delete_button)
                    },
                inProgress = uiState.deletingDate == holiday.date,
            )
        }
    }
}

@Composable
private fun NationalHolidays(nationalHolidays: List<NationalHolidayItem>) {
    SectionHeader(Res.drawable.ic_event, stringResource(Res.string.company_holidays_national_holidays_header))
    Text(text = stringResource(Res.string.company_holidays_national_holidays_description), style = MaterialTheme.typography.bodySmall)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        nationalHolidays.forEach { holiday ->
            Text(stringResource(Res.string.company_holidays_item, formatDate(holiday.date), holiday.name))
        }
    }
}

@Composable
private fun DeleteConfirmationDialog(
    target: CompanyHoliday,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { SimpleIcon(Res.drawable.ic_delete) },
        title = { Text(stringResource(Res.string.company_holidays_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.company_holidays_delete_confirm_message, target.date.toString(), target.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.company_holidays_delete_confirm_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.company_holidays_cancel_button)) }
        },
    )
}

@Composable
private fun ErrorText(text: String) {
    StatusMessage(text, StatusKind.ERROR)
}
