package jp.co.yaz.kanuchi.domain.auth

/**
 * 未登録（未招待）のメールアドレスへマジックリンクを送ろうとしたことを示すマーカー例外。
 *
 * 本アプリは「adminが招待したユーザーのみアカウントを作成できる」運用のため、
 * Supabase Auth側の自動サインアップは無効化している (SupabaseAuthRepository参照)。
 * そのため、未登録メールアドレスでのログイン試行は失敗として扱われる。
 *
 * このメッセージ文言自体は保持しない。ローカライズされたメッセージへの変換は
 * presentation層の責務とする ([GenericAuthFailureException] と同様の設計方針)。
 */
class EmailNotInvitedException : Exception()
