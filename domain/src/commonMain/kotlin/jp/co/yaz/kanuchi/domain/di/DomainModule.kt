package jp.co.yaz.kanuchi.domain.di

import jp.co.yaz.kanuchi.domain.auth.SendMagicLinkUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * domain層のKoinモジュール。UseCaseは状態を持たないため factory (呼び出しごとに新規生成) で登録する。
 */
val domainModule =
    module {
        factoryOf(::SendMagicLinkUseCase)
    }
