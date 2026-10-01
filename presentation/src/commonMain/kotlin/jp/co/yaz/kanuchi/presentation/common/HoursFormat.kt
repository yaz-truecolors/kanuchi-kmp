package jp.co.yaz.kanuchi.presentation.common

import jp.co.yaz.kanuchi.domain.common.Hours
import jp.co.yaz.kanuchi.domain.common.SignedHours
import jp.co.yaz.kanuchi.domain.work.AllocationRatio

/*
 * 時間数・割合の表示用の書式 (数値の部分だけ。単位 (時間・%) は呼び出し側が strings.xml の文言で付ける)。
 * 表で桁が揃うよう、時間数は小数第2位まで、割合は小数第1位まで常に表示する。
 */

/** 時間数の表示 (例: `7.50`、`0.00`)。 */
fun formatHours(hours: Hours): String = formatHundredths(hours.hundredths)

/** 符号付きの時間数の表示。正なら `+`、負なら `-` を付ける (例: `+0.50`、`-1.25`、`0.00`)。 */
fun formatSignedHours(hours: SignedHours): String {
    val sign =
        when {
            hours.isPositive -> "+"
            hours.isNegative -> "-"
            else -> ""
        }
    return sign + formatHundredths(hours.absolute().hundredths)
}

/** 割合の表示 (例: `33.3`、`100.0`、`-2.5`)。 */
fun formatRatio(ratio: AllocationRatio): String = ratio.toString()

private fun formatHundredths(hundredths: Int): String = "${hundredths / HUNDRED}.${(hundredths % HUNDRED).toString().padStart(2, '0')}"

private const val HUNDRED = 100
