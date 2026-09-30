package jp.co.yaz.kanuchi.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.presentation.auth.LoginScreen
import jp.co.yaz.kanuchi.presentation.common.LoadingScreen
import jp.co.yaz.kanuchi.presentation.home.HomeScreen
import org.koin.compose.viewmodel.koinViewModel

/**
 * アプリ全体のNavigationグラフ。
 * 遷移先ルートは [KanuchiDestinations] に定義する。
 *
 * ログイン状態 ([AuthGateViewModel.authState]) に応じて表示する画面を切り替える。
 * - [AuthState.SignedIn] → ホーム画面、[AuthState.SignedOut] → ログイン画面
 * - [AuthState.Unknown] → 何もしない (起動直後は開始画面の読み込み中画面のまま。ログイン後にセッションの
 *   更新が一時的に失敗している間は今の画面のまま)
 *
 * 切り替え時はバックスタックを空にしてから遷移する。これにより
 * - ブラウザの戻る操作等で、ログアウト後にホーム画面へ戻れてしまうことを防ぐ
 * - 画面ごとの ViewModel が破棄され、ログアウト→再ログイン時に前回の入力内容・エラー表示が残らない
 */
@Composable
fun KanuchiNavHost(authGateViewModel: AuthGateViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val authState by authGateViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        val destination =
            when (authState) {
                is AuthState.SignedIn -> KanuchiDestinations.HOME
                AuthState.SignedOut -> KanuchiDestinations.LOGIN
                AuthState.Unknown -> return@LaunchedEffect
            }
        if (navController.currentDestination?.route == destination) return@LaunchedEffect
        navController.navigate(destination) {
            popUpTo(navController.graph.id) { inclusive = true }
            launchSingleTop = true
        }
    }

    NavHost(navController = navController, startDestination = KanuchiDestinations.LOADING) {
        composable(KanuchiDestinations.LOADING) {
            LoadingScreen()
        }
        composable(KanuchiDestinations.LOGIN) {
            LoginScreen()
        }
        composable(KanuchiDestinations.HOME) {
            HomeScreen()
        }
    }
}
