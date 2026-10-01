package jp.co.yaz.kanuchi.data.di

import io.github.jan.supabase.SupabaseClient
import jp.co.yaz.kanuchi.data.auth.SupabaseAuthRepository
import jp.co.yaz.kanuchi.data.auth.createKanuchiSupabaseClient
import jp.co.yaz.kanuchi.data.profile.SupabaseProfileRepository
import jp.co.yaz.kanuchi.data.project.SupabaseProjectRepository
import jp.co.yaz.kanuchi.data.role.SupabaseUserRoleRepository
import jp.co.yaz.kanuchi.data.shift.SupabaseShiftSettingsRepository
import jp.co.yaz.kanuchi.data.user.SupabaseUserManagementRepository
import jp.co.yaz.kanuchi.domain.auth.AuthRepository
import jp.co.yaz.kanuchi.domain.profile.ProfileRepository
import jp.co.yaz.kanuchi.domain.project.ProjectRepository
import jp.co.yaz.kanuchi.domain.role.UserRoleRepository
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsRepository
import jp.co.yaz.kanuchi.domain.user.UserManagementRepository
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

/**
 * data層のKoinモジュール。
 * SupabaseClientは接続を1つに保つためアプリ全体でシングルトンとして共有する。
 *
 * [jp.co.yaz.kanuchi.data.auth.AuthRedirectUrl] は実行環境に依存するため、このモジュールでは定義せず、
 * エントリポイント (app-wasmjs) のモジュールが提供する。
 */
val dataModule =
    module {
        single<SupabaseClient> { createKanuchiSupabaseClient() }
        singleOf(::SupabaseAuthRepository) bind AuthRepository::class
        singleOf(::SupabaseProfileRepository) bind ProfileRepository::class
        singleOf(::SupabaseUserManagementRepository) bind UserManagementRepository::class
        singleOf(::SupabaseUserRoleRepository) bind UserRoleRepository::class
        singleOf(::SupabaseShiftSettingsRepository) bind ShiftSettingsRepository::class
        singleOf(::SupabaseProjectRepository) bind ProjectRepository::class
    }
