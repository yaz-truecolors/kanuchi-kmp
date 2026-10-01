package jp.co.yaz.kanuchi.domain.di

import jp.co.yaz.kanuchi.domain.auth.ConsumeMagicLinkCallbackErrorUseCase
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import jp.co.yaz.kanuchi.domain.auth.SendMagicLinkUseCase
import jp.co.yaz.kanuchi.domain.auth.SignOutUseCase
import jp.co.yaz.kanuchi.domain.calendar.AddCompanyHolidaysUseCase
import jp.co.yaz.kanuchi.domain.calendar.DeleteCompanyHolidayUseCase
import jp.co.yaz.kanuchi.domain.calendar.GetCompanyHolidaysOfYearUseCase
import jp.co.yaz.kanuchi.domain.calendar.GetTodayUseCase
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import jp.co.yaz.kanuchi.domain.profile.GetProfilesUseCase
import jp.co.yaz.kanuchi.domain.profile.UpdateDisplayNameUseCase
import jp.co.yaz.kanuchi.domain.project.AddProjectUseCase
import jp.co.yaz.kanuchi.domain.project.GetProjectAssignmentUseCase
import jp.co.yaz.kanuchi.domain.project.GetProjectsUseCase
import jp.co.yaz.kanuchi.domain.project.RenameProjectUseCase
import jp.co.yaz.kanuchi.domain.project.SaveProjectAssignmentUseCase
import jp.co.yaz.kanuchi.domain.project.SetProjectActiveUseCase
import jp.co.yaz.kanuchi.domain.role.ChangeUserRoleUseCase
import jp.co.yaz.kanuchi.domain.role.GetRoleManagementOverviewUseCase
import jp.co.yaz.kanuchi.domain.shift.GetShiftSettingsUseCase
import jp.co.yaz.kanuchi.domain.shift.SaveShiftSettingsUseCase
import jp.co.yaz.kanuchi.domain.user.GetUserManagementOverviewUseCase
import jp.co.yaz.kanuchi.domain.user.InviteUserUseCase
import jp.co.yaz.kanuchi.domain.user.ReactivateUserUseCase
import jp.co.yaz.kanuchi.domain.user.RevokeInvitationUseCase
import jp.co.yaz.kanuchi.domain.user.SuspendUserUseCase
import jp.co.yaz.kanuchi.domain.work.GetMonthlyWorkSheetUseCase
import jp.co.yaz.kanuchi.domain.work.GetTeamMonthlySummaryUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module
import kotlin.time.Clock

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
        factoryOf(::UpdateDisplayNameUseCase)
        factoryOf(::GetShiftSettingsUseCase)
        factoryOf(::SaveShiftSettingsUseCase)
        factoryOf(::GetProjectsUseCase)
        factoryOf(::AddProjectUseCase)
        factoryOf(::RenameProjectUseCase)
        factoryOf(::SetProjectActiveUseCase)
        factoryOf(::GetProjectAssignmentUseCase)
        factoryOf(::SaveProjectAssignmentUseCase)
        // 「今日」の基準。テストでは固定の Clock を渡す
        single<Clock> { Clock.System }
        factoryOf(::GetTodayUseCase)
        factoryOf(::GetMonthlyWorkSheetUseCase)
        factoryOf(::GetTeamMonthlySummaryUseCase)
        factoryOf(::GetCompanyHolidaysOfYearUseCase)
        factoryOf(::AddCompanyHolidaysUseCase)
        factoryOf(::DeleteCompanyHolidayUseCase)
    }
