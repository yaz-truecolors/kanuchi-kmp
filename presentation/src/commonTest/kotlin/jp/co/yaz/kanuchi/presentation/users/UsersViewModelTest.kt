package jp.co.yaz.kanuchi.presentation.users

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.user.AlreadyInvitedException
import jp.co.yaz.kanuchi.domain.user.GetUserManagementOverviewUseCase
import jp.co.yaz.kanuchi.domain.user.Invitation
import jp.co.yaz.kanuchi.domain.user.InviteUserUseCase
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException
import jp.co.yaz.kanuchi.domain.user.ReactivateUserUseCase
import jp.co.yaz.kanuchi.domain.user.RevokeInvitationUseCase
import jp.co.yaz.kanuchi.domain.user.SuspendUserUseCase
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeUserManagementRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlinx.coroutines.CompletableDeferred
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class UsersViewModelTest : MainDispatcherTest() {
    private val profileRepository =
        FakeProfileRepository().apply { currentUserProfileResult = Result.success(FakeProfileRepository.ADMIN) }
    private val userManagementRepository =
        FakeUserManagementRepository().apply { invitedEmails += listOf("new@example.com", "taro@example.com") }

    private fun createViewModel() =
        UsersViewModel(
            GetUserManagementOverviewUseCase(profileRepository, userManagementRepository),
            InviteUserUseCase(userManagementRepository),
            RevokeInvitationUseCase(userManagementRepository),
            SuspendUserUseCase(userManagementRepository),
            ReactivateUserUseCase(userManagementRepository),
        )

    @Test
    fun `users and invitations are loaded`() {
        val viewModel = createViewModel()

        val overview = viewModel.uiState.value.overview!!
        assertEquals(FakeProfileRepository.ADMIN.id, overview.currentUserId)
        assertEquals(listOf(FakeProfileRepository.MEMBER, FakeProfileRepository.ADMIN), overview.users)
        assertEquals(
            listOf(Invitation("new@example.com", hasAccount = false), Invitation("taro@example.com", hasAccount = true)),
            overview.invitations,
        )
        assertTrue(viewModel.uiState.value.canOperate)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        userManagementRepository.getInvitedEmailsResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertNull(viewModel.uiState.value.overview)
        assertFalse(viewModel.uiState.value.canOperate)

        userManagementRepository.getInvitedEmailsResult = Result.success(Unit)
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(
            2,
            viewModel.uiState.value.overview!!
                .invitations.size,
        )
    }

    @Test
    fun `invited email is added to the list as normalized and the input is cleared`() {
        val viewModel = createViewModel()

        viewModel.onInviteEmailChanged(" Hanako2@Example.com ")
        viewModel.onInviteClicked()

        val state = viewModel.uiState.value
        assertEquals("", state.inviteEmail)
        assertEquals("hanako2@example.com", state.invitedEmail)
        assertNull(state.inviteError)
        assertEquals(
            "hanako2@example.com",
            state.overview!!
                .invitations
                .first()
                .email,
        )
    }

    @Test
    fun `invalid email is rejected`() {
        val viewModel = createViewModel()

        viewModel.onInviteEmailChanged("not-an-email")
        viewModel.onInviteClicked()

        assertEquals(InviteError.INVALID_EMAIL, viewModel.uiState.value.inviteError)
        assertEquals("not-an-email", viewModel.uiState.value.inviteEmail)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }

    @Test
    fun `already invited and save failures are distinguished`() {
        val viewModel = createViewModel()
        viewModel.onInviteEmailChanged("new@example.com")

        userManagementRepository.inviteResult = Result.failure(AlreadyInvitedException())
        viewModel.onInviteClicked()
        assertEquals(InviteError.ALREADY_INVITED, viewModel.uiState.value.inviteError)

        userManagementRepository.inviteResult = Result.failure(GenericDataFailureException())
        viewModel.onInviteClicked()
        assertEquals(InviteError.SAVE_FAILED, viewModel.uiState.value.inviteError)
    }

    @Test
    fun `editing the email clears the invite error`() {
        val viewModel = createViewModel()
        viewModel.onInviteEmailChanged("x")
        viewModel.onInviteClicked()

        viewModel.onInviteEmailChanged("x@example.com")

        assertNull(viewModel.uiState.value.inviteError)
    }

    @Test
    fun `invitation is revoked and the list is reloaded`() {
        val viewModel = createViewModel()

        viewModel.onRevokeInvitationClicked("new@example.com")

        assertEquals(
            listOf("taro@example.com"),
            viewModel.uiState.value.overview!!
                .invitations
                .map { it.email },
        )
        assertEquals(2, userManagementRepository.getInvitedEmailsCallCount)
    }

    @Test
    fun `suspension is requested only after confirmation`() {
        val viewModel = createViewModel()

        viewModel.onSuspendClicked(FakeProfileRepository.MEMBER)
        assertEquals(FakeProfileRepository.MEMBER, viewModel.uiState.value.suspendConfirmationTarget)
        assertTrue(userManagementRepository.suspensionRequests.isEmpty())

        viewModel.onSuspendConfirmed()

        assertNull(viewModel.uiState.value.suspendConfirmationTarget)
        assertEquals(listOf(FakeProfileRepository.MEMBER.id to true), userManagementRepository.suspensionRequests)
        assertEquals(2, userManagementRepository.getInvitedEmailsCallCount)
    }

    @Test
    fun `dismissing the confirmation does not suspend`() {
        val viewModel = createViewModel()

        viewModel.onSuspendClicked(FakeProfileRepository.MEMBER)
        viewModel.onSuspendDismissed()

        assertNull(viewModel.uiState.value.suspendConfirmationTarget)
        assertTrue(userManagementRepository.suspensionRequests.isEmpty())
    }

    @Test
    fun `user is reactivated`() {
        val viewModel = createViewModel()

        viewModel.onReactivateClicked(FakeProfileRepository.MEMBER)

        assertEquals(listOf(FakeProfileRepository.MEMBER.id to false), userManagementRepository.suspensionRequests)
    }

    @Test
    fun `last active admin failure is shown and can be dismissed`() {
        userManagementRepository.setUserSuspendedResult = Result.failure(LastActiveAdminRequiredException())
        val viewModel = createViewModel()

        viewModel.onSuspendClicked(FakeProfileRepository.MEMBER)
        viewModel.onSuspendConfirmed()

        assertEquals(UserActionError.LAST_ACTIVE_ADMIN_REQUIRED, viewModel.uiState.value.actionError)
        assertFalse(viewModel.uiState.value.isSubmitting)

        viewModel.onActionErrorDismissed()
        assertNull(viewModel.uiState.value.actionError)
    }

    @Test
    fun `other action failures are shown as save failures`() {
        userManagementRepository.revokeResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        viewModel.onRevokeInvitationClicked("new@example.com")

        assertEquals(UserActionError.SAVE_FAILED, viewModel.uiState.value.actionError)
    }

    @Test
    fun `actions are not requested twice while submitting`() {
        val gate = CompletableDeferred<Unit>()
        userManagementRepository.setUserSuspendedGate = gate
        val viewModel = createViewModel()

        viewModel.onReactivateClicked(FakeProfileRepository.MEMBER)
        assertTrue(viewModel.uiState.value.isSubmitting)
        assertFalse(viewModel.uiState.value.canOperate)
        viewModel.onReactivateClicked(FakeProfileRepository.MEMBER)
        viewModel.onSuspendClicked(FakeProfileRepository.MEMBER)

        assertEquals(1, userManagementRepository.suspensionRequests.size)
        assertNull(viewModel.uiState.value.suspendConfirmationTarget)

        gate.complete(Unit)
        assertFalse(viewModel.uiState.value.isSubmitting)
    }
}
