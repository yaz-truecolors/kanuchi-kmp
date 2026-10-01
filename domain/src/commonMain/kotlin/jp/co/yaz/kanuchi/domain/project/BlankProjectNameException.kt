package jp.co.yaz.kanuchi.domain.project

/**
 * 案件名が空 (空白のみを含む) であることを示すマーカー例外。
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。
 */
class BlankProjectNameException : Exception()
