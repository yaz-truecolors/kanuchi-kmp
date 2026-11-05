package jp.co.yaz.kanuchi.presentation.project

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.project.Project
import jp.co.yaz.kanuchi.presentation.common.ButtonLabel
import jp.co.yaz.kanuchi.presentation.common.EmptyMessage
import jp.co.yaz.kanuchi.presentation.common.IconText
import jp.co.yaz.kanuchi.presentation.common.LoadErrorMessage
import jp.co.yaz.kanuchi.presentation.common.SimpleIcon
import jp.co.yaz.kanuchi.presentation.common.StatusKind
import jp.co.yaz.kanuchi.presentation.common.StatusMessage
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.ic_add
import kanuchi.presentation.generated.resources.ic_close
import kanuchi.presentation.generated.resources.ic_edit
import kanuchi.presentation.generated.resources.ic_group
import kanuchi.presentation.generated.resources.ic_save
import kanuchi.presentation.generated.resources.ic_toggle_off
import kanuchi.presentation.generated.resources.ic_toggle_on
import kanuchi.presentation.generated.resources.ic_work
import kanuchi.presentation.generated.resources.projects_activate_button
import kanuchi.presentation.generated.resources.projects_add_button
import kanuchi.presentation.generated.resources.projects_adding_button
import kanuchi.presentation.generated.resources.projects_blank_name_error_message
import kanuchi.presentation.generated.resources.projects_cancel_button
import kanuchi.presentation.generated.resources.projects_deactivate_button
import kanuchi.presentation.generated.resources.projects_duplicate_name_error_message
import kanuchi.presentation.generated.resources.projects_empty_message
import kanuchi.presentation.generated.resources.projects_inactive_note
import kanuchi.presentation.generated.resources.projects_members_button
import kanuchi.presentation.generated.resources.projects_name_label
import kanuchi.presentation.generated.resources.projects_new_name_label
import kanuchi.presentation.generated.resources.projects_rename_button
import kanuchi.presentation.generated.resources.projects_save_button
import kanuchi.presentation.generated.resources.projects_saving_button
import kanuchi.presentation.generated.resources.projects_status_active
import kanuchi.presentation.generated.resources.projects_status_inactive
import kanuchi.presentation.generated.resources.projects_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 案件管理画面 (admin のみ)。共有の案件の一覧 (有効な案件が先)・追加・名前変更・有効／無効の切り替えを行い、
 * 各案件の担当メンバー画面を開く。案件は削除できない (使わなくなった案件は無効にする)。
 * 各ユーザーが個人設定で追加した個人の案件は表示しない。
 *
 * @param onOpenMembers 「担当メンバー」が押されたときに、案件のIDを受け取る
 */
@Composable
fun ProjectsScreen(
    onBack: () -> Unit,
    onOpenMembers: (projectId: String) -> Unit,
    viewModel: ProjectsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.projects_title), onBack = onBack) {
        AddProjectForm(
            uiState = uiState,
            onNameChange = viewModel::onNewProjectNameChanged,
            onAdd = viewModel::onAddClicked,
        )
        Text(text = stringResource(Res.string.projects_inactive_note), style = MaterialTheme.typography.bodySmall)
        HorizontalDivider()

        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed -> LoadErrorMessage(onRetry = viewModel::onRetryClicked)
            uiState.projects.isEmpty() -> EmptyMessage(stringResource(Res.string.projects_empty_message))
            else ->
                uiState.projects.forEach { project ->
                    key(project.id) {
                        if (uiState.renamingProjectId == project.id) {
                            RenameProjectRow(
                                uiState = uiState,
                                onNameChange = viewModel::onRenamingNameChanged,
                                onSave = viewModel::onRenameSaveClicked,
                                onCancel = viewModel::onRenameCancelled,
                            )
                        } else {
                            ProjectRow(
                                project = project,
                                uiState = uiState,
                                onRename = { viewModel.onRenameClicked(project) },
                                onToggleActive = { viewModel.onToggleActiveClicked(project) },
                                onOpenMembers = { onOpenMembers(project.id) },
                            )
                        }
                        HorizontalDivider()
                    }
                }
        }
    }
}

