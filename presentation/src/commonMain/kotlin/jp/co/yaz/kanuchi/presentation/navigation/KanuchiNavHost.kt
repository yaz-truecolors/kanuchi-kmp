package jp.co.yaz.kanuchi.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.co.yaz.kanuchi.presentation.auth.LoginScreen

/**
 * アプリ全体のNavigationグラフ。
 * v1スコープでは "login" (マジックリンク送信画面) のみを持つ。
 * ログイン後の画面 (日次入力・集計等) は今後のタスクで追加する。
 */
object KanuchiDestinations {
    const val LOGIN = "login"
}

@Composable
fun KanuchiNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = KanuchiDestinations.LOGIN) {
        composable(KanuchiDestinations.LOGIN) {
            LoginScreen()
        }
    }
}
