package jp.co.yaz.kanuchi.domain.di

import jp.co.yaz.kanuchi.domain.auth.ConsumeMagicLinkCallbackErrorUseCase
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import jp.co.yaz.kanuchi.domain.auth.SendMagicLinkUseCase
import jp.co.yaz.kanuchi.domain.auth.SignOutUseCase
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import jp.co.yaz.kanuchi.domain.profile.GetProfilesUseCase
import jp.co.yaz.kanuchi.domain.profile.UpdateDisplayNameUseCase
import jp.co.yaz.kanuchi.domain.project.AddProjectUseCase
import jp.co.yaz.kanuchi.domain.project.GetProjectAssignmentUseCase
import jp.co.yaz.kanuchi.domain.project.GetProjectsUseCase
import jp.co.yaz.kanuchi.domain.project.RenameProjectUseCase
import jp.co.yaz.kanuchi.domain.project.SaveProjectAssignmentUseCase
import jp.co.yaz.kanuchi.domain.project.SetProjectActiveUseCase
import jp.co.yaz.kanuchi.domain.shift.GetShiftSettingsUseCase
import jp.co.yaz.kanuchi.domain.shift.SaveShiftSettingsUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

/**
 * domain層のKoinモジュール。UseCaseは状態を持たないため factory (呼び出しごとに新規生成) で登録する。
 */
val domainModule =
    module {
        factoryOf(::SendMagicLinkUseCase)
        factoryOf(::ObserveAuthStateUseCase)
        factoryOf(::ConsumeMagicLinkCallbackErrorUseCase)
        factoryOf(::SignOutUseCase)
        factoryOf(::GetCurrentUserProfileUseCase)
        factoryOf(::GetProfilesUseCase)
        factoryOf(::UpdateDisplayNameUseCase)
        factoryOf(::GetShiftSettingsUseCase)
        factoryOf(::SaveShiftSettingsUseCase)
        factoryOf(::GetProjectsUseCase)
        factoryOf(::AddProjectUseCase)
        factoryOf(::RenameProjectUseCase)
        factoryOf(::SetProjectActiveUseCase)
        factoryOf(::GetProjectAssignmentUseCase)
        factoryOf(::SaveProjectAssignmentUseCase)
    }
