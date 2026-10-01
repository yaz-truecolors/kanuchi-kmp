package jp.co.yaz.kanuchi.domain.shift

/**
 * 勤務時間設定の入力が満たしていない条件。文言は持たず、presentation 層が strings.xml の文言に変換して表示する。
 *
 * @property field 違反を表示する入力欄
 */
enum class ShiftSettingsViolation(
    val field: ShiftSettingsField,
) {
    /** 始業時刻が `HH:mm` 形式の時刻でない。 */
    START_TIME_INVALID_FORMAT(ShiftSettingsField.START_TIME),

    /** 終業時刻が `HH:mm` 形式の時刻でない。 */
    END_TIME_INVALID_FORMAT(ShiftSettingsField.END_TIME),

    /** 終業時刻が始業時刻より後でない (DB の `end_time > start_time`)。 */
    END_TIME_NOT_AFTER_START_TIME(ShiftSettingsField.END_TIME),

    /** 休憩時間が0以上の数値 (小数第2位まで) でない。 */
    BREAK_HOURS_INVALID_FORMAT(ShiftSettingsField.BREAK_HOURS),

    /** 休憩時間が [ShiftSettings.MAX_BREAK_HOURS] を超えている。 */
    BREAK_HOURS_TOO_LARGE(ShiftSettingsField.BREAK_HOURS),

    /** 稼働時間の下限が0以上の数値 (小数第2位まで) でない。 */
    MIN_HOURS_INVALID_FORMAT(ShiftSettingsField.MIN_HOURS),

    /** 稼働時間の下限が [ShiftSettings.MAX_MONTHLY_HOURS] を超えている。 */
    MIN_HOURS_TOO_LARGE(ShiftSettingsField.MIN_HOURS),

    /** 稼働時間の上限が0以上の数値 (小数第2位まで) でない。 */
    MAX_HOURS_INVALID_FORMAT(ShiftSettingsField.MAX_HOURS),

    /** 稼働時間の上限が [ShiftSettings.MAX_MONTHLY_HOURS] を超えている。 */
    MAX_HOURS_TOO_LARGE(ShiftSettingsField.MAX_HOURS),

    /** 稼働時間の下限が上限より大きい (DB の `min_hours <= max_hours`)。上限の欄に表示する。 */
    MIN_HOURS_EXCEEDS_MAX_HOURS(ShiftSettingsField.MAX_HOURS),
}

/** 勤務時間設定の入力欄。 */
enum class ShiftSettingsField {
    START_TIME,
    END_TIME,
    BREAK_HOURS,
    MIN_HOURS,
    MAX_HOURS,
}

/**
 * 勤務時間設定の入力が条件を満たしていないため保存できないことを示す例外。
 * 文言は持たず、presentation 層が [violations] を strings.xml の文言に変換して表示する。
 */
class InvalidShiftSettingsException(
    val violations: Set<ShiftSettingsViolation>,
) : Exception()
