package jp.co.yaz.kanuchi.app

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.ComposeViewport
import kotlinx.browser.document

/**
 * Kanuchi (鍛冶) - wasmJs エントリポイント。
 *
 * 現時点ではプレースホルダー画面のみ。DI(Koin)の初期化や Navigation グラフは
 * presentation 層の実装が進み次第、ここから組み立てる。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    ComposeViewport(document.body!!) {
        KanuchiPlaceholderApp()
    }
}

@Composable
private fun KanuchiPlaceholderApp() {
    MaterialTheme {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Kanuchi（鍛冶） - presentation層の実装後にここへ画面が組み込まれます")
        }
    }
}
