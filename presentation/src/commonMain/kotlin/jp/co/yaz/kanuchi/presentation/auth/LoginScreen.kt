package jp.co.yaz.kanuchi.presentation.auth

import androidx.compose.foundation.layout.Arrangement
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
import org.koin.compose.viewmodel.koinViewModel

/**
 * ログイン画面 (マジックリンク送信フロー)。
 * メールアドレスを入力して送信すると、Supabase Authがそのアドレス宛にログイン用リンクを送る。
 */
@Composable
fun LoginScreen(viewModel: LoginViewModel = koinViewModel()) {
    val uiState by viewModel.uiState.collectAsState()

    Column(
        modifier = Modifier.fillMaxWidth().padding(24.dp).widthIn(max = 400.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(text = "Kanuchi（鍛冶）", style = MaterialTheme.typography.headlineSmall)
        Text(text = "メールアドレスにログイン用のリンクを送ります。")

        OutlinedTextField(
            value = uiState.email,
            onValueChange = viewModel::onEmailChanged,
            label = { Text("メールアドレス") },
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
            Text(if (uiState.isSending) "送信中..." else "ログインリンクを送る")
        }

        when {
            uiState.sentSuccessfully -> Text("メールを送信しました。受信箱をご確認ください。")
            uiState.errorMessage != null -> Text("エラー: ${uiState.errorMessage}")
        }
    }
}
