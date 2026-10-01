package jp.co.yaz.kanuchi.presentation.users

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.user.Invitation
import jp.co.yaz.kanuchi.domain.user.UserManagementOverview
import jp.co.yaz.kanuchi.presentation.common.SubScreenScaffold
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_data_save_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.users_cancel_button
import kanuchi.presentation.generated.resources.users_invitation_has_account
import kanuchi.presentation.generated.resources.users_invitation_no_account
import kanuchi.presentation.generated.resources.users_invitation_revoke_button
import kanuchi.presentation.generated.resources.users_invitation_revoke_note
import kanuchi.presentation.generated.resources.users_invitations_empty
import kanuchi.presentation.generated.resources.users_invitations_header
import kanuchi.presentation.generated.resources.users_invite_already_invited_message
import kanuchi.presentation.generated.resources.users_invite_button
import kanuchi.presentation.generated.resources.users_invite_description
import kanuchi.presentation.generated.resources.users_invite_email_label
import kanuchi.presentation.generated.resources.users_invite_header
import kanuchi.presentation.generated.resources.users_invite_invalid_email_message
import kanuchi.presentation.generated.resources.users_invite_success_message
import kanuchi.presentation.generated.resources.users_last_active_admin_required_message
import kanuchi.presentation.generated.resources.users_list_current_user_name
import kanuchi.presentation.generated.resources.users_list_header
import kanuchi.presentation.generated.resources.users_ok_button
import kanuchi.presentation.generated.resources.users_reactivate_button
import kanuchi.presentation.generated.resources.users_role_admin
import kanuchi.presentation.generated.resources.users_role_and_status
import kanuchi.presentation.generated.resources.users_role_member
import kanuchi.presentation.generated.resources.users_status_active
import kanuchi.presentation.generated.resources.users_status_suspended
import kanuchi.presentation.generated.resources.users_suspend_button
import kanuchi.presentation.generated.resources.users_suspend_confirm_button
import kanuchi.presentation.generated.resources.users_suspend_confirm_message
import kanuchi.presentation.generated.resources.users_suspend_confirm_title
import kanuchi.presentation.generated.resources.users_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * ユーザー管理画面 (admin 用)。招待リストの追加・取り消しと、ユーザーの利用停止・復帰。
 * 自分自身の行には利用停止ボタンを出さない (DB 側でも自分自身の利用停止は拒否される)。
 */
@Composable
fun UsersScreen(
    onBack: () -> Unit,
    viewModel: UsersViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()

    SubScreenScaffold(title = stringResource(Res.string.users_title), onBack = onBack) {
        val overview = uiState.overview
        when {
            uiState.loadFailed -> {
                Text(stringResource(Res.string.common_data_load_error_message))
                TextButton(onClick = viewModel::onRetryClicked) { Text(stringResource(Res.string.common_retry_button)) }
            }
            overview == null -> CircularProgressIndicator(modifier = Modifier.align(Alignment.CenterHorizontally))
            else -> {
                InviteSection(uiState, viewModel)
                HorizontalDivider()
                InvitationList(overview.invitations, enabled = uiState.canOperate, onRevoke = viewModel::onRevokeInvitationClicked)
                HorizontalDivider()
                UserList(overview, enabled = uiState.canOperate, viewModel = viewModel)
            }
        }
    }

    uiState.suspendConfirmationTarget?.let { target ->
        SuspendConfirmationDialog(target, onConfirm = viewModel::onSuspendConfirmed, onDismiss = viewModel::onSuspendDismissed)
    }
    uiState.actionError?.let { error ->
        ActionErrorDialog(error, onDismiss = viewModel::onActionErrorDismissed)
    }
}

@Composable
private fun InviteSection(
    uiState: UsersUiState,
    viewModel: UsersViewModel,
) {
    Text(text = stringResource(Res.string.users_invite_header), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(Res.string.users_invite_description))
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = uiState.inviteEmail,
            onValueChange = viewModel::onInviteEmailChanged,
            label = { Text(stringResource(Res.string.users_invite_email_label)) },
            singleLine = true,
            enabled = !uiState.isSubmitting,
            modifier = Modifier.weight(1f),
        )
        Button(
            onClick = viewModel::onInviteClicked,
            enabled = uiState.canOperate && uiState.inviteEmail.isNotBlank(),
        ) {
            Text(stringResource(Res.string.users_invite_button))
        }
    }
    val inviteError = uiState.inviteError
    val invitedEmail = uiState.invitedEmail
    when {
        inviteError != null -> Text(inviteError.message(), color = MaterialTheme.colorScheme.error)
        invitedEmail != null -> Text(stringResource(Res.string.users_invite_success_message, invitedEmail))
    }
}

