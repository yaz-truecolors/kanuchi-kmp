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
     * Supabase Auth側の自動サインアップは無効化している。そのため未登録の
     * メールアドレスを指定した場合は必ず失敗する ([EmailNotInvitedException])。
     * この設計上、「メールアドレスが登録済みかどうか」は本APIの結果から
     * 判別可能になる (招待制を実現する以上、原理的に避けられないトレードオフ)。
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
