package jp.co.yaz.kanuchi.domain.auth

/**
 * 認証まわりの操作を抽象化するRepositoryインターフェース。
 * 実装はdata層 (SupabaseAuthRepository) が提供する。
 *
 * v1スコープ: マジックリンクの送信のみ。セッション復元・現在のログイン状態監視は
 * 別タスクとして後で追加する (docs/requirements.md 参照)。
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
}
