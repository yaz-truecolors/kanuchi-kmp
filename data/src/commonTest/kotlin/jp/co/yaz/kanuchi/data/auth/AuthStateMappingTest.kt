package jp.co.yaz.kanuchi.data.auth

import io.github.jan.supabase.annotations.SupabaseExperimental
import io.github.jan.supabase.auth.event.AuthEvent
import io.github.jan.supabase.auth.status.RefreshFailureCause
import io.github.jan.supabase.auth.status.SessionStatus
import io.github.jan.supabase.auth.user.UserInfo
import io.github.jan.supabase.auth.user.UserSession
import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.AuthenticatedUser
import jp.co.yaz.kanuchi.domain.auth.MagicLinkCallbackError
import kotlin.test.Test
import kotlin.test.assertEquals

class AuthStateMappingTest {
    private fun session(user: UserInfo?) =
        UserSession(
            accessToken = "access-token",
            refreshToken = "refresh-token",
            expiresIn = 3600,
            tokenType = "bearer",
            user = user,
        )

    @Test
    fun `initializing is mapped to unknown`() {
        assertEquals(AuthState.Unknown, SessionStatus.Initializing.toAuthState())
    }

    @Test
    fun `refresh failure is mapped to unknown instead of signed out`() {
        val status = SessionStatus.RefreshFailure(RefreshFailureCause.NetworkError(RuntimeException("offline")))

        assertEquals(AuthState.Unknown, status.toAuthState())
    }

    @Test
    fun `not authenticated is mapped to signed out`() {
        assertEquals(AuthState.SignedOut, SessionStatus.NotAuthenticated(isSignOut = true).toAuthState())
    }

    @Test
    fun `authenticated session is mapped to signed in with the user`() {
        val status = SessionStatus.Authenticated(session(UserInfo(aud = "authenticated", id = "user-1", email = "taro@example.com")))

        assertEquals(
            AuthState.SignedIn(AuthenticatedUser(id = "user-1", email = "taro@example.com")),
            status.toAuthState(),
        )
    }

    @Test
    fun `authenticated session without user info is mapped to signed out`() {
        assertEquals(AuthState.SignedOut, SessionStatus.Authenticated(session(user = null)).toAuthState())
    }

    @OptIn(SupabaseExperimental::class)
    @Test
    fun `otp expired error is mapped to expired`() {
        val error = AuthEvent.OtpError(error = "otp_expired", errorDescription = "Email link is invalid or has expired (access_denied)")

        assertEquals(MagicLinkCallbackError.EXPIRED, error.toMagicLinkCallbackError())
    }

    @OptIn(SupabaseExperimental::class)
    @Test
    fun `user banned error is mapped to suspended`() {
        // ローカルの Supabase Auth で、利用停止中のユーザーがマジックリンクを開いたときに返る値
        val error = AuthEvent.OtpError(error = "user_banned", errorDescription = "User is banned")

        assertEquals(MagicLinkCallbackError.SUSPENDED, error.toMagicLinkCallbackError())
    }

    @OptIn(SupabaseExperimental::class)
    @Test
    fun `other errors are mapped to unknown`() {
        val error = AuthEvent.OtpError(error = "unexpected_failure", errorDescription = "anything")

        assertEquals(MagicLinkCallbackError.UNKNOWN, error.toMagicLinkCallbackError())
    }
}
