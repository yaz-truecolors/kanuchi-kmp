package jp.co.yaz.kanuchi.app

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeViewport
import jp.co.yaz.kanuchi.data.auth.AuthRedirectUrl
import jp.co.yaz.kanuchi.data.di.dataModule
import jp.co.yaz.kanuchi.domain.di.domainModule
import jp.co.yaz.kanuchi.presentation.di.presentationModule
import jp.co.yaz.kanuchi.presentation.navigation.KanuchiNavHost
import jp.co.yaz.kanuchi.presentation.theme.KanuchiTheme
import kotlinx.browser.document
import kotlinx.browser.window
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * 実行環境 (ブラウザ) に依存する値を提供するKoinモジュール。
 *
 * マジックリンクの戻り先は、今開いているページのURL (クエリ・ハッシュを除く) とする。
 * GitHub Pages ではアプリがパス (`/kanuchi-kmp/`) の下で配信されるため、origin だけでなく pathname まで含める。
 */
private val appModule =
    module {
        single { AuthRedirectUrl(window.location.origin + window.location.pathname) }
    }

/**
 * Kanuchi (鍛冶) - wasmJs エントリポイント。
 * Koinの起動とCompose Navigationの組み立てのみを担い、実際の画面ロジックは
 * presentation層の各Composable/ViewModelに委ねる。
 */
@OptIn(ExperimentalComposeUiApi::class)
fun main() {
    startKoin {
        modules(appModule, domainModule, dataModule, presentationModule)
    }

    ComposeViewport(document.body!!) {
        KanuchiTheme {
            KanuchiNavHost()
        }
    }
}
