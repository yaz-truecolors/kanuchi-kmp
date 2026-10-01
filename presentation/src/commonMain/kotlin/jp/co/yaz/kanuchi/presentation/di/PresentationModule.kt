package jp.co.yaz.kanuchi.presentation.di

import jp.co.yaz.kanuchi.presentation.auth.LoginViewModel
import jp.co.yaz.kanuchi.presentation.dailyinput.DailyInputDayViewModel
import jp.co.yaz.kanuchi.presentation.dailyinput.DailyInputViewModel
import jp.co.yaz.kanuchi.presentation.dashboard.AdminDashboardViewModel
import jp.co.yaz.kanuchi.presentation.holiday.CompanyHolidaysViewModel
import jp.co.yaz.kanuchi.presentation.home.HomeViewModel
import jp.co.yaz.kanuchi.presentation.navigation.AuthGateViewModel
import jp.co.yaz.kanuchi.presentation.project.ProjectMembersViewModel
import jp.co.yaz.kanuchi.presentation.project.ProjectsViewModel
import jp.co.yaz.kanuchi.presentation.role.RolesViewModel
import jp.co.yaz.kanuchi.presentation.settings.SettingsViewModel
import jp.co.yaz.kanuchi.presentation.summary.ProjectSummaryViewModel
import jp.co.yaz.kanuchi.presentation.users.UsersViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * presentation層のKoinモジュール。ViewModelはKoinの viewModel スコープで管理し、
 * koinViewModel() composable関数から解決される。
 */
val presentationModule =
    module {
        viewModelOf(::AuthGateViewModel)
        viewModelOf(::LoginViewModel)
        viewModelOf(::HomeViewModel)
        viewModelOf(::RolesViewModel)
        viewModelOf(::UsersViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::ProjectsViewModel)
        // 案件のIDは画面のルートの引数から koinViewModel { parametersOf(projectId) } で渡す
        viewModel { params -> ProjectMembersViewModel(params.get(), get(), get()) }
        viewModelOf(::DailyInputViewModel)
        // 日付は画面のルートの引数から koinViewModel { parametersOf(date) } で渡す
        viewModel { params -> DailyInputDayViewModel(params.get(), get(), get(), get()) }
        viewModelOf(::AdminDashboardViewModel)
        // 集計の対象のユーザーID・月 (どちらも null 可) は画面のルートの引数から koinViewModel { parametersOf(userId, yearMonth) } で渡す
        viewModel { params -> ProjectSummaryViewModel(params[0], params[1], get(), get(), get(), get(), get()) }
        viewModelOf(::CompanyHolidaysViewModel)
    }
