package jp.co.yaz.kanuchi.presentation.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
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
import jp.co.yaz.kanuchi.domain.project.Project
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
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.ic_add
import kanuchi.presentation.generated.resources.ic_admin_panel_settings
import kanuchi.presentation.generated.resources.ic_delete
import kanuchi.presentation.generated.resources.ic_person
import kanuchi.presentation.generated.resources.ic_work
import kanuchi.presentation.generated.resources.my_projects_add_button
import kanuchi.presentation.generated.resources.my_projects_adding_button
import kanuchi.presentation.generated.resources.my_projects_assigned_description
import kanuchi.presentation.generated.resources.my_projects_assigned_empty_message
import kanuchi.presentation.generated.resources.my_projects_assigned_header
import kanuchi.presentation.generated.resources.my_projects_blank_name_error_message
import kanuchi.presentation.generated.resources.my_projects_cancel_button
import kanuchi.presentation.generated.resources.my_projects_delete_button
import kanuchi.presentation.generated.resources.my_projects_delete_confirm_button
import kanuchi.presentation.generated.resources.my_projects_delete_confirm_message
import kanuchi.presentation.generated.resources.my_projects_delete_confirm_title
import kanuchi.presentation.generated.resources.my_projects_delete_error_message
import kanuchi.presentation.generated.resources.my_projects_delete_in_use_error_message
import kanuchi.presentation.generated.resources.my_projects_delete_note
import kanuchi.presentation.generated.resources.my_projects_deleting_button
import kanuchi.presentation.generated.resources.my_projects_new_name_label
import kanuchi.presentation.generated.resources.my_projects_personal_description
import kanuchi.presentation.generated.resources.my_projects_personal_empty_message
import kanuchi.presentation.generated.resources.my_projects_personal_header
import kanuchi.presentation.generated.resources.my_projects_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 案件の編集画面 (個人設定から開く。全員が使える)。日次入力で工数を記録する案件を表示する。
 * 個人の案件は追加・削除 (確認ダイアログを経る) でき、管理者が割り当てた共有の案件は表示するだけ。
 */
@Composable
fun MyProjectsScreen(
    onBack: () -> Unit,
    viewModel: MyProjectsViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.my_projects_title), onBack = onBack) {
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed -> LoadErrorMessage(onRetry = viewModel::onRetryClicked)
            else -> {
                PersonalProjectsSection(
                    uiState = uiState,
                    onNameChange = viewModel::onNewProjectNameChanged,
                    onAdd = viewModel::onAddClicked,
                    onDelete = viewModel::onDeleteClicked,
                )
                HorizontalDivider()
                AssignedProjectsSection(uiState.assignedProjects)
            }
        }
    }

    uiState.deleteTarget?.let { target ->
        DeleteConfirmDialog(
            target = target,
            onConfirm = viewModel::onDeleteConfirmed,
            onDismiss = viewModel::onDeleteDismissed,
        )
    }
}

@Composable
private fun PersonalProjectsSection(
    uiState: MyProjectsUiState,
    onNameChange: (String) -> Unit,
    onAdd: () -> Unit,
    onDelete: (Project) -> Unit,
) {
    SectionHeader(Res.drawable.ic_person, stringResource(Res.string.my_projects_personal_header))
    Text(text = stringResource(Res.string.my_projects_personal_description))
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedTextField(
            value = uiState.newProjectName,
            onValueChange = onNameChange,
            label = { Text(stringResource(Res.string.my_projects_new_name_label)) },
            leadingIcon = { SimpleIcon(Res.drawable.ic_work) },
            singleLine = true,
            enabled = !uiState.isAdding,
            modifier = Modifier.weight(1f),
        )
        Button(onClick = onAdd, enabled = uiState.canEdit && uiState.newProjectName.isNotBlank()) {
            ButtonLabel(
                icon = Res.drawable.ic_add,
                text = stringResource(if (uiState.isAdding) Res.string.my_projects_adding_button else Res.string.my_projects_add_button),
                inProgress = uiState.isAdding,
            )
        }
    }
    when (uiState.addError) {
        PersonalProjectAddError.BLANK_NAME -> ErrorText(stringResource(Res.string.my_projects_blank_name_error_message))
        PersonalProjectAddError.SAVE_FAILED -> ErrorText(stringResource(Res.string.common_data_save_error_message))
        null -> Unit
    }
    Text(text = stringResource(Res.string.my_projects_delete_note), style = MaterialTheme.typography.bodySmall)

    if (uiState.personalProjects.isEmpty()) {
        EmptyMessage(stringResource(Res.string.my_projects_personal_empty_message))
    }
    uiState.personalProjects.forEach { project ->
        key(project.id) {
            HorizontalDivider()
            PersonalProjectRow(project = project, uiState = uiState, onDelete = { onDelete(project) })
        }
    }
}

@Composable
private fun PersonalProjectRow(
    project: Project,
    uiState: MyProjectsUiState,
    onDelete: () -> Unit,
) {
    val isDeletingThis = uiState.deletingProjectId == project.id
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(modifier = Modifier.weight(1f)) {
            IconText(icon = Res.drawable.ic_work, text = project.name)
        }
        TextButton(onClick = onDelete, enabled = uiState.canEdit) {
            ButtonLabel(
                icon = Res.drawable.ic_delete,
                text = stringResource(if (isDeletingThis) Res.string.my_projects_deleting_button else Res.string.my_projects_delete_button),
                inProgress = isDeletingThis,
            )
        }
    }
    if (uiState.deleteFailedProjectId == project.id) {
        when (uiState.deleteError) {
            PersonalProjectDeleteError.IN_USE -> ErrorText(stringResource(Res.string.my_projects_delete_in_use_error_message))
            PersonalProjectDeleteError.DELETE_FAILED -> ErrorText(stringResource(Res.string.my_projects_delete_error_message))
            null -> Unit
        }
    }
}

@Composable
private fun AssignedProjectsSection(projects: List<Project>) {
    SectionHeader(Res.drawable.ic_admin_panel_settings, stringResource(Res.string.my_projects_assigned_header))
    Text(text = stringResource(Res.string.my_projects_assigned_description))
    if (projects.isEmpty()) {
        EmptyMessage(stringResource(Res.string.my_projects_assigned_empty_message))
    }
    projects.forEach { project ->
        key(project.id) {
            HorizontalDivider()
            IconText(icon = Res.drawable.ic_work, text = project.name)
        }
    }
}

@Composable
private fun DeleteConfirmDialog(
    target: Project,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { SimpleIcon(Res.drawable.ic_delete) },
        title = { Text(stringResource(Res.string.my_projects_delete_confirm_title)) },
        text = { Text(stringResource(Res.string.my_projects_delete_confirm_message, target.name)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.my_projects_delete_confirm_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.my_projects_cancel_button)) }
        },
    )
}

@Composable
private fun ErrorText(text: String) {
    StatusMessage(text, StatusKind.ERROR)
}