@Composable
private fun InvitationList(
    invitations: List<Invitation>,
    enabled: Boolean,
    onRevoke: (email: String) -> Unit,
) {
    Text(text = stringResource(Res.string.users_invitations_header), style = MaterialTheme.typography.titleMedium)
    if (invitations.isEmpty()) {
        Text(stringResource(Res.string.users_invitations_empty))
        return
    }
    invitations.forEach { invitation ->
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(modifier = Modifier.weight(1f)) {
                Text(invitation.email)
                Text(
                    text =
                        if (invitation.hasAccount) {
                            stringResource(Res.string.users_invitation_has_account)
                        } else {
                            stringResource(Res.string.users_invitation_no_account)
                        },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = { onRevoke(invitation.email) }, enabled = enabled) {
                Text(stringResource(Res.string.users_invitation_revoke_button))
            }
        }
    }
    Text(text = stringResource(Res.string.users_invitation_revoke_note), style = MaterialTheme.typography.bodySmall)
}

@Composable
private fun UserList(
    overview: UserManagementOverview,
    enabled: Boolean,
    viewModel: UsersViewModel,
) {
    Text(text = stringResource(Res.string.users_list_header), style = MaterialTheme.typography.titleMedium)
    overview.users.forEach { user ->
        val isCurrentUser = user.id == overview.currentUserId
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    if (isCurrentUser) {
                        stringResource(Res.string.users_list_current_user_name, user.displayName)
                    } else {
                        user.displayName
                    },
                )
                Text(text = user.email, style = MaterialTheme.typography.bodySmall)
                Text(
                    text = stringResource(Res.string.users_role_and_status, user.role.label(), user.statusLabel()),
                    style = MaterialTheme.typography.bodySmall,
                    color = if (user.isSuspended) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                )
            }
            when {
                isCurrentUser -> Unit
                user.isSuspended ->
                    OutlinedButton(onClick = { viewModel.onReactivateClicked(user) }, enabled = enabled) {
                        Text(stringResource(Res.string.users_reactivate_button))
                    }
                else ->
                    OutlinedButton(onClick = { viewModel.onSuspendClicked(user) }, enabled = enabled) {
                        Text(stringResource(Res.string.users_suspend_button))
                    }
            }
        }
    }
}

@Composable
private fun SuspendConfirmationDialog(
    target: UserProfile,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(Res.string.users_suspend_confirm_title)) },
        text = { Text(stringResource(Res.string.users_suspend_confirm_message, target.displayName, target.email)) },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text(stringResource(Res.string.users_suspend_confirm_button)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.users_cancel_button)) }
        },
    )
}

@Composable
private fun ActionErrorDialog(
    error: UserActionError,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        text = {
            Text(
                when (error) {
                    UserActionError.LAST_ACTIVE_ADMIN_REQUIRED -> stringResource(Res.string.users_last_active_admin_required_message)
                    UserActionError.SAVE_FAILED -> stringResource(Res.string.common_data_save_error_message)
                },
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(Res.string.users_ok_button)) }
        },
    )
}

@Composable
private fun InviteError.message(): String =
    when (this) {
        InviteError.INVALID_EMAIL -> stringResource(Res.string.users_invite_invalid_email_message)
        InviteError.ALREADY_INVITED -> stringResource(Res.string.users_invite_already_invited_message)
        InviteError.SAVE_FAILED -> stringResource(Res.string.common_data_save_error_message)
    }

@Composable
private fun UserRole.label(): String =
    when (this) {
        UserRole.ADMIN -> stringResource(Res.string.users_role_admin)
        UserRole.MEMBER -> stringResource(Res.string.users_role_member)
    }

@Composable
private fun UserProfile.statusLabel(): String =
    if (isSuspended) {
        stringResource(Res.string.users_status_suspended)
    } else {
        stringResource(Res.string.users_status_active)
    }
