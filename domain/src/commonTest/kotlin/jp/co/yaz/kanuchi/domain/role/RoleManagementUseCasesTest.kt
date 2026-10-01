package jp.co.yaz.kanuchi.domain.role

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.profile.FakeProfileRepository
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class RoleManagementUseCasesTest {
    private val admin = UserProfile(id = "admin-1", email = "admin@example.com", displayName = "admin", role = UserRole.ADMIN)
    private val otherAdmin = UserProfile(id = "admin-2", email = "admin2@example.com", displayName = "admin2", role = UserRole.ADMIN)
    private val member = UserProfile(id = "user-1", email = "taro@example.com", displayName = "taro", role = UserRole.MEMBER)
    private val suspendedMember =
        UserProfile(id = "user-2", email = "jiro@example.com", displayName = "jiro", role = UserRole.MEMBER, isSuspended = true)

    private val profileRepository =
        FakeProfileRepository().apply {
            currentUserProfileResult = Result.success(admin)
            profilesResult = Result.success(listOf(admin, otherAdmin, member, suspendedMember))
        }
    private val userRoleRepository = FakeUserRoleRepository()

    @Test
    fun `overview contains current user and all users`() =
        runTest {
            val overview = GetRoleManagementOverviewUseCase(profileRepository)().getOrThrow()

            assertEquals("admin-1", overview.currentUserId)
            assertEquals(listOf(admin, otherAdmin, member, suspendedMember), overview.users)
        }

    @Test
    fun `overview fails when users fail to load`() =
        runTest {
            profileRepository.profilesResult = Result.failure(GenericDataFailureException())

            assertIs<GenericDataFailureException>(GetRoleManagementOverviewUseCase(profileRepository)().exceptionOrNull())
        }

    @Test
    fun `overview fails when current user fails to load`() =
        runTest {
            profileRepository.currentUserProfileResult = Result.failure(GenericDataFailureException())

            assertIs<GenericDataFailureException>(GetRoleManagementOverviewUseCase(profileRepository)().exceptionOrNull())
        }

    @Test
    fun `role of other active users can be changed`() {
        val overview = RoleManagementOverview(currentUserId = "admin-1", users = listOf(admin, otherAdmin, member, suspendedMember))

        assertTrue(overview.canChangeRole(otherAdmin))
        assertTrue(overview.canChangeRole(member))
    }

    @Test
    fun `own role cannot be changed`() {
        val overview = RoleManagementOverview(currentUserId = "admin-1", users = listOf(admin, member))

        assertFalse(overview.canChangeRole(admin))
    }

    @Test
    fun `role of suspended users cannot be changed`() {
        val overview = RoleManagementOverview(currentUserId = "admin-1", users = listOf(admin, suspendedMember))

        assertFalse(overview.canChangeRole(suspendedMember))
        assertFalse(overview.canChangeRole(otherAdmin.copy(isSuspended = true)))
    }

    @Test
    fun `role is changed`() =
        runTest {
            ChangeUserRoleUseCase(userRoleRepository)("user-1", UserRole.ADMIN).getOrThrow()
            ChangeUserRoleUseCase(userRoleRepository)("admin-2", UserRole.MEMBER).getOrThrow()

            assertEquals(listOf("user-1" to UserRole.ADMIN, "admin-2" to UserRole.MEMBER), userRoleRepository.roleChangeRequests)
        }

    @Test
    fun `last active admin failure is propagated as-is`() =
        runTest {
            userRoleRepository.changeRoleResult = Result.failure(LastActiveAdminRequiredException())

            assertIs<LastActiveAdminRequiredException>(
                ChangeUserRoleUseCase(userRoleRepository)("admin-2", UserRole.MEMBER).exceptionOrNull(),
            )
        }
}
