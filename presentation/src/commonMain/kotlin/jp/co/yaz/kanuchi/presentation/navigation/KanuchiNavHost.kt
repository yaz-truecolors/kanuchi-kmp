package jp.co.yaz.kanuchi.presentation.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import androidx.savedstate.read
import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.presentation.auth.LoginScreen
import jp.co.yaz.kanuchi.presentation.common.ComingSoonScreen
import jp.co.yaz.kanuchi.presentation.common.LoadingScreen
import jp.co.yaz.kanuchi.presentation.home.HomeScreen
import jp.co.yaz.kanuchi.presentation.project.ProjectMembersScreen
import jp.co.yaz.kanuchi.presentation.project.ProjectsScreen
import jp.co.yaz.kanuchi.presentation.settings.SettingsScreen
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.roles_title
import kanuchi.presentation.generated.resources.users_title
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.viewmodel.koinViewModel

/**
 * アプリ全体のNavigationグラフ。
 * 遷移先ルートは [KanuchiDestinations] に定義する。
 *
 * ログイン状態 ([AuthGateViewModel.authState]) に応じて表示する画面を切り替える。
 * - [AuthState.SignedIn] → 読み込み中・ログイン画面を表示していればホーム画面へ
 *   (ホーム画面から開いた画面を表示中の場合はそのまま。セッションの自動更新等で再度 SignedIn になっても
 *   操作中の画面から追い出さないため)
 * - [AuthState.SignedOut] → ログイン画面へ
 * - [AuthState.Unknown] → 何もしない (起動直後は開始画面の読み込み中画面のまま。ログイン後にセッションの
 *   更新が一時的に失敗している間は今の画面のまま)
 *
 * ログイン状態による切り替え時はバックスタックを空にしてから遷移する。これにより
 * - ブラウザの戻る操作等で、ログアウト後にログイン後の画面へ戻れてしまうことを防ぐ
 * - 画面ごとの ViewModel が破棄され、ログアウト→再ログイン時に前回の入力内容・エラー表示が残らない
 */
@Composable
fun KanuchiNavHost(authGateViewModel: AuthGateViewModel = koinViewModel()) {
    val navController = rememberNavController()
    val authState by authGateViewModel.authState.collectAsState()

    LaunchedEffect(authState) {
        val currentRoute = navController.currentDestination?.route
        val destination =
            when (authState) {
                is AuthState.SignedIn ->
                    if (currentRoute == KanuchiDestinations.LOADING || currentRoute == KanuchiDestinations.LOGIN) {
                        KanuchiDestinations.HOME
                    } else {
                        return@LaunchedEffect
                    }
                AuthState.SignedOut -> KanuchiDestinations.LOGIN
                AuthState.Unknown -> return@LaunchedEffect
            }
        if (currentRoute == destination) return@LaunchedEffect
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
            HomeScreen(onNavigate = { route -> navController.navigate(route) { launchSingleTop = true } })
        }
        composable(KanuchiDestinations.SETTINGS) {
            SettingsScreen(onBack = navController::backToHome)
        }
        projectRoutes(navController)
        composable(KanuchiDestinations.USERS) {
            ComingSoonScreen(title = stringResource(Res.string.users_title), onBack = navController::backToHome)
        }
        composable(KanuchiDestinations.ROLES) {
            ComingSoonScreen(title = stringResource(Res.string.roles_title), onBack = navController::backToHome)
        }
    }
}

/**
 * ホーム画面から開いた画面の「戻る」。ホーム画面まで戻る (戻る操作の連打で、ホーム画面より前に戻らないよう
 * popBackStack(route) を使う。ホーム画面の手前は空なので、バックスタックが空になることは無い)。
 */
private fun NavHostController.backToHome() {
    popBackStack(KanuchiDestinations.HOME, inclusive = false)
}

/**
 * 案件管理画面と、そこから開く担当メンバー画面のルート。
 */
private fun NavGraphBuilder.projectRoutes(navController: NavHostController) {
    composable(KanuchiDestinations.PROJECTS) {
        ProjectsScreen(
            onBack = navController::backToHome,
            onOpenMembers = { projectId ->
                navController.navigate(KanuchiDestinations.projectMembers(projectId)) { launchSingleTop = true }
            },
        )
    }
    composable(
        KanuchiDestinations.PROJECT_MEMBERS,
        arguments = listOf(navArgument(KanuchiDestinations.PROJECT_ID_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
        val projectId = backStackEntry.arguments?.read { getStringOrNull(KanuchiDestinations.PROJECT_ID_ARG) }.orEmpty()
        // 戻る操作の連打で案件管理画面より前に戻らないよう、backToHome と同じく popBackStack(route) を使う
        ProjectMembersScreen(
            projectId = projectId,
            onBack = { navController.popBackStack(KanuchiDestinations.PROJECTS, inclusive = false) },
        )
    }
}
