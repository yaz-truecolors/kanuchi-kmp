package jp.co.yaz.kanuchi.domain.auth

/**
 * マジックリンク送信が失敗したが、Repository実装側から安全に表示できる具体的な理由が
 * 得られなかったことを示すマーカー例外。
 *
 * このメッセージ文言自体は保持しない。ローカライズされた汎用エラーメッセージへの変換は
 * presentation層の責務とする (domain/data層はUI表示文言を持たない、という方針のため)。
 */
class GenericAuthFailureException : Exception()
