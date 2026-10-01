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
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.company_holidays_add_button
import kanuchi.presentation.generated.resources.company_holidays_added_message
import kanuchi.presentation.generated.resources.company_holidays_adding_button
import kanuchi.presentation.generated.resources.company_holidays_cancel_button
import kanuchi.presentation.generated.resources.company_holidays_date_hint
import kanuchi.presentation.generated.resources.company_holidays_delete_button
import kanuchi.presentation.generated.resources.company_holidays_delete_confirm_button
import kanuchi.presentation.generated.resources.company_holidays_delete_confirm_message
import kanuchi.presentation.generated.resources.company_holidays_delete_confirm_title
import kanuchi.presentation.generated.resources.company_holidays_deleting_button
import kanuchi.presentation.generated.resources.company_holidays_description
import kanuchi.presentation.generated.resources.company_holidays_empty_message
import kanuchi.presentation.generated.resources.company_holidays_end_date_label
import kanuchi.presentation.generated.resources.company_holidays_header
import kanuchi.presentation.generated.resources.company_holidays_name_hint
import kanuchi.presentation.generated.resources.company_holidays_name_label
import kanuchi.presentation.generated.resources.company_holidays_national_holidays_description
import kanuchi.presentation.generated.resources.company_holidays_national_holidays_header
import kanuchi.presentation.generated.resources.company_holidays_next_year_button
import kanuchi.presentation.generated.resources.company_holidays_overlaps_day_off_note
import kanuchi.presentation.generated.resources.company_holidays_previous_year_button
import kanuchi.presentation.generated.resources.company_holidays_start_date_label
import kanuchi.presentation.generated.resources.company_holidays_title
import kanuchi.presentation.generated.resources.company_holidays_year
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

        Text(text = stringResource(Res.string.company_holidays_header), style = MaterialTheme.typography.titleMedium)
        Text(text = stringResource(Res.string.company_holidays_description), style = MaterialTheme.typography.bodySmall)
        AddCompanyHolidayForm(uiState = uiState, onInputChange = viewModel::onInputChanged, onAdd = viewModel::onAddClicked)
        HorizontalDivider()

        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed -> {
                Text(stringResource(Res.string.common_data_load_error_message))
                TextButton(onClick = viewModel::onRetryClicked) { Text(stringResource(Res.string.common_retry_button)) }
            }
            uiState.companyHolidays.isEmpty() -> Text(stringResource(Res.string.company_holidays_empty_message))
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
        TextButton(onClick = onPrevious, enabled = uiState.canGoToPreviousYear) {
            Text(stringResource(Res.string.company_holidays_previous_year_button))
        }
        Text(
            text = stringResource(Res.string.company_holidays_year, uiState.year.toString()),
            style = MaterialTheme.typography.titleMedium,
        )
        TextButton(onClick = onNext, enabled = uiState.canGoToNextYear) {
            Text(stringResource(Res.string.company_holidays_next_year_button))
        }
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
            label = stringResource(Res.string.company_holidays_start_date_label),
            field = CompanyHolidayField.START_DATE,
            uiState = uiState,
            modifier = Modifier.weight(1f),
        )
        InputField(
            value = input.endDate,
            onValueChange = { onInputChange(input.copy(endDate = it)) },
            label = stringResource(Res.string.company_holidays_end_date_label),
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
        label = stringResource(Res.string.company_holidays_name_label),
        field = CompanyHolidayField.NAME,
        uiState = uiState,
        modifier = Modifier.fillMaxWidth(),
    )
    Text(
        text = stringResource(Res.string.company_holidays_name_hint, CompanyHoliday.MAX_NAME_LENGTH.toString()),
        style = MaterialTheme.typography.bodySmall,
    )
    Button(onClick = onAdd, enabled = uiState.canEdit && input.startDate.isNotBlank() && input.name.isNotBlank()) {
        Text(
            if (uiState.isAdding) {
                stringResource(Res.string.company_holidays_adding_button)
            } else {
                stringResource(Res.string.company_holidays_add_button)
            },
        )
    }
    uiState.addError?.let { ErrorText(it.message()) }
    uiState.addedDayCount?.let { Text(stringResource(Res.string.company_holidays_added_message, it.toString())) }
}

@Composable
private fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    field: CompanyHolidayField,
    uiState: CompanyHolidaysUiState,
    modifier: Modifier,
) {
    val violations = uiState.inputViolations.filter { it.field == field }
    Column(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            label = { Text(label) },
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
            Text("${formatDate(holiday.date)}  ${holiday.name}")
            if (holiday.date.isWeekendOrNationalHoliday()) {
                Text(
                    text = stringResource(Res.string.company_holidays_overlaps_day_off_note),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        TextButton(onClick = onDelete, enabled = uiState.canEdit) {
            Text(
                if (uiState.deletingDate == holiday.date) {
                    stringResource(Res.string.company_holidays_deleting_button)
                } else {
                    stringResource(Res.string.company_holidays_delete_button)
                },
            )
        }
    }
    if (uiState.deleteFailedDate == holiday.date) {
        ErrorText(stringResource(Res.string.common_data_save_error_message))
    }
}

@Composable
private fun NationalHolidays(nationalHolidays: List<NationalHolidayItem>) {
    Text(text = stringResource(Res.string.company_holidays_national_holidays_header), style = MaterialTheme.typography.titleMedium)
    Text(text = stringResource(Res.string.company_holidays_national_holidays_description), style = MaterialTheme.typography.bodySmall)
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        nationalHolidays.forEach { holiday ->
            Text("${formatDate(holiday.date)}  ${holiday.name}")
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
    Text(text = text, color = MaterialTheme.colorScheme.error, modifier = Modifier.fillMaxWidth())
}