@Composable
private fun AddProjectForm(
    uiState: ProjectsUiState,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = uiState.newProjectName,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.projects_new_name_label)) },
            leadingIcon = { SimpleIcon(Res.drawable.ic_work) },
            singleLine = true,
            enabled = !uiState.isAdding,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = onAdd,
            enabled = uiState.canAdd && uiState.newProjectName.isNotBlank(),
        ) {
            ButtonLabel(
                icon = Res.drawable.ic_add,
                text =
                    if (uiState.isAdding) {
                        stringResource(Res.string.projects_adding_button)
                    } else {
                        stringResource(Res.string.projects_add_button)
                    },
                inProgress = uiState.isAdding,
            )
        }
    }
    uiState.addError?.let { ErrorText(it.message()) }
}

@Composable
private fun ProjectRow(
    project: Project,
    uiState: ProjectsUiState,
    onRename: () -> Unit,
    onToggleActive: () -> Unit,
    onOpenMembers: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = project.name,
                color = if (project.isActive) Color.Unspecified else MaterialTheme.colorScheme.onSurfaceVariant,
            )
            IconText(
                icon = if (project.isActive) Res.drawable.ic_toggle_on else Res.drawable.ic_toggle_off,
                text =
                    if (project.isActive) {
                        stringResource(Res.string.projects_status_active)
                    } else {
                        stringResource(Res.string.projects_status_inactive)
                    },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                iconTint = if (project.isActive) MaterialTheme.colorScheme.primary else Color.Unspecified,
                iconSize = 16.dp,
            )
        }
        TextButton(onClick = onRename, enabled = !uiState.isSaving) {
            ButtonLabel(Res.drawable.ic_edit, stringResource(Res.string.projects_rename_button))
        }
        TextButton(onClick = onToggleActive, enabled = !uiState.isSaving) {
            ButtonLabel(
                // 押したあとの状態のアイコン (有効な案件なら「無効にする」= オフ)
                icon = if (project.isActive) Res.drawable.ic_toggle_off else Res.drawable.ic_toggle_on,
                text =
                    when {
                        uiState.savingProjectId == project.id -> stringResource(Res.string.projects_saving_button)
                        project.isActive -> stringResource(Res.string.projects_deactivate_button)
                        else -> stringResource(Res.string.projects_activate_button)
                    },
                inProgress = uiState.savingProjectId == project.id,
            )
        }
        TextButton(onClick = onOpenMembers) {
            ButtonLabel(Res.drawable.ic_group, stringResource(Res.string.projects_members_button))
        }
    }
    if (uiState.activeChangeFailedProjectId == project.id) {
        ErrorText(stringResource(Res.string.common_data_save_error_message))
    }
}

@Composable
private fun RenameProjectRow(
    uiState: ProjectsUiState,
    onNameChange: (String) -> Unit,
    onSave: () -> Unit,
    onCancel: () -> Unit,
) {
    val isSavingThis = uiState.savingProjectId != null && uiState.savingProjectId == uiState.renamingProjectId
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = uiState.renamingName,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.projects_name_label)) },
            leadingIcon = { SimpleIcon(Res.drawable.ic_work) },
            singleLine = true,
            enabled = !isSavingThis,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = onSave, enabled = !uiState.isSaving && uiState.renamingName.isNotBlank()) {
            ButtonLabel(
                icon = Res.drawable.ic_save,
                text =
                    if (isSavingThis) {
                        stringResource(Res.string.projects_saving_button)
                    } else {
                        stringResource(Res.string.projects_save_button)
                    },
                inProgress = isSavingThis,
            )
        }
        TextButton(onClick = onCancel, enabled = !isSavingThis) {
            ButtonLabel(Res.drawable.ic_close, stringResource(Res.string.projects_cancel_button))
        }
    }
    uiState.renameError?.let { ErrorText(it.message()) }
}

@Composable
private fun ErrorText(text: String) {
    StatusMessage(text, StatusKind.ERROR)
}

@Composable
private fun ProjectNameSaveError.message(): String =
    when (this) {
        ProjectNameSaveError.BLANK_NAME -> stringResource(Res.string.projects_blank_name_error_message)
        ProjectNameSaveError.DUPLICATE_NAME -> stringResource(Res.string.projects_duplicate_name_error_message)
        ProjectNameSaveError.SAVE_FAILED -> stringResource(Res.string.common_data_save_error_message)
    }
