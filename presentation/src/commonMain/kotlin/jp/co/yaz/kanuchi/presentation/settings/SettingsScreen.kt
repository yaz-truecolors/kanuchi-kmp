package jp.co.yaz.kanuchi.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.profile.DisplayName
import jp.co.yaz.kanuchi.domain.profile.DisplayNameViolation
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsField
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsInput
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsViolation
import jp.co.yaz.kanuchi.presentation.common.ButtonLabel
import jp.co.yaz.kanuchi.presentation.common.LoadErrorMessage
import jp.co.yaz.kanuchi.presentation.common.SectionHeader
import jp.co.yaz.kanuchi.presentation.common.SimpleIcon
import jp.co.yaz.kanuchi.presentation.common.StatusKind
import jp.co.yaz.kanuchi.presentation.common.StatusMessage
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.ic_badge
import kanuchi.presentation.generated.resources.ic_coffee
import kanuchi.presentation.generated.resources.ic_save
import kanuchi.presentation.generated.resources.ic_schedule
import kanuchi.presentation.generated.resources.ic_timer
import kanuchi.presentation.generated.resources.settings_break_hours_label
import kanuchi.presentation.generated.resources.settings_display_name_blank_error
import kanuchi.presentation.generated.resources.settings_display_name_header
import kanuchi.presentation.generated.resources.settings_display_name_hint
import kanuchi.presentation.generated.resources.settings_display_name_label
import kanuchi.presentation.generated.resources.settings_display_name_saved_message
import kanuchi.presentation.generated.resources.settings_display_name_too_long_error
import kanuchi.presentation.generated.resources.settings_end_time_label
import kanuchi.presentation.generated.resources.settings_end_time_not_after_start_time_error
import kanuchi.presentation.generated.resources.settings_hours_format_error
import kanuchi.presentation.generated.resources.settings_hours_hint
import kanuchi.presentation.generated.resources.settings_hours_too_large_error
import kanuchi.presentation.generated.resources.settings_max_hours_label
import kanuchi.presentation.generated.resources.settings_min_hours_exceeds_max_hours_error
import kanuchi.presentation.generated.resources.settings_min_hours_label
import kanuchi.presentation.generated.resources.settings_save_button
import kanuchi.presentation.generated.resources.settings_saving_button
import kanuchi.presentation.generated.resources.settings_shift_description
import kanuchi.presentation.generated.resources.settings_shift_header
import kanuchi.presentation.generated.resources.settings_shift_saved_message
import kanuchi.presentation.generated.resources.settings_start_time_label
import kanuchi.presentation.generated.resources.settings_time_format_error
import kanuchi.presentation.generated.resources.settings_time_hint
import kanuchi.presentation.generated.resources.settings_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 個人設定画面。ログイン中のユーザー自身の表示名と勤務時間設定を変更する (全ユーザーが使える)。
 * 表示名と勤務時間設定は、それぞれの「保存」で別々に保存する。
 */
@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.settings_title), onBack = onBack) {
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed -> LoadErrorMessage(onRetry = viewModel::onRetryClicked)
            else -> {
                DisplayNameSection(
                    uiState = uiState,
                    onDisplayNameChanged = viewModel::onDisplayNameChanged,
                    onSave = viewModel::onSaveDisplayNameClicked,
                )
                HorizontalDivider()
                ShiftSettingsSection(
                    uiState = uiState,
                    onChanged = viewModel::onShiftSettingsChanged,
                    onSave = viewModel::onSaveShiftSettingsClicked,
                )
            }
        }
    }
}

@Composable
private fun DisplayNameSection(
    uiState: SettingsUiState,
    onDisplayNameChanged: (String) -> Unit,
    onSave: () -> Unit,
) {
    val maxLength = DisplayName.MAX_LENGTH.toString()
    val error =
        when (uiState.displayNameViolation) {
            DisplayNameViolation.BLANK -> stringResource(Res.string.settings_display_name_blank_error)
            DisplayNameViolation.TOO_LONG -> stringResource(Res.string.settings_display_name_too_long_error, maxLength)
            null -> null
        }

    SectionHeader(Res.drawable.ic_badge, stringResource(Res.string.settings_display_name_header))
    // 入力欄の下には、エラーがあればエラーを、無ければ入力の説明を表示する (勤務時間の入力欄も同じ)
    OutlinedTextField(
        value = uiState.displayName,
        onValueChange = onDisplayNameChanged,
        label = { Text(stringResource(Res.string.settings_display_name_label)) },
        leadingIcon = { SimpleIcon(Res.drawable.ic_badge) },
        supportingText = { Text(error ?: stringResource(Res.string.settings_display_name_hint, maxLength)) },
        isError = error != null,
        singleLine = true,
        enabled = !uiState.isSavingDisplayName,
        modifier = Modifier.fillMaxWidth(),
    )
    SaveButton(isSaving = uiState.isSavingDisplayName, onClick = onSave)
    when {
        uiState.displayNameSaved -> StatusMessage(stringResource(Res.string.settings_display_name_saved_message), StatusKind.SUCCESS)
        uiState.displayNameSaveFailed -> StatusMessage(stringResource(Res.string.common_data_save_error_message), StatusKind.ERROR)
    }
}

