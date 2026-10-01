package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.LocalDate
import kotlin.time.Clock

/**
 * 日本時間の今日の日付を返すユースケース。画面の初期表示の月を決めるのに使う。
 * テストで「今日」を固定できるよう、[Clock] を注入する (Koin では `Clock.System`)。
 */
class GetTodayUseCase(
    private val clock: Clock,
) {
    operator fun invoke(): LocalDate = clock.todayInJapan()
}
