package com.cy.codex.status

import java.math.BigInteger

/**
 * Credit and cost formatting for the estimated-usage lines.
 *
 * Mirrors `codex-rs/tui/src/status/thread_usage.rs`: credits are compacted to one decimal at the
 * largest fitting unit, and USD is rounded to cents with two extra digits while the amount is under
 * a dollar. `BigInteger` because `micros * 10` overflows a `Long` for credit values near its range.
 */
internal fun formatCreditMicros(micros: Long): String {
    val value = BigInteger.valueOf(micros.coerceAtLeast(0L))
    val units = listOf(
        BigInteger.TEN.pow(18) to "T",
        BigInteger.TEN.pow(15) to "B",
        BigInteger.TEN.pow(12) to "M",
        BigInteger.TEN.pow(9) to "K",
    )
    val (unit, suffix) = units.firstOrNull { value >= it.first } ?: (BigInteger.TEN.pow(6) to "")
    val tenths = (value * BigInteger.TEN + unit / BigInteger.TWO) / unit
    val whole = tenths / BigInteger.TEN
    val fraction = tenths % BigInteger.TEN
    return if (fraction.signum() == 0) "$whole$suffix" else "$whole.$fraction$suffix"
}

/** `null` when the backend has no estimate (negative), never `~$0.00`. */
internal fun formatEstimatedUsdMicros(micros: Long?): String? {
    if (micros == null || micros < 0) return null
    if (micros < 100) return "~$0." + micros.toString().padStart(6, '0')
    if (micros < 10_000) {
        val tenThousandths = (micros + 50) / 100
        return "~$0." + tenThousandths.toString().padStart(4, '0')
    }
    val cents = (micros + 5_000) / 10_000
    return "~$" + (cents / 100) + "." + (cents % 100).toString().padStart(2, '0')
}
