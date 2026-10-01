package jp.co.yaz.kanuchi.presentation.settings

import jp.co.yaz.kanuchi.domain.profile.DisplayNameViolation
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsField
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsInput
import jp.co.yaz.kanuchi.domain.shift.ShiftSettingsViolation

/**
 * 個人設定画面のUI状態。表示名と勤務時間設定は別々に保存する (保存先のテーブルが別のため)。
 */
data class SettingsUiState(
    /** 表示名・勤務時間設定を読み込み中か。初回の表示時は読み込み中から始まる。 */
    val isLoading: Boolean = true,
    val loadFailed: Boolean = false,
    val displayName: String = "",
    /** 表示名が満たしていない条件 (保存しようとしたときに分かったもの)。 */
    val displayNameViolation: DisplayNameViolation? = null,
    val isSavingDisplayName: Boolean = false,
    val displayNameSaveFailed: Boolean = false,
    val displayNameSaved: Boolean = false,
    val shiftSettings: ShiftSettingsInput = EMPTY_SHIFT_SETTINGS,
    /** 勤務時間設定が満たしていない条件 (保存しようとしたときに分かったもの)。 */
    val shiftSettingsViolations: Set<ShiftSettingsViolation> = emptySet(),
    val isSavingShiftSettings: Boolean = false,
    val shiftSettingsSaveFailed: Boolean = false,
    val shiftSettingsSaved: Boolean = false,
) {
    /** 入力欄を表示するか (読み込みが成功した後のみ)。 */
    val showsForm: Boolean get() = !isLoading && !loadFailed

    /** 指定した入力欄に表示する違反 (複数ある場合は1つ目)。 */
    fun shiftSettingsViolationOf(field: ShiftSettingsField): ShiftSettingsViolation? =
        shiftSettingsViolations.filter { it.field == field }.minByOrNull { it.ordinal }

    private companion object {
        val EMPTY_SHIFT_SETTINGS = ShiftSettingsInput(startTime = "", endTime = "", breakHours = "", minHours = "", maxHours = "")
    }
}
