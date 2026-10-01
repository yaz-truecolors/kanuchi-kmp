package jp.co.yaz.kanuchi.domain.work

/**
 * 日ごとの印 (DB の `work_records.flag`)。印の付いた日の稼働時間は 0 とする。
 */
enum class DayFlag {
    /** 「休」: 個人の休暇 (DB の値は `holiday`)。その月の営業日数から除く。 */
    VACATION,

    /** 「欠」: 欠勤 (DB の値は `absence`)。営業日数には含める。 */
    ABSENCE,
}
