package jp.co.yaz.kanuchi.domain.shift

/**
 * ログイン中のユーザーの勤務時間設定を取得するユースケース。
 * まだ保存していないユーザーには初期値 ([ShiftSettings.DEFAULT]、DB の列の既定値と同じ) を返す。
 */
class GetShiftSettingsUseCase(
    private val shiftSettingsRepository: ShiftSettingsRepository,
) {
    suspend operator fun invoke(): Result<ShiftSettings> = shiftSettingsRepository.getShiftSettings().map { it ?: ShiftSettings.DEFAULT }
}
