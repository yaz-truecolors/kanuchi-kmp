package jp.co.yaz.kanuchi.presentation.role

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
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
import jp.co.yaz.kanuchi.presentation.common.ButtonLabel
import jp.co.yaz.kanuchi.presentation.common.IconText
import jp.co.yaz.kanuchi.presentation.common.LoadErrorMessage
import jp.co.yaz.kanuchi.presentation.common.SimpleIcon
import jp.co.yaz.kanuchi.presentation.common.StatusKind
import jp.co.yaz.kanuchi.presentation.common.StatusMessage
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.ic_arrow_downward
import kanuchi.presentation.generated.resources.ic_arrow_upward
import kanuchi.presentation.generated.resources.ic_block
import kanuchi.presentation.generated.resources.ic_person
import kanuchi.presentation.generated.resources.ic_shield_person
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
import org.jetbrains.compose.resources.DrawableResource
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
            uiState.loadFailed -> LoadErrorMessage(onRetry = viewModel::onRetryClicked)
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
        StatusMessage(
            stringResource(Res.string.roles_change_success_message, change.user.displayName, stringResource(change.newRole.label())),
            StatusKind.SUCCESS,
        )
    }
    when (uiState.saveError) {
        RoleChangeError.LAST_ACTIVE_ADMIN_REQUIRED ->
            StatusMessage(stringResource(Res.string.roles_last_active_admin_error_message), StatusKind.ERROR)
        RoleChangeError.GENERIC -> StatusMessage(stringResource(Res.string.common_data_save_error_message), StatusKind.ERROR)
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
                IconText(icon = profile.role.icon(), text = stringResource(profile.role.label()))
                if (profile.isSuspended) {
                    IconText(
                        icon = Res.drawable.ic_block,
                        text = stringResource(Res.string.roles_suspended_label),
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
        if (item.canChangeRole) {
            val newRole = if (profile.isAdmin) UserRole.MEMBER else UserRole.ADMIN
            val isSavingThisUser = savingChange?.user?.id == profile.id
            OutlinedButton(onClick = { onChangeRole(newRole) }, enabled = enabled) {
                val promotes = newRole == UserRole.ADMIN
                ButtonLabel(
                    icon = if (promotes) Res.drawable.ic_arrow_upward else Res.drawable.ic_arrow_downward,
                    text =
                        stringResource(
                            when {
                                isSavingThisUser -> Res.string.roles_changing_button
                                promotes -> Res.string.roles_promote_button
                                else -> Res.string.roles_demote_button
                            },
                        ),
                    inProgress = isSavingThisUser,
                )
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
        icon = { SimpleIcon(if (promotes) Res.drawable.ic_arrow_upward else Res.drawable.ic_arrow_downward) },
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

private fun UserRole.icon(): DrawableResource =
    when (this) {
        UserRole.ADMIN -> Res.drawable.ic_shield_person
        UserRole.MEMBER -> Res.drawable.ic_person
    }

private fun UserRole.label(): StringResource =
    when (this) {
        UserRole.ADMIN -> Res.string.roles_role_admin
        UserRole.MEMBER -> Res.string.roles_role_member
    }
