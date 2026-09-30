package jp.co.yaz.kanuchi.presentation.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.app_title
import kanuchi.presentation.generated.resources.home_coming_soon_message
import kanuchi.presentation.generated.resources.home_email_unknown
import kanuchi.presentation.generated.resources.home_sign_out_button
import kanuchi.presentation.generated.resources.home_sign_out_error_message
import kanuchi.presentation.generated.resources.home_signed_in_as
import kanuchi.presentation.generated.resources.home_signing_out_button
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * ホーム画面 (ログイン後の画面)。
 * 現時点ではログイン中のユーザーの表示とログアウトのみ。日次入力・集計等の画面への入口は今後追加する。
 */
@Composable
fun HomeScreen(viewModel: HomeViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // 幅上限の付け方は LoginScreen と同じ (理由は LoginScreen のコメント参照)。
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 400.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(Res.string.app_title), style = MaterialTheme.typography.headlineSmall)
            Text(
                text =
                    stringResource(
                        Res.string.home_signed_in_as,
                        uiState.email ?: stringResource(Res.string.home_email_unknown),
                    ),
            )
            Text(text = stringResource(Res.string.home_coming_soon_message))

            OutlinedButton(
                onClick = viewModel::onSignOutClicked,
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

            if (uiState.signOutFailed) {
                Text(stringResource(Res.string.home_sign_out_error_message))
            }
        }
    }
}
