package jp.co.yaz.kanuchi.domain.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ユースケースのテスト用の [AuthRepository] の偽物。呼び出し内容を記録し、返す値をテストから差し替えられる。
 */
internal class FakeAuthRepository : AuthRepository {
    var lastRequestedEmail: EmailAddress? = null
    var sendMagicLinkResult: Result<Unit> = Result.success(Unit)

    val authState = MutableStateFlow<AuthState>(AuthState.Unknown)

    var magicLinkCallbackError: MagicLinkCallbackError? = null

    var signOutCallCount = 0
    var signOutResult: Result<Unit> = Result.success(Unit)

    override suspend fun sendMagicLink(email: EmailAddress): Result<Unit> {
        lastRequestedEmail = email
        return sendMagicLinkResult
    }

    override fun observeAuthState(): Flow<AuthState> = authState

    override fun consumeMagicLinkCallbackError(): MagicLinkCallbackError? = magicLinkCallbackError.also { magicLinkCallbackError = null }

    override suspend fun signOut(): Result<Unit> {
        signOutCallCount++
        return signOutResult
    }
}
