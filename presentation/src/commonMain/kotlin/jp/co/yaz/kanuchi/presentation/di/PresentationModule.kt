package jp.co.yaz.kanuchi.presentation.di

import jp.co.yaz.kanuchi.presentation.auth.LoginViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

/**
 * presentation層のKoinモジュール。ViewModelはKoinの viewModel スコープで管理し、
 * koinViewModel() composable関数から解決される。
 */
val presentationModule =
    module {
        viewModelOf(::LoginViewModel)
    }
