package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.auth.AuthRepository
import jp.co.yaz.kanuchi.domain.auth.AuthState
import jp.co.yaz.kanuchi.domain.auth.EmailAddress
import jp.co.yaz.kanuchi.domain.auth.MagicLinkCallbackError
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow

/**
 * ViewModel のテスト用の [AuthRepository] の偽物。
 */
internal class FakeAuthRepository : AuthRepository {
    var sendMagicLinkResult: Result<Unit> = Result.success(Unit)

    val authState = MutableStateFlow<AuthState>(AuthState.Unknown)

    var magicLinkCallbackError: MagicLinkCallbackError? = null

    var signOutCallCount = 0

    /** signOut() の結果。complete するまで signOut() は完了しない (処理中の状態をテストするため)。 */
    var signOutResult = CompletableDeferred<Result<Unit>>()

    override suspend fun sendMagicLink(email: EmailAddress): Result<Unit> = sendMagicLinkResult

    override fun observeAuthState(): Flow<AuthState> = authState

    override fun consumeMagicLinkCallbackError(): MagicLinkCallbackError? = magicLinkCallbackError.also { magicLinkCallbackError = null }

    override suspend fun signOut(): Result<Unit> {
        signOutCallCount++
        return signOutResult.await()
    }
}
