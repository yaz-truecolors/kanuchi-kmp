package jp.co.yaz.kanuchi.domain.work

/**
 * 月の稼働時間合計と、本人の勤務時間設定の下限・上限 (`shift_settings.min_hours` / `max_hours`) との比較結果。
 */
enum class WorkingHoursRangeStatus {
    /** 下限未満。 */
    BELOW_MIN,

    /** 下限以上・上限以下。 */
    WITHIN_RANGE,

    /** 上限超過。 */
    ABOVE_MAX,
}
