package jp.co.yaz.kanuchi.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import jp.co.yaz.kanuchi.presentation.navigation.KanuchiDestinations
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.app_title
import kanuchi.presentation.generated.resources.common_data_load_error_message
import kanuchi.presentation.generated.resources.common_retry_button
import kanuchi.presentation.generated.resources.home_admin_menu_header
import kanuchi.presentation.generated.resources.home_coming_soon_message
import kanuchi.presentation.generated.resources.home_email_unknown
import kanuchi.presentation.generated.resources.home_sign_out_button
import kanuchi.presentation.generated.resources.home_sign_out_error_message
import kanuchi.presentation.generated.resources.home_signed_in_as
import kanuchi.presentation.generated.resources.home_signed_in_as_with_name
import kanuchi.presentation.generated.resources.home_signing_out_button
import kanuchi.presentation.generated.resources.projects_title
import kanuchi.presentation.generated.resources.roles_title
import kanuchi.presentation.generated.resources.settings_title
import kanuchi.presentation.generated.resources.users_title
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * ホーム画面 (ログイン後の画面)。ログイン中のユーザーの表示、各画面へのメニュー、ログアウト。
 * 管理者メニューは、プロフィールを読み込めて admin だった場合のみ表示する
 * (表示の制御は使い勝手のためで、実際のアクセス制御は DB 側の RLS で行っている)。
 *
 * @param onNavigate メニューが押されたときに、遷移先のルート ([KanuchiDestinations]) を受け取る
 */
@Composable
fun HomeScreen(
    onNavigate: (route: String) -> Unit,
    viewModel: HomeViewModel = koinViewModel(),
) {
    val uiState by viewModel.uiState.collectAsState()
    // ホーム画面から開いた画面から戻ったときも、この画面は作り直されるため再度実行される
    LaunchedEffect(viewModel) { viewModel.onScreenShown() }

    // 幅上限の付け方は LoginScreen と同じ (理由は LoginScreen のコメント参照)。
    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 400.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(Res.string.app_title), style = MaterialTheme.typography.headlineSmall)
            SignedInAs(uiState)
            ProfileLoadStatus(uiState, onRetry = viewModel::onRetryProfileClicked)

            MenuButton(Res.string.settings_title) { onNavigate(KanuchiDestinations.SETTINGS) }
            if (uiState.showsAdminMenu) {
                HorizontalDivider()
                Text(text = stringResource(Res.string.home_admin_menu_header), style = MaterialTheme.typography.titleSmall)
                MenuButton(Res.string.projects_title) { onNavigate(KanuchiDestinations.PROJECTS) }
                MenuButton(Res.string.users_title) { onNavigate(KanuchiDestinations.USERS) }
                MenuButton(Res.string.roles_title) { onNavigate(KanuchiDestinations.ROLES) }
            }
            HorizontalDivider()
            Text(text = stringResource(Res.string.home_coming_soon_message))

            SignOutButton(uiState, onClick = viewModel::onSignOutClicked)
            if (uiState.signOutFailed) {
                Text(stringResource(Res.string.home_sign_out_error_message))
            }
        }
    }
}

@Composable
private fun SignedInAs(uiState: HomeUiState) {
    val email = uiState.profile?.email ?: uiState.email ?: stringResource(Res.string.home_email_unknown)
    val profile = uiState.profile
    Text(
        text =
            if (profile != null) {
                stringResource(Res.string.home_signed_in_as_with_name, profile.displayName, email)
            } else {
                stringResource(Res.string.home_signed_in_as, email)
            },
    )
}

@Composable
private fun ProfileLoadStatus(
    uiState: HomeUiState,
    onRetry: () -> Unit,
) {
    when {
        uiState.isLoadingProfile -> CircularProgressIndicator()
        uiState.profileLoadFailed -> {
            Text(stringResource(Res.string.common_data_load_error_message))
            TextButton(onClick = onRetry) { Text(stringResource(Res.string.common_retry_button)) }
        }
    }
}

@Composable
private fun MenuButton(
    label: StringResource,
    onClick: () -> Unit,
) {
    Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(label))
    }
}

@Composable
private fun SignOutButton(
    uiState: HomeUiState,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = !uiState.isSigningOut,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (uiState.isSigningOut) {
            CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
        }
        Text(
            if (uiState.isSigningOut) {
                stringResource(Res.string.home_signing_out_button)
            } else {
                stringResource(Res.string.home_sign_out_button)
            },
        )
    }
}
