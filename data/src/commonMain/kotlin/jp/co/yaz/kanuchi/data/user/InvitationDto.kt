package jp.co.yaz.kanuchi.data.user

import kotlinx.serialization.Serializable

/**
 * `invitations` テーブルの1行 (アプリが参照・追加する列のみ)。
 * 追加時も email だけを送る (authenticated には email 列の INSERT 権限のみがあり、invited_by はトリガーが設定する)。
 */
@Serializable
internal data class InvitationDto(
    val email: String,
) {
    companion object {
        /** 取得する列 (`Columns.list` に渡す)。 */
        const val COLUMNS = "email"
    }
}
