package jp.co.yaz.kanuchi.domain.work

import jp.co.yaz.kanuchi.domain.calendar.DayKind
import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import jp.co.yaz.kanuchi.domain.shift.ShiftSettings
import kotlinx.datetime.LocalDate

/**
 * 利用者が日次入力画面で入力した1日分の記録 (入力欄の文字列そのまま)。
 * [toWorkRecord] で検証して [WorkRecord] に変換する。入力中の再計算には [preview] を使う。
 *
 * - 時刻 ([clockIn] / [clockOut]): `HH:mm` 形式 ([TimeOfDay.parse])。空欄なら定時
 * - 時間数 ([breakHours] / [allocations] の値): 0以上の数値、小数第2位まで ([Hours.parse])。休憩の空欄は定時
 * - 案件ごとの工数は、空欄または 0 なら配分しない (保存しない)
 * - 印 ([flag]) を付けた日は稼働 0 のため、出勤・退勤・休憩・案件ごとの工数は検証も保存もしない
 *   (入力欄の内容は残しておき、印を外せば元に戻せる)。備考は保存する
 *
 * @property allocations 案件ごとの工数 (案件ID → 入力した文字列)
 */
data class WorkRecordInput(
    val clockIn: String = "",
    val clockOut: String = "",
    val breakHours: String = "",
    val flag: DayFlag? = null,
    val note: String = "",
    val allocations: Map<String, String> = emptyMap(),
) {
    /** 出勤・退勤・休憩・案件ごとの工数を入力できるか (印を付けた日は入力しない)。 */
    val acceptsWorkInput: Boolean get() = flag == null

    /**
     * 入力を検証して [date] の [WorkRecord] に変換する。条件を満たさない場合は、満たしていない条件をすべて持つ
     * [InvalidWorkRecordException] で失敗する。
     *
     * @param dayKind その日の種類 (稼働時間の計算に使う)
     * @param shiftSettings 本人の勤務時間設定 (空欄の定時の値として使う)
     */
    fun toWorkRecord(
        date: LocalDate,
        dayKind: DayKind,
        shiftSettings: ShiftSettings,
    ): Result<WorkRecord> {
        val parsed = parse()
        val violations = parsed.violations.toMutableSet()
        val record = parsed.toRecord(date)
        // 出勤・退勤・休憩に他の違反がある場合は、そちらを直せば解消することが多いため重ねて表示しない
        if (record != null && violations.isEmpty() && WorkingHoursCalculator.calculate(dayKind, record, shiftSettings).isNegative) {
            violations += WorkRecordViolation.WORKING_HOURS_NEGATIVE
        }
        if (violations.isNotEmpty() || parsed.allocationViolations.isNotEmpty() || record == null) {
            return Result.failure(InvalidWorkRecordException(violations, parsed.allocationViolations))
        }
        return Result.success(record)
    }

    /**
     * 入力中の再計算 (稼働時間・配分の合計・過不足)。形式が正しくない欄があって計算できない値は null にする。
     *
     * @param dayKind その日の種類
     * @param shiftSettings 本人の勤務時間設定 (空欄の定時の値として使う)
     */
    fun preview(
        dayKind: DayKind,
        shiftSettings: ShiftSettings,
    ): WorkRecordPreview {
        if (flag != null) return WorkRecordPreview(DailyWorkingHours(Hours.ZERO, isNegative = false), Hours.ZERO)
        val parsed = parse()
        val workingHours =
            if (parsed.hasValidTimes) {
                // 日付は計算に使わないため、任意の日付でよい
                val record = WorkRecord(PREVIEW_DATE, parsed.clockIn, parsed.clockOut, parsed.breakHours)
                WorkingHoursCalculator.calculate(dayKind, record, shiftSettings)
            } else {
                null
            }
        val allocatedHours = if (parsed.allocationViolations.isEmpty()) parsed.allocations.values.fold(Hours.ZERO, Hours::plus) else null
        return WorkRecordPreview(workingHours, allocatedHours)
    }

    private fun parse(): ParsedInput {
        if (flag != null) {
            return ParsedInput(null, null, null, emptyMap(), emptySet(), emptyMap(), hasValidTimes = true)
        }
        val clockInValue = clockIn.parseOptionalValue(TimeOfDay::parse)
        val clockOutValue = clockOut.parseOptionalValue(TimeOfDay::parse)
        val breakHoursValue = breakHours.parseOptionalValue(Hours::parse)
        val violations =
            buildSet {
                if (clockInValue == null) add(WorkRecordViolation.CLOCK_IN_INVALID_FORMAT)
                if (clockOutValue == null) add(WorkRecordViolation.CLOCK_OUT_INVALID_FORMAT)
                val start = clockInValue?.value
                val end = clockOutValue?.value
                if (start != null && end != null && end <= start) add(WorkRecordViolation.CLOCK_OUT_NOT_AFTER_CLOCK_IN)
                if (breakHoursValue == null) {
                    add(WorkRecordViolation.BREAK_HOURS_INVALID_FORMAT)
                } else if (breakHoursValue.value != null && breakHoursValue.value > MAX_HOURS) {
                    add(WorkRecordViolation.BREAK_HOURS_TOO_LARGE)
                }
            }
        val allocationViolations = mutableMapOf<String, AllocationHoursViolation>()
        val allocationValues = mutableMapOf<String, Hours>()
        parseAllocations(allocationValues, allocationViolations)
        return ParsedInput(
            clockIn = clockInValue?.value,
            clockOut = clockOutValue?.value,
            breakHours = breakHoursValue?.value,
            allocations = allocationValues,
            violations = violations,
            allocationViolations = allocationViolations,
            hasValidTimes = clockInValue != null && clockOutValue != null && breakHoursValue != null,
        )
    }

    /** 案件ごとの工数を解釈する。空欄・0 の案件は [values] に入れない。 */
    private fun parseAllocations(
        values: MutableMap<String, Hours>,
        violations: MutableMap<String, AllocationHoursViolation>,
    ) {
        for ((projectId, text) in allocations) {
            if (text.isBlank()) continue
            val hours = Hours.parse(text)
            when {
                hours == null -> violations[projectId] = AllocationHoursViolation.INVALID_FORMAT
                hours > MAX_HOURS -> violations[projectId] = AllocationHoursViolation.TOO_LARGE
                hours > Hours.ZERO -> values[projectId] = hours
            }
        }
    }

    private fun ParsedInput.toRecord(date: LocalDate): WorkRecord? =
        if (hasValidTimes) {
            WorkRecord(
                date = date,
                clockIn = clockIn,
                clockOut = clockOut,
                breakHours = breakHours,
                flag = flag,
                note = note.trim().ifEmpty { null },
                allocations = allocations,
            )
        } else {
            null
        }

    /** 解釈した入力。形式が正しくない欄の値は null。 */
    private class ParsedInput(
        val clockIn: TimeOfDay?,
        val clockOut: TimeOfDay?,
        val breakHours: Hours?,
        val allocations: Map<String, Hours>,
        val violations: Set<WorkRecordViolation>,
        val allocationViolations: Map<String, AllocationHoursViolation>,
        /** 出勤・退勤・休憩の形式がすべて正しい (空欄を含む) か。 */
        val hasValidTimes: Boolean,
    )

    /** 空欄可の入力欄の値。空欄なら value が null、形式が正しくなければ (このクラス自体が) null。 */
    private class OptionalValue<T>(
        val value: T?,
    )

    private fun <T> String.parseOptionalValue(parse: (String) -> T?): OptionalValue<T>? =
        if (isBlank()) OptionalValue(null) else parse(this)?.let { OptionalValue(it) }

    companion object {
        /** 休憩時間・案件ごとの工数の上限。DB の列の型 `numeric(4, 2)` に収まる最大値。 */
        val MAX_HOURS: Hours = Hours.ofHundredths(9_999)

        private val PREVIEW_DATE = LocalDate(2000, 1, 1)

        /** 保存済みの記録 (無ければ空欄) を入力欄に表示する文字列に変換する。 */
        fun from(record: WorkRecord?): WorkRecordInput =
            WorkRecordInput(
                clockIn = record?.clockIn?.toString().orEmpty(),
                clockOut = record?.clockOut?.toString().orEmpty(),
                breakHours = record?.breakHours?.toString().orEmpty(),
                flag = record?.flag,
                note = record?.note.orEmpty(),
                allocations = record?.allocations.orEmpty().mapValues { (_, hours) -> hours.toString() },
            )
    }
}

/**
 * 日次入力の入力中の再計算結果。
 *
 * @property workingHours 稼働時間。出勤・退勤・休憩の形式が正しくなく計算できない場合は null
 * @property allocatedHours 案件への配分の合計。工数の形式が正しくない案件があり計算できない場合は null
 */
data class WorkRecordPreview(
    val workingHours: DailyWorkingHours?,
    val allocatedHours: Hours?,
) {
    /** 過不足 (稼働時間 − 配分の合計)。どちらかが計算できない場合は null。 */
    val balance: SignedHours?
        get() =
            if (workingHours != null && allocatedHours != null) {
                AllocationBalancer.balanceOf(workingHours.hours, allocatedHours)
            } else {
                null
            }
}
