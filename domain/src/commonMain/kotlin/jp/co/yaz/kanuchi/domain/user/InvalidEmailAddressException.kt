package jp.co.yaz.kanuchi.domain.user

/**
 * 招待しようとしたメールアドレスの形式が正しくないことを示すマーカー例外。
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。
 */
class InvalidEmailAddressException : Exception()
