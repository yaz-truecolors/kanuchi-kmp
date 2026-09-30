package jp.co.yaz.kanuchi.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * ログイン状態の変化を購読するユースケース。
 * アプリ全体の画面の出し分け (ログイン画面 / ログイン後の画面) に使う。
 */
class ObserveAuthStateUseCase(
    private val authRepository: AuthRepository,
) {
    operator fun invoke(): Flow<AuthState> = authRepository.observeAuthState()
}
