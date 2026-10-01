package jp.co.yaz.kanuchi.domain.work

/**
 * 日次入力 ([WorkRecordInput]) が満たしていない条件 (案件ごとの工数を除く)。
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。
 *
 * @property field 違反を表示する入力欄
 */
enum class WorkRecordViolation(
    val field: WorkRecordField,
) {
    /** 出勤時刻が `HH:mm` 形式の時刻でない。 */
    CLOCK_IN_INVALID_FORMAT(WorkRecordField.CLOCK_IN),

    /** 退勤時刻が `HH:mm` 形式の時刻でない。 */
    CLOCK_OUT_INVALID_FORMAT(WorkRecordField.CLOCK_OUT),

    /** 出勤・退勤の両方を入力していて、退勤が出勤より後でない。 */
    CLOCK_OUT_NOT_AFTER_CLOCK_IN(WorkRecordField.CLOCK_OUT),

    /** 休憩時間が0以上の数値 (小数第2位まで) でない。 */
    BREAK_HOURS_INVALID_FORMAT(WorkRecordField.BREAK_HOURS),

    /** 休憩時間が [WorkRecordInput.MAX_HOURS] を超えている。 */
    BREAK_HOURS_TOO_LARGE(WorkRecordField.BREAK_HOURS),

    /** 稼働時間の計算結果が負になる (空欄の定時と組み合わせた結果、休憩が長すぎる等)。 */
    WORKING_HOURS_NEGATIVE(WorkRecordField.WORKING_HOURS),
}

/** 日次入力の入力欄 (稼働時間は入力欄ではなく、計算結果の表示)。 */
enum class WorkRecordField {
    CLOCK_IN,
    CLOCK_OUT,
    BREAK_HOURS,
    WORKING_HOURS,
}

/** 案件ごとの工数の入力が満たしていない条件。 */
enum class AllocationHoursViolation {
    /** 0以上の数値 (小数第2位まで) でない。 */
    INVALID_FORMAT,

    /** [WorkRecordInput.MAX_HOURS] を超えている。 */
    TOO_LARGE,
}

/**
 * 日次入力が条件を満たしていないため保存できないことを示す例外。
 * 文言は持たず、presentation 層が strings.xml の文言に変換して表示する。
 *
 * @property violations 案件ごとの工数以外の違反
 * @property allocationViolations 案件ごとの工数の違反 (案件ID → 違反)
 */
class InvalidWorkRecordException(
    val violations: Set<WorkRecordViolation>,
    val allocationViolations: Map<String, AllocationHoursViolation>,
) : Exception()
