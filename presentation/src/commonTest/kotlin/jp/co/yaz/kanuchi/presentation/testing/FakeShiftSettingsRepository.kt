package jp.co.yaz.kanuchi.presentation.testing

import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsRepository
import kotlinx.coroutines.CompletableDeferred

/**
 * ViewModel のテスト用の [ShiftSettingsRepository] の偽物。
 */
internal class FakeShiftSettingsRepository : ShiftSettingsRepository {
    var getResult: Result<ShiftSettings?> = Result.success(null)

    /** saveShiftSettings() の結果。null の場合は渡された設定をそのまま返す (保存に成功する)。 */
    var saveResult: Result<ShiftSettings>? = null
    val savedSettings = mutableListOf<ShiftSettings>()
    var getCallCount = 0

    /** null でない場合、saveShiftSettings() はこれが complete するまで完了しない (保存中の状態をテストするため)。 */
    var saveGate: CompletableDeferred<Unit>? = null

    override suspend fun getShiftSettings(): Result<ShiftSettings?> {
        getCallCount++
        return getResult
    }

    override suspend fun saveShiftSettings(settings: ShiftSettings): Result<ShiftSettings> {
        savedSettings += settings
        saveGate?.await()
        return saveResult ?: Result.success(settings)
    }
}
