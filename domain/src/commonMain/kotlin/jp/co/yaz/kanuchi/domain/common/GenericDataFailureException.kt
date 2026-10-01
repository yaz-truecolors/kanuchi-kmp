package jp.co.yaz.kanuchi.domain.common

/**
 * データの取得・保存 (Supabase の DB へのアクセス) が失敗したが、Repository実装側から
 * 安全に表示できる具体的な理由が得られなかったことを示すマーカー例外。
 *
 * [jp.co.yaz.kanuchi.domain.auth.GenericAuthFailureException] と同じく文言は持たず、
 * presentation 層が strings.xml の汎用メッセージに変換して表示する。
 * 調査用に元の例外を [cause] として保持できる (message は明示的に null のままにする)。
 */
class GenericDataFailureException(
    cause: Throwable? = null,
) : Exception(null, cause)
