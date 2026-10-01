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
import jp.co.yaz.kanuchi.presentation.dailyinput.DailyInputDayScreen
import jp.co.yaz.kanuchi.presentation.dailyinput.DailyInputScreen
import jp.co.yaz.kanuchi.presentation.dashboard.AdminDashboardScreen
import jp.co.yaz.kanuchi.presentation.holiday.CompanyHolidaysScreen
import jp.co.yaz.kanuchi.presentation.home.HomeScreen
import jp.co.yaz.kanuchi.presentation.project.ProjectMembersScreen
import jp.co.yaz.kanuchi.presentation.project.ProjectsScreen
import jp.co.yaz.kanuchi.presentation.role.RolesScreen
import jp.co.yaz.kanuchi.presentation.settings.SettingsScreen
import jp.co.yaz.kanuchi.presentation.summary.ProjectSummaryScreen
import jp.co.yaz.kanuchi.presentation.users.UsersScreen
import kanuchi.presentation.generated.resources.Res
import kanuchi.presentation.generated.resources.admin_dashboard_title
import kanuchi.presentation.generated.resources.company_holidays_title
import kanuchi.presentation.generated.resources.summary_title
import kotlinx.datetime.LocalDate
import kanuchi.presentation.generated.resources.daily_input_title
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
            UsersScreen(onBack = navController::backToHome)
        }
        composable(KanuchiDestinations.ROLES) {
            RolesScreen(onBack = navController::backToHome)
        }
        workRecordRoutes(navController)
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

/**
 * 日次入力・案件別集計・管理者ダッシュボード・休業日管理のルート。
 * 案件別集計は、管理者ダッシュボードから開いた場合 (他のメンバーの分) は管理者ダッシュボードへ、
 * ホーム画面から開いた場合はホーム画面へ戻る。
 */
private fun NavGraphBuilder.workRecordRoutes(navController: NavHostController) {
    dailyInputRoutes(navController)
    composable(
        KanuchiDestinations.SUMMARY,
        arguments =
            listOf(
                navArgument(KanuchiDestinations.SUMMARY_USER_ID_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
                navArgument(KanuchiDestinations.SUMMARY_YEAR_MONTH_ARG) {
                    type = NavType.StringType
                    nullable = true
                    defaultValue = null
                },
            ),
    ) { backStackEntry ->
        val arguments = backStackEntry.arguments
        ProjectSummaryScreen(
            userId = arguments?.read { getStringOrNull(KanuchiDestinations.SUMMARY_USER_ID_ARG) },
            yearMonth = arguments?.read { getStringOrNull(KanuchiDestinations.SUMMARY_YEAR_MONTH_ARG) },
            onBack = navController::backFromSummary,
        )
    }
    composable(KanuchiDestinations.ADMIN_DASHBOARD) {
        AdminDashboardScreen(
            onBack = navController::backToHome,
            onOpenMember = { userId, yearMonth ->
                navController.navigate(KanuchiDestinations.summaryOf(userId, yearMonth)) { launchSingleTop = true }
            },
        )
    }
    composable(KanuchiDestinations.COMPANY_HOLIDAYS) {
        CompanyHolidaysScreen(onBack = navController::backToHome)
    }
}

/**
 * 案件別集計の「戻る」。管理者ダッシュボードから開いていれば管理者ダッシュボードへ、そうでなければホーム画面へ戻る
 * (戻る操作の連打で戻りすぎないよう、backToHome と同じく popBackStack(route) を使う)。
 */
private fun NavHostController.backFromSummary() {
    if (!popBackStack(KanuchiDestinations.ADMIN_DASHBOARD, inclusive = false)) backToHome()
}

/**
 * 日次入力 (月の一覧) と、そこから開く1日分の入力のルート。
 * 1日分の入力の「戻る」・保存後は、戻る操作の連打で一覧より前に戻らないよう popBackStack(route) で一覧へ戻る。
 */
private fun NavGraphBuilder.dailyInputRoutes(navController: NavHostController) {
    composable(KanuchiDestinations.DAILY_INPUT) {
        DailyInputScreen(
            onBack = navController::backToHome,
            onOpenDay = { date -> navController.navigate(KanuchiDestinations.dailyInputDay(date)) { launchSingleTop = true } },
        )
    }
    composable(
        KanuchiDestinations.DAILY_INPUT_DAY,
        arguments = listOf(navArgument(KanuchiDestinations.DAILY_INPUT_DATE_ARG) { type = NavType.StringType }),
    ) { backStackEntry ->
        val backToList = { navController.popBackStack(KanuchiDestinations.DAILY_INPUT, inclusive = false) }
        val date =
            backStackEntry.arguments
                ?.read { getStringOrNull(KanuchiDestinations.DAILY_INPUT_DATE_ARG) }
                ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
        if (date == null) {
            // ルートは dailyInputDay() で組み立てるため通常は起きない
            LaunchedEffect(Unit) { backToList() }
        } else {
            DailyInputDayScreen(date = date, onBack = { backToList() }, onFinished = { backToList() })
        }
    }
}
