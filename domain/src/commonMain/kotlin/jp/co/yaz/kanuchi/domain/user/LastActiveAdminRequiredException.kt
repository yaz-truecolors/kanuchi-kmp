package jp.co.yaz.kanuchi.domain.user

/**
 * 利用停止・降格によって、利用中の admin が1人もいなくなるため拒否されたことを示すマーカー例外。
 * (DB 側のトリガー guard_active_admins が拒否する。複数の admin が同時に操作した場合に起こり得る)
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。
 */
class LastActiveAdminRequiredException(
    cause: Throwable? = null,
) : Exception(null, cause)
