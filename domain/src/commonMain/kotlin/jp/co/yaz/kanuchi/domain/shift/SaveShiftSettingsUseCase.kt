package jp.co.yaz.kanuchi.domain.shift

/**
 * ログイン中のユーザーの勤務時間設定を保存するユースケース。
 * 入力の検証 ([ShiftSettingsInput.toShiftSettings]) と保存 ([ShiftSettingsRepository.saveShiftSettings]) を束ねる。
 * 入力が条件を満たさない場合は [InvalidShiftSettingsException] で失敗し、保存しない。
 */
class SaveShiftSettingsUseCase(
    private val shiftSettingsRepository: ShiftSettingsRepository,
) {
    suspend operator fun invoke(input: ShiftSettingsInput): Result<ShiftSettings> {
        val settings = input.toShiftSettings().getOrElse { return Result.failure(it) }
        return shiftSettingsRepository.saveShiftSettings(settings)
    }
}
