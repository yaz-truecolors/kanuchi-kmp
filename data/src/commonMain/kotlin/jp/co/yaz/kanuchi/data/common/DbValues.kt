package jp.co.yaz.kanuchi.data.common

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.TimeOfDay
import kotlin.math.roundToInt

/**
 * DB の `time` 型の値 (`HH:mm:ss`、秒の小数部が付くこともある) を [TimeOfDay] に変換する。
 * アプリは分単位でしか扱わないため、秒以下は切り捨てる (アプリから保存した値に秒は付かない)。
 * 保存するときは [TimeOfDay.toString] (`HH:mm` 形式) をそのまま送る。
 */
internal fun parseDbTime(value: String): TimeOfDay {
    val parts = value.split(":")
    require(parts.size >= 2) { "unexpected time value: $value" }
    return TimeOfDay.of(hour = parts[0].toInt(), minute = parts[1].toInt())
}

/**
 * DB の `numeric(p, 2)` 型の時間数 (JSON の数値) を [Hours] に変換する。
 * 値は 0.01 単位なので、2進小数の誤差は四捨五入で吸収する。
 */
internal fun hoursOf(value: Double): Hours = Hours.ofHundredths((value * HUNDRED).roundToInt())

/** [Hours] を DB の `numeric(p, 2)` 型に送る値 (JSON の数値) に変換する。 */
internal fun Hours.toDbValue(): Double = hundredths / HUNDRED.toDouble()

private const val HUNDRED = 100
