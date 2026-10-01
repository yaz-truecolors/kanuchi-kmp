package jp.co.yaz.kanuchi.presentation.di

import jp.co.yaz.kanuchi.presentation.auth.LoginViewModel
import jp.co.yaz.kanuchi.presentation.home.HomeViewModel
import jp.co.yaz.kanuchi.presentation.navigation.AuthGateViewModel
import jp.co.yaz.kanuchi.presentation.project.ProjectMembersViewModel
import jp.co.yaz.kanuchi.presentation.project.ProjectsViewModel
import jp.co.yaz.kanuchi.presentation.settings.SettingsViewModel
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
        viewModelOf(::UsersViewModel)
        viewModelOf(::SettingsViewModel)
        viewModelOf(::ProjectsViewModel)
        // 案件のIDは画面のルートの引数から koinViewModel { parametersOf(projectId) } で渡す
        viewModel { params -> ProjectMembersViewModel(params.get(), get(), get()) }
    }
