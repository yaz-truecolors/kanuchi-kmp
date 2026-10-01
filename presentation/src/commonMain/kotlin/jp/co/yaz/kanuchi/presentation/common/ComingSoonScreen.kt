package jp.co.yaz.kanuchi.presentation.common

import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.common_coming_soon_message
import org.jetbrains.compose.resources.stringResource

/**
 * まだ実装していない画面の仮置き。ホーム画面のメニューから遷移先を先に用意しておき、
 * 各画面の実装時にこの呼び出しを本物の画面に置き換える。
 */
@Composable
fun ComingSoonScreen(
    title: String,
    onBack: () -> Unit,
) {
    SubScreenScaffold(title = title, onBack = onBack) {
        Text(stringResource(Res.string.common_coming_soon_message))
    }
}
