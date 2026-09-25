package jp.co.yaz.kanuchi.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.co.yaz.kanuchi.presentation.auth.LoginScreen

/**
 * アプリ全体のNavigationグラフ。
 * 遷移先ルートは [KanuchiDestinations] に定義する。
 */
@Composable
fun KanuchiNavHost() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = KanuchiDestinations.LOGIN) {
        composable(KanuchiDestinations.LOGIN) {
            LoginScreen()
        }
    }
}
