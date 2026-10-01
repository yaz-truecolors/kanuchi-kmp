package jp.co.yaz.kanuchi.domain.calendar

import kotlinx.datetime.FixedOffsetTimeZone
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.UtcOffset
import kotlinx.datetime.todayIn
import kotlin.time.Clock

/**
 * 日本時間 (Asia/Tokyo)。日本は夏時間が無く常に UTC+9 のため、固定オフセットで表す
 * (`TimeZone.of("Asia/Tokyo")` は実行環境のタイムゾーンデータベースに依存するため使わない)。
 */
val JAPAN_TIME_ZONE: TimeZone = FixedOffsetTimeZone(UtcOffset(hours = 9))

/** 日本時間の今日の日付。 */
fun Clock.todayInJapan(): LocalDate = todayIn(JAPAN_TIME_ZONE)
