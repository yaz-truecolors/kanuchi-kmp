package jp.co.yaz.kanuchi.domain.shift

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay

/**
 * ユーザーごとの勤務時間設定。DB の `shift_settings` テーブルの1行に対応する。
 * 日次入力・集計の初期値や判定に使う。
 *
 * DB の check 制約と同じ条件 ([violationsOf]) を満たさないインスタンスは作れない
 * (違反する場合はコンストラクタが [IllegalArgumentException] を投げる)。利用者の入力から作る場合は
 * [ShiftSettingsInput.toShiftSettings] を使う。
 * 休憩時間・稼働時間の下限が0以上であること (`break_hours >= 0`、`min_hours >= 0`) は [Hours] が保証する。
 *
 * @property startTime 定時の始業時刻
 * @property endTime 定時の終業時刻。[startTime] より後
 * @property breakHours 1日の休憩時間 (時間)
 * @property minHours 月の稼働時間の下限 (時間)
 * @property maxHours 月の稼働時間の上限 (時間)。[minHours] 以上
 */
data class ShiftSettings(
    val startTime: TimeOfDay,
    val endTime: TimeOfDay,
    val breakHours: Hours,
    val minHours: Hours,
    val maxHours: Hours,
) {
    init {
        val violations = violationsOf(startTime, endTime, breakHours, minHours, maxHours)
        require(violations.isEmpty()) { "invalid shift settings: $violations" }
    }

    companion object {
        /** 休憩時間の上限。DB の列の型 `numeric(4, 2)` に収まる最大値。 */
        val MAX_BREAK_HOURS: Hours = Hours.ofHundredths(9_999)

        /** 月の稼働時間の下限・上限に設定できる最大値。DB の列の型 `numeric(6, 2)` に収まる最大値。 */
        val MAX_MONTHLY_HOURS: Hours = Hours.ofHundredths(999_999)

        /**
         * まだ設定を保存していないユーザーの初期値。DB の列の既定値
         * (`supabase/migrations/20260917010000_initial_schema.sql` の `shift_settings`) と一致させること。
         */
        val DEFAULT: ShiftSettings =
            ShiftSettings(
                startTime = TimeOfDay.of(9, 30),
                endTime = TimeOfDay.of(18, 30),
                breakHours = Hours.ofHundredths(100),
                minHours = Hours.ofHundredths(14_000),
                maxHours = Hours.ofHundredths(18_000),
            )

        /**
         * 値の組み合わせが満たしていない条件の一覧 (すべて満たしていれば空)。
         * DB の check 制約 (`end_time > start_time`、`min_hours <= max_hours`) と、列の型に収まる範囲を確認する。
         * null (入力の形式が正しくなかった値) が関わる条件は確認しない。
         */
        internal fun violationsOf(
            startTime: TimeOfDay?,
            endTime: TimeOfDay?,
            breakHours: Hours?,
            minHours: Hours?,
            maxHours: Hours?,
        ): Set<ShiftSettingsViolation> =
            buildSet {
                if (startTime != null && endTime != null && endTime <= startTime) {
                    add(ShiftSettingsViolation.END_TIME_NOT_AFTER_START_TIME)
                }
                if (breakHours != null && breakHours > MAX_BREAK_HOURS) add(ShiftSettingsViolation.BREAK_HOURS_TOO_LARGE)
                if (minHours != null && minHours > MAX_MONTHLY_HOURS) add(ShiftSettingsViolation.MIN_HOURS_TOO_LARGE)
                if (maxHours != null && maxHours > MAX_MONTHLY_HOURS) add(ShiftSettingsViolation.MAX_HOURS_TOO_LARGE)
                if (minHours != null && maxHours != null && minHours > maxHours) {
                    add(ShiftSettingsViolation.MIN_HOURS_EXCEEDS_MAX_HOURS)
                }
            }
    }
}
