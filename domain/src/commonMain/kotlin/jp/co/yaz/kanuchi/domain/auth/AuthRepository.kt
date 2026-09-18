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
     * 成功/失敗はネットワークエラーやSupabase側のエラーのみを表し、
     * 「そのメールアドレスが実在するかどうか」は判定しない
     * (メール列挙攻撃を防ぐためSupabase Auth側も同様の挙動をする)。
     *
     * 失敗時の例外は以下のいずれか:
     * - [Result.exceptionOrNull]?.message が非nullの場合、そのままUIに表示してよい
     *   (実装側が認証ヘッダー等の内部詳細を含まない安全なメッセージに変換する責任を持つ)
     * - [GenericAuthFailureException] の場合、具体的な理由が得られなかったことを示す。
     *   presentation層が自前の汎用メッセージ (strings.xml等) に変換して表示すること。
     *   domain/data層はUI表示用の文言そのものを持たない。
     */
    suspend fun sendMagicLink(email: EmailAddress): Result<Unit>
}
