package jp.co.yaz.kanuchi.domain.user

/**
 * 招待しようとしたメールアドレスが、既に招待リストに登録されていることを示すマーカー例外。
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。
 */
class AlreadyInvitedException(
    cause: Throwable? = null,
) : Exception(null, cause)
