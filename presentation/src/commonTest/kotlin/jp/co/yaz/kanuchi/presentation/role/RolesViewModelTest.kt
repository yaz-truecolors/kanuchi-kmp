package jp.co.yaz.kanuchi.presentation.role

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.profile.UserProfile
import jp.co.yaz.kanuchi.domain.profile.UserRole
import jp.co.yaz.kanuchi.domain.role.ChangeUserRoleUseCase
import jp.co.yaz.kanuchi.domain.role.GetRoleManagementOverviewUseCase
import jp.co.yaz.kanuchi.domain.user.LastActiveAdminRequiredException
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeUserRoleRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RolesViewModelTest : MainDispatcherTest() {
    private val me = UserProfile(id = "admin-1", email = "admin1@example.com", displayName = "admin1", role = UserRole.ADMIN)
    private val otherAdmin = UserProfile(id = "admin-2", email = "admin2@example.com", displayName = "admin2", role = UserRole.ADMIN)
    private val member = UserProfile(id = "user-1", email = "taro@example.com", displayName = "taro", role = UserRole.MEMBER)
    private val suspended =
        UserProfile(id = "user-2", email = "jiro@example.com", displayName = "jiro", role = UserRole.MEMBER, isSuspended = true)

    private val profileRepository =
        FakeProfileRepository().apply {
            currentUserProfileResult = Result.success(me)
            profilesResult = Result.success(listOf(me, otherAdmin, member, suspended))
        }
    private val userRoleRepository = FakeUserRoleRepository()

    private fun createViewModel() =
        RolesViewModel(
            GetRoleManagementOverviewUseCase(profileRepository),
            ChangeUserRoleUseCase(userRoleRepository),
        )

    private fun RolesUiState.item(id: String) = users.single { it.profile.id == id }

    @Test
    fun `users are loaded with whether their role can be changed`() {
        val state = createViewModel().uiState.value

        assertFalse(state.isLoading)
        assertFalse(state.loadFailed)
        assertEquals(listOf(me, otherAdmin, member, suspended), state.users.map { it.profile })
        assertTrue(state.item("admin-1").isCurrentUser)
        assertFalse(state.item("admin-1").canChangeRole)
        assertTrue(state.item("admin-2").canChangeRole)
        assertTrue(state.item("user-1").canChangeRole)
        assertFalse(state.item("user-2").canChangeRole)
    }

    @Test
    fun `load failure is shown and can be retried`() {
        profileRepository.profilesResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.loadFailed)
        assertTrue(
            viewModel.uiState.value.users
                .isEmpty(),
        )

        profileRepository.profilesResult = Result.success(listOf(me, member))
        viewModel.onRetryClicked()

        assertFalse(viewModel.uiState.value.loadFailed)
        assertEquals(
            listOf(me, member),
            viewModel.uiState.value.users
                .map { it.profile },
        )
    }

    @Test
    fun `confirmation is required before promoting a member`() {
        val viewModel = createViewModel()

        viewModel.onChangeRoleClicked("user-1", UserRole.ADMIN)

        assertEquals(RoleChange(member, UserRole.ADMIN), viewModel.uiState.value.pendingChange)
        assertTrue(userRoleRepository.roleChangeRequests.isEmpty())
    }

    @Test
    fun `dismissing the confirmation does not change the role`() {
        val viewModel = createViewModel()

        viewModel.onChangeRoleClicked("user-1", UserRole.ADMIN)
        viewModel.onChangeRoleDismissed()
        viewModel.onChangeRoleConfirmed()

        assertNull(viewModel.uiState.value.pendingChange)
        assertTrue(userRoleRepository.roleChangeRequests.isEmpty())
    }

    @Test
    fun `member is promoted after confirmation`() {
        val viewModel = createViewModel()

        viewModel.onChangeRoleClicked("user-1", UserRole.ADMIN)
        viewModel.onChangeRoleConfirmed()

        val state = viewModel.uiState.value
        assertEquals(listOf("user-1" to UserRole.ADMIN), userRoleRepository.roleChangeRequests)
        assertEquals(UserRole.ADMIN, state.item("user-1").profile.role)
        assertEquals(RoleChange(member, UserRole.ADMIN), state.completedChange)
        assertNull(state.pendingChange)
        assertFalse(state.isSaving)
        assertNull(state.saveError)
    }

    @Test
    fun `admin is demoted after confirmation`() {
        val viewModel = createViewModel()

        viewModel.onChangeRoleClicked("admin-2", UserRole.MEMBER)
        viewModel.onChangeRoleConfirmed()

        assertEquals(listOf("admin-2" to UserRole.MEMBER), userRoleRepository.roleChangeRequests)
        assertEquals(
            UserRole.MEMBER,
            viewModel.uiState.value
                .item("admin-2")
                .profile.role,
        )
    }

    @Test
    fun `own role and suspended users cannot be changed`() {
        val viewModel = createViewModel()

        viewModel.onChangeRoleClicked("admin-1", UserRole.MEMBER)
        assertNull(viewModel.uiState.value.pendingChange)

        viewModel.onChangeRoleClicked("user-2", UserRole.ADMIN)
        assertNull(viewModel.uiState.value.pendingChange)
    }

    @Test
    fun `changing to the current role is ignored`() {
        val viewModel = createViewModel()

        viewModel.onChangeRoleClicked("admin-2", UserRole.ADMIN)

        assertNull(viewModel.uiState.value.pendingChange)
    }

    @Test
    fun `saving state is shown and other changes are not accepted while saving`() {
        userRoleRepository.changeRoleResult = null
        val viewModel = createViewModel()

        viewModel.onChangeRoleClicked("user-1", UserRole.ADMIN)
        viewModel.onChangeRoleConfirmed()

        assertEquals(RoleChange(member, UserRole.ADMIN), viewModel.uiState.value.savingChange)
        assertTrue(viewModel.uiState.value.isSaving)

        viewModel.onChangeRoleClicked("admin-2", UserRole.MEMBER)
        assertNull(viewModel.uiState.value.pendingChange)

        userRoleRepository.pendingResult.complete(Result.success(Unit))

        assertFalse(viewModel.uiState.value.isSaving)
        assertEquals(
            UserRole.ADMIN,
            viewModel.uiState.value
                .item("user-1")
                .profile.role,
        )
        assertEquals(1, userRoleRepository.roleChangeRequests.size)
    }

    @Test
    fun `generic save failure is shown and the role is kept`() {
        userRoleRepository.changeRoleResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        viewModel.onChangeRoleClicked("admin-2", UserRole.MEMBER)
        viewModel.onChangeRoleConfirmed()

        val state = viewModel.uiState.value
        assertEquals(RoleChangeError.GENERIC, state.saveError)
        assertFalse(state.isSaving)
        assertNull(state.completedChange)
        assertEquals(UserRole.ADMIN, state.item("admin-2").profile.role)
    }

    @Test
    fun `last active admin failure is shown and the list is reloaded`() {
        userRoleRepository.changeRoleResult = Result.failure(LastActiveAdminRequiredException())
        val viewModel = createViewModel()
        // 画面を開いた後に、他の admin によって admin-1 (自分) 以外の admin が降格され、admin-2 が利用停止されていた
        val suspendedAdmin = otherAdmin.copy(isSuspended = true)
        profileRepository.profilesResult = Result.success(listOf(me, suspendedAdmin, member))

        viewModel.onChangeRoleClicked("admin-2", UserRole.MEMBER)
        viewModel.onChangeRoleConfirmed()

        val state = viewModel.uiState.value
        assertEquals(RoleChangeError.LAST_ACTIVE_ADMIN_REQUIRED, state.saveError)
        assertEquals(listOf(me, suspendedAdmin, member), state.users.map { it.profile })
        assertFalse(state.item("admin-2").canChangeRole)
    }

    @Test
    fun `previous result is cleared when the next change is saved`() {
        userRoleRepository.changeRoleResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()
        viewModel.onChangeRoleClicked("admin-2", UserRole.MEMBER)
        viewModel.onChangeRoleConfirmed()

        userRoleRepository.changeRoleResult = Result.success(Unit)
        viewModel.onChangeRoleClicked("admin-2", UserRole.MEMBER)
        viewModel.onChangeRoleConfirmed()

        assertNull(viewModel.uiState.value.saveError)
        assertEquals(RoleChange(otherAdmin, UserRole.MEMBER), viewModel.uiState.value.completedChange)
    }
}
