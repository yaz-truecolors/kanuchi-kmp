package jp.co.yaz.kanuchi.presentation.common

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_back_button
import org.jetbrains.compose.resources.stringResource

/**
 * ホーム画面から開く各画面 (個人設定・案件管理等) の共通の枠。
 * 上部に「戻る」ボタンと画面タイトルを表示し、その下に [content] を縦に並べる (はみ出す場合は縦スクロール)。
 *
 * 一覧・表を表示する画面があるため、ログイン画面 (400.dp) より広い 720.dp を幅の上限にしている。
 * 幅上限の付け方 (Box で中央寄せし、内側に widthIn を付ける) の理由は LoginScreen のコメントを参照。
 */
@Composable
fun SubScreenScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        Column(
            modifier = Modifier.widthIn(max = 720.dp).fillMaxWidth().padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack) {
                    Text(stringResource(Res.string.common_back_button))
                }
                Text(text = title, style = MaterialTheme.typography.headlineSmall)
            }
            content()
        }
    }
}
