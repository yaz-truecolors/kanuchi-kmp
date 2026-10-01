package jp.co.yaz.kanuchi.presentation.home

import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.AuthenticatedUser
import jp.co.yaz.kanuchi.domain.auth.GenericAuthFailureException
import jp.co.yaz.kanuchi.domain.auth.ObserveAuthStateUseCase
import jp.co.yaz.kanuchi.domain.auth.SignOutUseCase
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import jp.co.yaz.kanuchi.domain.profile.GetCurrentUserProfileUseCase
import jp.co.yaz.kanuchi.presentation.testing.FakeAuthRepository
import jp.co.yaz.kanuchi.presentation.testing.FakeProfileRepository
import jp.co.yaz.kanuchi.presentation.testing.MainDispatcherTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class HomeViewModelTest : MainDispatcherTest() {
    private val repository =
        FakeAuthRepository().apply {
            authState.value = AuthState.SignedIn(AuthenticatedUser(id = "user-1", email = "taro@example.com"))
        }

    private val profileRepository = FakeProfileRepository()

    private fun createViewModel() =
        HomeViewModel(
            ObserveAuthStateUseCase(repository),
            GetCurrentUserProfileUseCase(profileRepository),
            SignOutUseCase(repository),
        ).also { it.onScreenShown() }

    @Test
    fun `profile is loaded and admin menu is hidden for members`() {
        val viewModel = createViewModel()

        assertEquals(FakeProfileRepository.MEMBER, viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.isLoadingProfile)
        assertFalse(viewModel.uiState.value.showsAdminMenu)
    }

    @Test
    fun `admin menu is shown for admins`() {
        profileRepository.currentUserProfileResult = Result.success(FakeProfileRepository.ADMIN)

        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.showsAdminMenu)
    }

    @Test
    fun `profile load failure hides admin menu and can be retried`() {
        profileRepository.currentUserProfileResult = Result.failure(GenericDataFailureException())
        val viewModel = createViewModel()

        assertTrue(viewModel.uiState.value.profileLoadFailed)
        assertFalse(viewModel.uiState.value.showsAdminMenu)

        profileRepository.currentUserProfileResult = Result.success(FakeProfileRepository.ADMIN)
        viewModel.onRetryProfileClicked()

        assertFalse(viewModel.uiState.value.profileLoadFailed)
        assertTrue(viewModel.uiState.value.showsAdminMenu)
        assertEquals(2, profileRepository.getCurrentUserProfileCallCount)
    }

    @Test
    fun `profile is reloaded when the screen is shown again`() {
        val viewModel = createViewModel()

        profileRepository.currentUserProfileResult = Result.success(FakeProfileRepository.MEMBER.copy(displayName = "山田"))
        viewModel.onScreenShown()

        assertEquals(
            "山田",
            viewModel.uiState.value.profile
                ?.displayName,
        )
        assertEquals(2, profileRepository.getCurrentUserProfileCallCount)
    }

    @Test
    fun `previous profile is kept when reloading fails`() {
        val viewModel = createViewModel()

        profileRepository.currentUserProfileResult = Result.failure(GenericDataFailureException())
        viewModel.onScreenShown()

        assertEquals(FakeProfileRepository.MEMBER, viewModel.uiState.value.profile)
        assertFalse(viewModel.uiState.value.isLoadingProfile)
        assertFalse(viewModel.uiState.value.profileLoadFailed)
    }

    @Test
    fun `email of the signed in user is shown`() {
        val viewModel = createViewModel()

        assertEquals("taro@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun `email is kept when auth state becomes signed out or unknown`() {
        val viewModel = createViewModel()

        repository.authState.value = AuthState.Unknown
        repository.authState.value = AuthState.SignedOut

        assertEquals("taro@example.com", viewModel.uiState.value.email)
    }

    @Test
    fun `signing out state is kept after success until the screen is replaced`() {
        val viewModel = createViewModel()

        viewModel.onSignOutClicked()
        assertTrue(viewModel.uiState.value.isSigningOut)

        repository.signOutResult.complete(Result.success(Unit))

        assertTrue(viewModel.uiState.value.isSigningOut)
        assertFalse(viewModel.uiState.value.signOutFailed)
    }

    @Test
    fun `sign out failure is shown and the button is enabled again`() {
        val viewModel = createViewModel()

        viewModel.onSignOutClicked()
        repository.signOutResult.complete(Result.failure(GenericAuthFailureException()))

        assertFalse(viewModel.uiState.value.isSigningOut)
        assertTrue(viewModel.uiState.value.signOutFailed)
    }

    @Test
    fun `sign out is not requested twice while signing out`() {
        val viewModel = createViewModel()

        viewModel.onSignOutClicked()
        viewModel.onSignOutClicked()

        assertEquals(1, repository.signOutCallCount)
    }
}
