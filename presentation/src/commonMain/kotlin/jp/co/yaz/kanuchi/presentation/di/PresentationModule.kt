package jp.co.yaz.kanuchi.presentation.di

import jp.co.yaz.kanuchi.presentation.auth.LoginViewModel
import jp.co.yaz.kanuchi.presentation.home.HomeViewModel
import jp.co.yaz.kanuchi.presentation.navigation.AuthGateViewModel
import jp.co.yaz.kanuchi.presentation.role.RolesViewModel
import jp.co.yaz.kanuchi.presentation.users.UsersViewModel
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
    }
