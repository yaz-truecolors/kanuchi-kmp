package jp.co.yaz.kanuchi.domain.user

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.profile.FakeProfileRepository
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserManagementUseCasesTest {
    private val admin = UserProfile(id = "admin-1", email = "admin@example.com", displayName = "admin", role = UserRole.ADMIN)
    private val member = UserProfile(id = "user-1", email = "Taro@Example.com", displayName = "taro", role = UserRole.MEMBER)

    private val profileRepository =
        FakeProfileRepository().apply {
            currentUserProfileResult = Result.success(admin)
            profilesResult = Result.success(listOf(admin, member))
        }
    private val userManagementRepository = FakeUserManagementRepository()

    @Test
    fun `overview contains current user, users and invitations with account status`() =
        runTest {
            userManagementRepository.invitedEmailsResult = Result.success(listOf("new@example.com", "taro@example.com"))

            val overview = GetUserManagementOverviewUseCase(profileRepository, userManagementRepository)().getOrThrow()

            assertEquals("admin-1", overview.currentUserId)
            assertEquals(listOf(admin, member), overview.users)
            assertEquals(
                listOf(
                    Invitation(email = "new@example.com", hasAccount = false),
                    // アカウントのメールアドレスと大文字小文字が違っても、同じアドレスとして扱う
                    Invitation(email = "taro@example.com", hasAccount = true),
                ),
                overview.invitations,
            )
        }

    @Test
    fun `overview fails when any of the data fails to load`() =
        runTest {
            userManagementRepository.invitedEmailsResult = Result.failure(GenericDataFailureException())

            assertIs<GenericDataFailureException>(
                GetUserManagementOverviewUseCase(profileRepository, userManagementRepository)().exceptionOrNull(),
            )
        }

    @Test
    fun `overview fails when current user fails to load`() =
        runTest {
            profileRepository.currentUserProfileResult = Result.failure(GenericDataFailureException())

            assertIs<GenericDataFailureException>(
                GetUserManagementOverviewUseCase(profileRepository, userManagementRepository)().exceptionOrNull(),
            )
        }

    @Test
    fun `valid email is invited after trimming`() =
        runTest {
            val result = InviteUserUseCase(userManagementRepository)("  new@example.com ")

            assertTrue(result.isSuccess)
            assertEquals(listOf("new@example.com"), userManagementRepository.invitedEmails.map { it.value })
        }

    @Test
    fun `invalid email is rejected without calling the repository`() =
        runTest {
            val result = InviteUserUseCase(userManagementRepository)("not-an-email")

            assertIs<InvalidEmailAddressException>(result.exceptionOrNull())
            assertTrue(userManagementRepository.invitedEmails.isEmpty())
        }

    @Test
    fun `already invited failure is propagated as-is`() =
        runTest {
            userManagementRepository.inviteResult = Result.failure(AlreadyInvitedException())

            assertIs<AlreadyInvitedException>(InviteUserUseCase(userManagementRepository)("new@example.com").exceptionOrNull())
        }

    @Test
    fun `invitation is revoked`() =
        runTest {
            RevokeInvitationUseCase(userManagementRepository)("new@example.com").getOrThrow()

            assertEquals(listOf("new@example.com"), userManagementRepository.revokedEmails)
        }

    @Test
    fun `user is suspended and reactivated`() =
        runTest {
            SuspendUserUseCase(userManagementRepository)("user-1").getOrThrow()
            ReactivateUserUseCase(userManagementRepository)("user-1").getOrThrow()

            assertEquals(listOf("user-1" to true, "user-1" to false), userManagementRepository.suspensionRequests)
        }

    @Test
    fun `last active admin failure is propagated as-is`() =
        runTest {
            userManagementRepository.setUserSuspendedResult = Result.failure(LastActiveAdminRequiredException())

            assertIs<LastActiveAdminRequiredException>(SuspendUserUseCase(userManagementRepository)("admin-2").exceptionOrNull())
        }
}
