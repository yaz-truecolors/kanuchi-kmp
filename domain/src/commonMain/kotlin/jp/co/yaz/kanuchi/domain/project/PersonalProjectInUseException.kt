package jp.co.yaz.kanuchi.domain.project

/**
 * 当月以降 (日本時間) の日に工数が配分されているため、個人案件を削除できなかったことを示すマーカー例外。
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。調査用に元の例外を [cause] として保持できる。
 */
class PersonalProjectInUseException(
    cause: Throwable? = null,
) : Exception(null, cause)
