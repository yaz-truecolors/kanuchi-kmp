package jp.co.yaz.kanuchi.data.calendar

import jp.co.yaz.kanuchi.data.project.POSTGRES_UNIQUE_VIOLATION
import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.DuplicateCompanyHolidayException
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `company_holidays` テーブルの1行 (PostgREST のリクエスト・レスポンス)。
 * 追加するときもこの列だけを送る (`created_by`・`created_at` は DB の既定値。列単位の権限で送れない)。
 */
@Serializable
internal data class CompanyHolidayDto(
    @SerialName("holiday_date") val holidayDate: String,
    val name: String,
) {
    companion object {
        val COLUMNS = listOf("holiday_date", "name")
    }
}

internal fun CompanyHolidayDto.toDomain(): CompanyHoliday = CompanyHoliday(date = LocalDate.parse(holidayDate), name = name)

internal fun CompanyHoliday.toDto(): CompanyHolidayDto = CompanyHolidayDto(holidayDate = date.toString(), name = name)

/**
 * 会社の休業日を追加したときの失敗を変換する。主キーは `holiday_date` なので、unique 制約違反
 * ([postgresErrorCode] が [POSTGRES_UNIQUE_VIOLATION]) は「同じ日付の休業日がある」とみなす。
 */
internal fun companyHolidayAddFailure(
    failure: GenericDataFailureException,
    postgresErrorCode: String?,
): Exception =
    if (postgresErrorCode == POSTGRES_UNIQUE_VIOLATION) {
        DuplicateCompanyHolidayException(failure.cause)
    } else {
        failure
    }
