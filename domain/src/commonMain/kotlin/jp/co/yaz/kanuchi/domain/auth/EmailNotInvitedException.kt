package jp.co.yaz.kanuchi.domain.auth

/**
 * 未招待のメールアドレスへマジックリンクを送ろうとしたことを示すマーカー例外。
 *
 * 本アプリは「adminが招待したユーザーのみアカウントを作成できる」運用のため、
 * 招待リストに無いメールアドレスでのアカウント作成はサーバー側 (Supabase Auth の
 * Before User Created Hook) で拒否される (SupabaseAuthRepository参照)。
 *
 * このメッセージ文言自体は保持しない。ローカライズされたメッセージへの変換は
 * presentation層の責務とする ([GenericAuthFailureException] と同様の設計方針)。
 */
class EmailNotInvitedException : Exception()
