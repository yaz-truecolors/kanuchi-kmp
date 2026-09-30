package jp.co.yaz.kanuchi.domain.auth

/**
 * 認証まわりの操作 (マジックリンク送信・ログアウト等) が失敗したが、Repository実装側から
 * 安全に表示できる具体的な理由が得られなかったことを示すマーカー例外。
 *
 * このメッセージ文言自体は保持しない。ローカライズされた汎用エラーメッセージへの変換は
 * presentation層の責務とする (domain/data層はUI表示文言を持たない、という方針のため)。
 *
 * 調査用に元の例外を [cause] として保持できる。`Exception(cause)` は cause の toString() を
 * message に設定してしまうため、message は明示的に null のままにする。
 */
class GenericAuthFailureException(
    cause: Throwable? = null,
) : Exception(null, cause)
