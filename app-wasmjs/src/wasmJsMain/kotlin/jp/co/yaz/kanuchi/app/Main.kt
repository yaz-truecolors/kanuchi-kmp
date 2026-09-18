package jp.co.yaz.kanuchi.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import jp.co.yaz.kanuchi.data.di.dataModule
import jp.co.yaz.kanuchi.domain.di.domainModule
import jp.co.yaz.kanuchi.presentation.di.presentationModule
import jp.co.yaz.kanuchi.presentation.navigation.KanuchiNavHost
import jp.co.yaz.kanuchi.presentation.theme.KanuchiTheme
import kotlinx.browser.document
import org.koin.core.context.startKoin

/**
 * Kanuchi (鍛冶) - wasmJs エントリポイント。
 * Koinの起動とCompose Navigationの組み立てのみを担い、実際の画面ロジックは
 * presentation層の各Composable/ViewModelに委ねる。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(domainModule, dataModule, presentationModule)
    }

    ComposeViewport(document.body!!) {
        KanuchiTheme {
            KanuchiNavHost()
        }
    }
}
