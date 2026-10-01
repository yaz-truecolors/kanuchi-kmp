package jp.co.yaz.kanuchi.domain.profile

/**
 * ユーザーの権限。DB の `profiles.role` (`member` / `admin`) に対応する。
 */
enum class UserRole {
    MEMBER,
    ADMIN,
}
