package jp.co.yaz.kanuchi.domain.shift

/**
 * ユースケースのテスト用の [ShiftSettingsRepository] の偽物。返す値をテストから差し替えられる。
 */
internal class FakeShiftSettingsRepository : ShiftSettingsRepository {
    var getResult: Result<ShiftSettings?> = Result.success(null)
    var saveResult: Result<ShiftSettings>? = null
    val savedSettings = mutableListOf<ShiftSettings>()

    /** getShiftSettingsOf() の結果 (ユーザーIDごと)。無ければ未保存 (null) を返す。 */
    val getOfResults = mutableMapOf<String, Result<ShiftSettings?>>()

    override suspend fun getShiftSettings(): Result<ShiftSettings?> = getResult

    override suspend fun getShiftSettingsOf(userId: String): Result<ShiftSettings?> = getOfResults[userId] ?: Result.success(null)

    override suspend fun saveShiftSettings(settings: ShiftSettings): Result<ShiftSettings> {
        savedSettings += settings
        return saveResult ?: Result.success(settings)
    }
}
