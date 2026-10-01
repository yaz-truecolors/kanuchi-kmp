package jp.co.yaz.kanuchi.domain.di

import jp.co.yaz.kanuchi.domain.auth.ConsumeMagicLinkCallbackErrorUseCase
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import jp.co.yaz.kanuchi.domain.auth.SendMagicLinkUseCase
import jp.co.yaz.kanuchi.domain.auth.SignOutUseCase
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import jp.co.yaz.kanuchi.domain.profile.GetProfilesUseCase
import jp.co.yaz.kanuchi.domain.role.ChangeUserRoleUseCase
import jp.co.yaz.kanuchi.domain.role.GetRoleManagementOverviewUseCase
import jp.co.yaz.kanuchi.domain.user.GetUserManagementOverviewUseCase
import jp.co.yaz.kanuchi.domain.user.InviteUserUseCase
import jp.co.yaz.kanuchi.domain.user.ReactivateUserUseCase
import jp.co.yaz.kanuchi.domain.user.RevokeInvitationUseCase
import jp.co.yaz.kanuchi.domain.user.SuspendUserUseCase
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
        factoryOf(::GetUserManagementOverviewUseCase)
        factoryOf(::InviteUserUseCase)
        factoryOf(::RevokeInvitationUseCase)
        factoryOf(::SuspendUserUseCase)
        factoryOf(::ReactivateUserUseCase)
        factoryOf(::GetRoleManagementOverviewUseCase)
        factoryOf(::ChangeUserRoleUseCase)
    }
