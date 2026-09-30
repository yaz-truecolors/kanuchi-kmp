package jp.co.yaz.kanuchi.domain.auth

import kotlinx.coroutines.flow.Flow

/**
 * 認証まわりの操作を抽象化するRepositoryインターフェース。
 * 実装はdata層 (SupabaseAuthRepository) が提供する。
 */
interface AuthRepository {
    /**
     * 指定したメールアドレス宛にマジックリンクを送信する。
     *
     * 本アプリは「adminが招待したユーザーのみアカウントを作成できる」運用のため、
     * 招待リストに登録済みのメールアドレスであれば、初回の呼び出し時にアカウントが作成される。
     * 招待リストに無いメールアドレスを指定した場合は必ず失敗する ([EmailNotInvitedException])。
     * この設計上、「メールアドレスが招待済みかどうか」は本APIの結果から判別可能になる。
     * 入力ミス・未招待を本人に伝えるUXを優先して意図的に許容しているトレードオフである
     * (docs/requirements.md 5節)。
     *
     * 失敗時の例外は以下のいずれか:
     * - [EmailNotInvitedException] の場合、指定したメールアドレスが未招待であることを示す。
     *   presentation層が専用の案内文言 (strings.xml等) に変換して表示すること。
     * - [GenericAuthFailureException] の場合、それ以外の理由で具体的な原因が
     *   得られなかったことを示す。presentation層が自前の汎用メッセージに変換して表示すること。
     * - それ以外の例外で [Result.exceptionOrNull]?.message が非nullの場合、そのままUIに表示してよい
     *   (実装側が認証ヘッダー等の内部詳細を含まない安全なメッセージに変換する責任を持つ)
     *
     * domain/data層はいずれの場合もUI表示用の文言そのものは持たない。
     */
    suspend fun sendMagicLink(email: EmailAddress): Result<Unit>

    /**
     * ログイン状態の変化を購読する。購読開始時に現在の状態が1回流れ、以降は変化するたびに流れる。
     *
     * マジックリンクを開いてアプリに戻ってきた場合のログイン処理 (URLに含まれるトークンの取り込み) や、
     * 保存済みセッションの復元・自動更新は実装側で行われ、その結果がこのFlowに反映される。
     */
    fun observeAuthState(): Flow<AuthState>

    /**
     * マジックリンクを開いてアプリに戻ってきた際にログインできなかった場合、その理由を返す。
     *
     * 起動時に1回だけ発生し得るエラーのため、**最初の呼び出しでのみ値を返し、以降は常に null を返す**
     * (ログアウト後にログイン画面を開き直した際に、古いエラーが再表示されないようにするため)。
     * [observeAuthState] が [AuthState.Unknown] 以外になった後に呼び出すこと。
     */
    fun consumeMagicLinkCallbackError(): MagicLinkCallbackError?

    /**
     * ログアウトする。成功するとこの端末に保存されたセッションが削除され、
     * [observeAuthState] に [AuthState.SignedOut] が流れる。
     *
     * 失敗時は [GenericAuthFailureException] を返す (presentation 層が文言に変換して表示する)。
     */
    suspend fun signOut(): Result<Unit>
}
