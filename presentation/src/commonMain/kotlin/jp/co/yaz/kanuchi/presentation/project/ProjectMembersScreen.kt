package jp.co.yaz.kanuchi.presentation.project

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.project_members_description
import kanuchi.presentation.generated.resources.project_members_project_name
import kanuchi.presentation.generated.resources.project_members_project_name_inactive
import kanuchi.presentation.generated.resources.project_members_save_button
import kanuchi.presentation.generated.resources.project_members_saved_message
import kanuchi.presentation.generated.resources.project_members_saving_button
import kanuchi.presentation.generated.resources.project_members_title
import kanuchi.presentation.generated.resources.project_members_user_label
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel
import org.koin.core.parameter.parametersOf

/**
 * 案件の担当メンバー画面 (admin のみ)。全ユーザーのチェックリストから担当メンバーを選んで保存する。
 * ここで設定した割当は、日々の入力でその人が選べる案件を絞るのに使う。
 *
 * @param projectId 対象の案件のID
 */
@Composable
fun ProjectMembersScreen(
    projectId: String,
    onBack: () -> Unit,
    viewModel: ProjectMembersViewModel = koinViewModel { parametersOf(projectId) },
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.project_members_title), onBack = onBack) {
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed -> {
                Text(stringResource(Res.string.common_data_load_error_message))
                TextButton(onClick = viewModel::onRetryClicked) { Text(stringResource(Res.string.common_retry_button)) }
            }
            else -> {
                ProjectMembersContent(
                    uiState = uiState,
                    onUserCheckedChange = viewModel::onUserCheckedChange,
                    onSave = viewModel::onSaveClicked,
                )
            }
        }
    }
}

@Composable
private fun ProjectMembersContent(
    uiState: ProjectMembersUiState,
    onUserCheckedChange: (userId: String, checked: Boolean) -> Unit,
    onSave: () -> Unit,
) {
    val project = uiState.project ?: return
    Text(
        text =
            if (project.isActive) {
                stringResource(Res.string.project_members_project_name, project.name)
            } else {
                stringResource(Res.string.project_members_project_name_inactive, project.name)
            },
        style = MaterialTheme.typography.titleMedium,
    )
    Text(stringResource(Res.string.project_members_description))

    uiState.users.forEach { user ->
        key(user.id) {
            UserCheckRow(
                user = user,
                checked = user.id in uiState.selectedUserIds,
                enabled = !uiState.isSaving,
                onCheckedChange = { checked -> onUserCheckedChange(user.id, checked) },
            )
        }
    }

    Button(
        onClick = onSave,
        enabled = !uiState.isSaving && uiState.hasChanges,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            if (uiState.isSaving) {
                stringResource(Res.string.project_members_saving_button)
            } else {
                stringResource(Res.string.project_members_save_button)
            },
        )
    }
    when {
        uiState.saveFailed ->
            Text(
                text = stringResource(Res.string.common_data_save_error_message),
                color = MaterialTheme.colorScheme.error,
            )
        uiState.showsSavedMessage -> Text(stringResource(Res.string.project_members_saved_message))
    }
}

@Composable
private fun UserCheckRow(
    user: UserProfile,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    // 行全体 (チェックボックスと名前) をクリックで切り替えられるようにする
    Row(
        modifier = Modifier.fillMaxWidth().toggleable(value = checked, enabled = enabled, onValueChange = onCheckedChange),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = checked, onCheckedChange = null, enabled = enabled)
        Text(
            text = stringResource(Res.string.project_members_user_label, user.displayName, user.email),
            modifier = Modifier.padding(start = 8.dp),
        )
    }
}