@Composable
private fun ShiftSettingsSection(
    uiState: SettingsUiState,
    onChanged: (ShiftSettingsInput) -> Unit,
    onSave: () -> Unit,
) {
    val input = uiState.shiftSettings

    SectionHeader(Res.drawable.ic_schedule, stringResource(Res.string.settings_shift_header))
    Text(text = stringResource(Res.string.settings_shift_description))
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        ShiftField(
            uiState = uiState,
            field = ShiftSettingsField.START_TIME,
            value = input.startTime,
            onValueChange = { onChanged(input.copy(startTime = it)) },
            label = Res.string.settings_start_time_label,
            modifier = Modifier.weight(1f),
        )
        ShiftField(
            uiState = uiState,
            field = ShiftSettingsField.END_TIME,
            value = input.endTime,
            onValueChange = { onChanged(input.copy(endTime = it)) },
            label = Res.string.settings_end_time_label,
            modifier = Modifier.weight(1f),
        )
    }
    ShiftField(
        uiState = uiState,
        field = ShiftSettingsField.BREAK_HOURS,
        value = input.breakHours,
        onValueChange = { onChanged(input.copy(breakHours = it)) },
        label = Res.string.settings_break_hours_label,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.fillMaxWidth()) {
        ShiftField(
            uiState = uiState,
            field = ShiftSettingsField.MIN_HOURS,
            value = input.minHours,
            onValueChange = { onChanged(input.copy(minHours = it)) },
            label = Res.string.settings_min_hours_label,
            modifier = Modifier.weight(1f),
        )
        ShiftField(
            uiState = uiState,
            field = ShiftSettingsField.MAX_HOURS,
            value = input.maxHours,
            onValueChange = { onChanged(input.copy(maxHours = it)) },
            label = Res.string.settings_max_hours_label,
            modifier = Modifier.weight(1f),
        )
    }
    SaveButton(isSaving = uiState.isSavingShiftSettings, onClick = onSave)
    when {
        uiState.shiftSettingsSaved -> StatusMessage(stringResource(Res.string.settings_shift_saved_message), StatusKind.SUCCESS)
        uiState.shiftSettingsSaveFailed -> StatusMessage(stringResource(Res.string.common_data_save_error_message), StatusKind.ERROR)
    }
}

@Composable
private fun ShiftField(
    uiState: SettingsUiState,
    field: ShiftSettingsField,
    value: String,
    onValueChange: (String) -> Unit,
    label: StringResource,
    modifier: Modifier,
) {
    val isTime = field == ShiftSettingsField.START_TIME || field == ShiftSettingsField.END_TIME
    val icon =
        when (field) {
            ShiftSettingsField.START_TIME, ShiftSettingsField.END_TIME -> Res.drawable.ic_schedule
            ShiftSettingsField.BREAK_HOURS -> Res.drawable.ic_coffee
            ShiftSettingsField.MIN_HOURS, ShiftSettingsField.MAX_HOURS -> Res.drawable.ic_timer
        }
    val error = uiState.shiftSettingsViolationOf(field)?.message()
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(stringResource(label)) },
        leadingIcon = { SimpleIcon(icon) },
        supportingText = {
            Text(error ?: stringResource(if (isTime) Res.string.settings_time_hint else Res.string.settings_hours_hint))
        },
        isError = error != null,
        singleLine = true,
        enabled = !uiState.isSavingShiftSettings,
        modifier = modifier,
    )
}

@Composable
private fun ShiftSettingsViolation.message(): String =
    when (this) {
        ShiftSettingsViolation.START_TIME_INVALID_FORMAT,
        ShiftSettingsViolation.END_TIME_INVALID_FORMAT,
        -> stringResource(Res.string.settings_time_format_error)
        ShiftSettingsViolation.END_TIME_NOT_AFTER_START_TIME -> stringResource(Res.string.settings_end_time_not_after_start_time_error)
        ShiftSettingsViolation.BREAK_HOURS_INVALID_FORMAT,
        ShiftSettingsViolation.MIN_HOURS_INVALID_FORMAT,
        ShiftSettingsViolation.MAX_HOURS_INVALID_FORMAT,
        -> stringResource(Res.string.settings_hours_format_error)
        ShiftSettingsViolation.BREAK_HOURS_TOO_LARGE ->
            stringResource(Res.string.settings_hours_too_large_error, ShiftSettings.MAX_BREAK_HOURS.toString())
        ShiftSettingsViolation.MIN_HOURS_TOO_LARGE,
        ShiftSettingsViolation.MAX_HOURS_TOO_LARGE,
        -> stringResource(Res.string.settings_hours_too_large_error, ShiftSettings.MAX_MONTHLY_HOURS.toString())
        ShiftSettingsViolation.MIN_HOURS_EXCEEDS_MAX_HOURS -> stringResource(Res.string.settings_min_hours_exceeds_max_hours_error)
    }

@Composable
private fun SaveButton(
    isSaving: Boolean,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, enabled = !isSaving, modifier = Modifier.fillMaxWidth()) {
        ButtonLabel(
            icon = Res.drawable.ic_save,
            text = stringResource(if (isSaving) Res.string.settings_saving_button else Res.string.settings_save_button),
            inProgress = isSaving,
        )
    }
}
