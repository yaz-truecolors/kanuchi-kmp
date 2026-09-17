package jp.co.yaz.kanuchi.data.di

import io.github.jan.supabase.SupabaseClient
import jp.co.yaz.kanuchi.data.auth.SupabaseAuthRepository
import jp.co.yaz.kanuchi.data.auth.createKanuchiSupabaseClient
import jp.co.yaz.kanuchi.domain.auth.AuthRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * data層のKoinモジュール。
 * SupabaseClientは接続を1つに保つためアプリ全体でシングルトンとして共有する。
 */
val dataModule =
    module {
        single<SupabaseClient> { createKanuchiSupabaseClient() }
        singleOf(::SupabaseAuthRepository) bind AuthRepository::class
    }
