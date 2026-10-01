package jp.co.yaz.kanuchi.data.calendar

import jp.co.yaz.kanuchi.domain.calendar.CompanyHoliday
import jp.co.yaz.kanuchi.domain.calendar.DuplicateCompanyHolidayException
import jp.co.yaz.kanuchi.domain.common.GenericDataFailureException
import kotlinx.datetime.LocalDate
import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame

class CompanyHolidayDtoTest {
    @Test
    fun `postgrest response is mapped to the domain model`() {
        val json = """[{"holiday_date":"2026-12-29","name":"年末休業"}]"""

        val holidays = Json.decodeFromString<List<CompanyHolidayDto>>(json).map { it.toDomain() }

        assertEquals(listOf(CompanyHoliday(LocalDate(2026, 12, 29), "年末休業")), holidays)
    }

    @Test
    fun `request body has only the date and the name`() {
        val json = Json.encodeToString(CompanyHolidayDto.serializer(), CompanyHoliday(LocalDate(2027, 1, 4), "創立記念日").toDto())

        assertEquals("""{"holiday_date":"2027-01-04","name":"創立記念日"}""", json)
    }

    @Test
    fun `unique violation is converted to a duplicate holiday failure`() {
        val cause = RuntimeException("duplicate key value violates unique constraint")

        val failure = companyHolidayAddFailure(GenericDataFailureException(cause), postgresErrorCode = "23505")

        assertIs<DuplicateCompanyHolidayException>(failure)
        assertSame(cause, failure.cause)
    }

    @Test
    fun `other failures are kept as is`() {
        val original = GenericDataFailureException(RuntimeException("permission denied"))

        assertSame(original, companyHolidayAddFailure(original, postgresErrorCode = "42501"))
        assertSame(original, companyHolidayAddFailure(original, postgresErrorCode = null))
    }
}
