package jp.co.yaz.kanuchi.domain.project

/**
 * 同じ名前の案件が既にあるため、追加・名前変更できなかったことを示すマーカー例外
 * (DB の `projects.name` の unique 制約違反)。
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。調査用に元の例外を [cause] として保持できる。
 */
class DuplicateProjectNameException(
    cause: Throwable? = null,
) : Exception(null, cause)
