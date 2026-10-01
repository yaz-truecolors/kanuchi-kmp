package jp.co.yaz.kanuchi.domain.shift

/**
 * ログイン中のユーザー自身の勤務時間設定を扱うRepositoryインターフェース。
 * 実装はdata層 (SupabaseShiftSettingsRepository) が提供する。
 * 本人の設定だけを参照・保存できる (DB のアクセス制御 (RLS) で強制している)。
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
     * ログイン中のユーザーの勤務時間設定を保存する (まだ保存していなければ追加、保存済みなら上書き)。
     * 保存後の設定を返す。
     */
    suspend fun saveShiftSettings(settings: ShiftSettings): Result<ShiftSettings>
}
