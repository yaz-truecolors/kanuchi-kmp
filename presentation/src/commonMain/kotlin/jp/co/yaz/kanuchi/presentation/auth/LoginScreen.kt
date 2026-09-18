package jp.co.yaz.kanuchi.presentation.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.login_app_title
import kanuchi.presentation.generated.resources.login_description
import kanuchi.presentation.generated.resources.login_email_label
import kanuchi.presentation.generated.resources.login_error_prefix
import kanuchi.presentation.generated.resources.login_send_button
import kanuchi.presentation.generated.resources.login_sending_button
import kanuchi.presentation.generated.resources.login_success_message
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * ログイン画面 (マジックリンク送信フロー)。
 * メールアドレスを入力して送信すると、Supabase Authがそのアドレス宛にログイン用リンクを送る。
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    // 画面幅いっぱいのBoxで中央寄せし、内側のColumnに幅上限(400.dp)を持たせる。
    // Column自身にfillMaxWidth()とwidthIn(max=...)を両方付けると、fillMaxWidthが
    // min=max=親の幅 という制約を先に確定させてしまい、後段のwidthInによる上限が
    // 効かなくなる(広い画面でフォームが際限なく伸びてしまう)ため、責務を分離している。
    Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
        Column(
            modifier = Modifier.widthIn(max = 400.dp).padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(text = stringResource(Res.string.login_app_title), style = MaterialTheme.typography.headlineSmall)
            Text(text = stringResource(Res.string.login_description))

            OutlinedTextField(
                value = uiState.email,
                onValueChange = viewModel::onEmailChanged,
                label = { Text(stringResource(Res.string.login_email_label)) },
                singleLine = true,
                enabled = !uiState.isSending,
                modifier = Modifier.fillMaxWidth(),
            )

            Button(
                onClick = viewModel::onSendMagicLinkClicked,
                enabled = !uiState.isSending && uiState.email.isNotBlank(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (uiState.isSending) {
                    CircularProgressIndicator(modifier = Modifier.padding(end = 8.dp))
                }
                Text(
                    if (uiState.isSending) {
                        stringResource(Res.string.login_sending_button)
                    } else {
                        stringResource(Res.string.login_send_button)
                    },
                )
            }

            when {
                uiState.sentSuccessfully -> Text(stringResource(Res.string.login_success_message))
                uiState.errorMessage != null ->
                    Text(stringResource(Res.string.login_error_prefix, uiState.errorMessage.orEmpty()))
            }
        }
    }
}
