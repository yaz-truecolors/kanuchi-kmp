package jp.co.yaz.kanuchi.presentation.testing

import kotlin.time.Clock
import kotlin.time.Instant

/** 「今日」を固定するための [Clock]。 */
internal class FixedClock(
    private val instant: Instant,
) : Clock {
    override fun now(): Instant = instant
}
