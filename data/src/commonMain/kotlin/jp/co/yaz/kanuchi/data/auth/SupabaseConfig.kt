package jp.co.yaz.kanuchi.data.auth

/**
 * Supabaseプロジェクトへの接続情報。
 *
 * ここに含まれる値はどちらも「publishable key」方式の公開可能な値であり、
 * クライアントコードに同梱されることを前提に設計されている
 * (実際のアクセス制御はSupabase側のRow Level Securityが担う)。
 * secret key・service_role keyは絶対にここに追加しないこと。
 *
 * 詳細は supabase/README.md を参照。
 */
internal object SupabaseConfig {
    const val PROJECT_URL: String = "https://azvfmvyquyrgzbkdfkga.supabase.co"
    const val PUBLISHABLE_KEY: String = "sb_publishable_h8cyJFt09dZ0PIZrSMY2EA_e_SzkCkZ"
}
