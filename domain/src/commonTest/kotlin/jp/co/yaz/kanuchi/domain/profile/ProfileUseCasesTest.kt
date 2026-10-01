package jp.co.yaz.kanuchi.domain.profile

import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue

class ProfileUseCasesTest {
    @Test
    fun `current user profile is returned from the repository`() =
        runTest {
            val useCase = GetCurrentUserProfileUseCase(FakeProfileRepository())

            assertEquals(FakeProfileRepository.PROFILE, useCase().getOrThrow())
        }

    @Test
    fun `failure of current user profile is propagated as-is`() =
        runTest {
            val repository =
                FakeProfileRepository().apply { currentUserProfileResult = Result.failure(GenericDataFailureException()) }

            assertIs<GenericDataFailureException>(GetCurrentUserProfileUseCase(repository)().exceptionOrNull())
        }

    @Test
    fun `profiles are returned from the repository`() =
        runTest {
            val useCase = GetProfilesUseCase(FakeProfileRepository())

            assertEquals(listOf(FakeProfileRepository.PROFILE), useCase().getOrThrow())
        }

    @Test
    fun `display name is validated and updated`() =
        runTest {
            val repository = FakeProfileRepository()

            val profile = UpdateDisplayNameUseCase(repository)("  山田  ").getOrThrow()

            assertEquals("山田", profile.displayName)
            assertEquals(listOf("山田"), repository.updatedDisplayNames.map { it.value })
        }

    @Test
    fun `invalid display name is not saved`() =
        runTest {
            val repository = FakeProfileRepository()

            val error = assertIs<InvalidDisplayNameException>(UpdateDisplayNameUseCase(repository)(" ").exceptionOrNull())

            assertEquals(DisplayNameViolation.BLANK, error.violation)
            assertTrue(repository.updatedDisplayNames.isEmpty())
        }

    @Test
    fun `failure of display name update is propagated as-is`() =
        runTest {
            val repository =
                FakeProfileRepository().apply { updateDisplayNameResult = Result.failure(GenericDataFailureException()) }

            assertIs<GenericDataFailureException>(UpdateDisplayNameUseCase(repository)("山田").exceptionOrNull())
        }

    @Test
    fun `only admin role is admin`() {
        assertTrue(FakeProfileRepository.PROFILE.copy(role = UserRole.ADMIN).isAdmin)
        assertFalse(FakeProfileRepository.PROFILE.isAdmin)
    }
}
