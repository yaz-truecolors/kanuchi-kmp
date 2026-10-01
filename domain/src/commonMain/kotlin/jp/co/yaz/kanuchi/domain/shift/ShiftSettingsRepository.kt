package jp.co.yaz.kanuchi.domain.shift

/**
 * ユーザーの勤務時間設定を扱うRepositoryインターフェース。
 * 実装はdata層 (SupabaseShiftSettingsRepository) が提供する。
 * 保存できるのは本人の設定だけ、参照できるのは本人の設定と、admin なら全員分 (DB のアクセス制御 (RLS) で強制している)。
 *
 * 失敗時は [jp.co.yaz.kanuchi.domain.common.GenericDataFailureException] を返す
 * (presentation 層が文言に変換して表示する)。
 */
interface ShiftSettingsRepository {
    /**
     * ログイン中のユーザーの勤務時間設定を取得する。まだ保存していない場合は null。
     */
    suspend fun getShiftSettings(): Result<ShiftSettings?>

    /**
     * [userId] のユーザーの勤務時間設定を取得する。まだ保存していない場合は null。
     * 他のユーザーの設定は admin だけが参照できる (参照できない場合も、エラーにならず null になる)。
     */
    suspend fun getShiftSettingsOf(userId: String): Result<ShiftSettings?>

    /**
     * ログイン中のユーザーの勤務時間設定を保存する (まだ保存していなければ追加、保存済みなら上書き)。
     * 保存後の設定を返す。
     */
    suspend fun saveShiftSettings(settings: ShiftSettings): Result<ShiftSettings>
}
