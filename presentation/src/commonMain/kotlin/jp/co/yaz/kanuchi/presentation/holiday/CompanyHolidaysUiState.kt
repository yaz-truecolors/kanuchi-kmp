package jp.co.yaz.kanuchi.presentation.holiday

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayInput
import jp.co.yaz.kanuchi.domain.calendar.CompanyHolidayViolation
import jp.co.yaz.kanuchi.domain.calendar.JapaneseHolidays
import kotlinx.datetime.LocalDate

/**
 * 会社の休業日の追加に失敗した理由 (入力の検証以外)。Composable 側で strings.xml の文言に変換して表示する。
 */
enum class CompanyHolidayAddError {
    /** 期間内に既に休業日として登録されている日がある。 */
    DUPLICATE,

    /** 通信エラー・権限が無い等、それ以外の理由。 */
    SAVE_FAILED,
}

/** 日本の祝日 (自動判定。参考として一覧に表示する)。 */
data class NationalHolidayItem(
    val date: LocalDate,
    val name: String,
)

/**
 * 休業日管理画面のUI状態。
 */
data class CompanyHolidaysUiState(
    /** 表示している年。 */
    val year: Int,
    /** [year] 年の会社の休業日 (日付の順)。 */
    val companyHolidays: List<CompanyHoliday> = emptyList(),
    /** [year] 年の日本の祝日 (日付の順)。自動判定のため読み込み不要。 */
    val nationalHolidays: List<NationalHolidayItem> = emptyList(),
    val isLoading: Boolean = false,
    val loadFailed: Boolean = false,
    /** 追加する休業日の入力内容。 */
    val input: CompanyHolidayInput = EMPTY_INPUT,
    /** 追加しようとした入力が満たしていない条件。 */
    val inputViolations: Set<CompanyHolidayViolation> = emptySet(),
    val isAdding: Boolean = false,
    val addError: CompanyHolidayAddError? = null,
    /** 直前の追加で追加した休業日の日数 (追加の完了を表示する)。入力を変更すると消す。 */
    val addedDayCount: Int? = null,
    /** 削除の確認ダイアログを表示している休業日。 */
    val deleteTarget: CompanyHoliday? = null,
    /** 削除中の休業日の日付。 */
    val deletingDate: LocalDate? = null,
    /** 削除に失敗した休業日。失敗後に一覧を読み込み直して行が消えても、失敗したことが分かるよう一覧の上に表示し続ける。 */
    val deleteFailedHoliday: CompanyHoliday? = null,
) {
    /** いずれかの保存 (追加・削除) の処理中か。処理中は他の保存と年の切り替えを受け付けない。 */
    val isSaving: Boolean get() = isAdding || deletingDate != null

    /**
     * 休業日を追加・削除できるか。一覧の取得に成功するまでは変更させない
     * (取得中に追加すると、後から返った一覧で追加した休業日が画面から消えるため)。
     */
    val canEdit: Boolean get() = !isLoading && !loadFailed && !isSaving

    val canGoToPreviousYear: Boolean get() = !isSaving && year > JapaneseHolidays.SUPPORTED_YEARS.first
    val canGoToNextYear: Boolean get() = !isSaving && year < JapaneseHolidays.SUPPORTED_YEARS.last

    companion object {
        val EMPTY_INPUT = CompanyHolidayInput(startDate = "", endDate = "", name = "")
    }
}
