package jp.co.yaz.kanuchi.presentation.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.roles_cancel_button
import kanuchi.presentation.generated.resources.roles_change_success_message
import kanuchi.presentation.generated.resources.roles_changing_button
import kanuchi.presentation.generated.resources.roles_confirm_button
import kanuchi.presentation.generated.resources.roles_current_user_name
import kanuchi.presentation.generated.resources.roles_demote_button
import kanuchi.presentation.generated.resources.roles_demote_confirm_message
import kanuchi.presentation.generated.resources.roles_demote_confirm_title
import kanuchi.presentation.generated.resources.roles_description
import kanuchi.presentation.generated.resources.roles_last_active_admin_error_message
import kanuchi.presentation.generated.resources.roles_promote_button
import kanuchi.presentation.generated.resources.roles_promote_confirm_message
import kanuchi.presentation.generated.resources.roles_promote_confirm_title
import kanuchi.presentation.generated.resources.roles_role_admin
import kanuchi.presentation.generated.resources.roles_role_member
import kanuchi.presentation.generated.resources.roles_suspended_label
import kanuchi.presentation.generated.resources.roles_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * 権限管理画面 (admin のみ)。全ユーザーの一覧を表示し、member → admin への昇格・admin → member への降格を行う。
 * 押し間違いを防ぐため、変更前に確認ダイアログを表示する。
 * 自分自身と利用停止中のユーザーは変更できない ([jp.co.yaz.kanuchi.domain.role.RoleManagementOverview.canChangeRole])。
 */
@Composable
fun RolesScreen(
    onBack: () -> Unit,
    viewModel: RolesViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.roles_title), onBack = onBack) {
        Text(stringResource(Res.string.roles_description))
        SaveStatus(uiState)
        when {
            uiState.isLoading -> CircularProgressIndicator()
            uiState.loadFailed -> {
                Text(stringResource(Res.string.common_data_load_error_message))
                TextButton(onClick = viewModel::onRetryClicked) { Text(stringResource(Res.string.common_retry_button)) }
            }
            else ->
                uiState.users.forEach { item ->
                    HorizontalDivider()
                    UserRow(
                        item = item,
                        savingChange = uiState.savingChange,
                        enabled = !uiState.isSaving,
                        onChangeRole = { newRole -> viewModel.onChangeRoleClicked(item.profile.id, newRole) },
                    )
                }
        }
    }

    uiState.pendingChange?.let { change ->
        ConfirmDialog(
            change = change,
            onConfirm = viewModel::onChangeRoleConfirmed,
            onDismiss = viewModel::onChangeRoleDismissed,
        )
    }
}

@Composable
private fun SaveStatus(uiState: RolesUiState) {
    uiState.completedChange?.let { change ->
        Text(stringResource(Res.string.roles_change_success_message, change.user.displayName, stringResource(change.newRole.label())))
    }
    when (uiState.saveError) {
        RoleChangeError.LAST_ACTIVE_ADMIN_REQUIRED ->
            Text(stringResource(Res.string.roles_last_active_admin_error_message), color = MaterialTheme.colorScheme.error)
        RoleChangeError.GENERIC -> Text(stringResource(Res.string.common_data_save_error_message), color = MaterialTheme.colorScheme.error)
        null -> Unit
    }
}

@Composable
private fun UserRow(
    item: RoleUserItem,
    savingChange: RoleChange?,
    enabled: Boolean,
    onChangeRole: (UserRole) -> Unit,
) {
    val profile = item.profile
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text =
                    if (item.isCurrentUser) {
                        stringResource(Res.string.roles_current_user_name, profile.displayName)
                    } else {
                        profile.displayName
                    },
                style = MaterialTheme.typography.titleSmall,
            )
            Text(text = profile.email, style = MaterialTheme.typography.bodySmall)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(profile.role.label()))
                if (profile.isSuspended) {
                    Text(stringResource(Res.string.roles_suspended_label), color = MaterialTheme.colorScheme.error)
                }
            }
        }
        if (item.canChangeRole) {
            val newRole = if (profile.isAdmin) UserRole.MEMBER else UserRole.ADMIN
            val isSavingThisUser = savingChange?.user?.id == profile.id
            OutlinedButton(onClick = { onChangeRole(newRole) }, enabled = enabled) {
                if (isSavingThisUser) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp).size(16.dp), strokeWidth = 2.dp)
                    Text(stringResource(Res.string.roles_changing_button))
                } else {
                    Text(
                        stringResource(
                            if (newRole == UserRole.ADMIN) Res.string.roles_promote_button else Res.string.roles_demote_button,
                        ),
                    )
                }
            }
        }
    }
}

@Composable
private fun ConfirmDialog(
    change: RoleChange,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val promotes = change.newRole == UserRole.ADMIN
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(stringResource(if (promotes) Res.string.roles_promote_confirm_title else Res.string.roles_demote_confirm_title))
        },
        text = {
            Text(
                stringResource(
                    if (promotes) Res.string.roles_promote_confirm_message else Res.string.roles_demote_confirm_message,
                    change.user.displayName,
                    change.user.email,
                ),
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.roles_confirm_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.roles_cancel_button)) }
        },
    )
}

private fun UserRole.label(): StringResource =
    when (this) {
        UserRole.ADMIN -> Res.string.roles_role_admin
        UserRole.MEMBER -> Res.string.roles_role_member
    }
