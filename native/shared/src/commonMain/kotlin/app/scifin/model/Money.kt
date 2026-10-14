package app.scifin.model

import kotlin.math.roundToLong

/**
 * Money is a `Long` in minor units (cents) everywhere in the engine (§12): integer arithmetic
 * throughout, formatted only at the display layer.
 *
 * A typealias rather than a value class: Room's KMP codegen does not store value classes, and
 * the engine's arithmetic (`+`, `-`, comparisons, `sumOf`) reads best on the primitive.
 */
typealias Money = Long

/** 100 minor units per major unit for every currency §12 offers. */
const val MINOR_UNITS_PER_MAJOR: Long = 100

/** `12.34` -> `1234`. Nearest minor unit, ties toward +∞; Double input, so `1.005` reads as `1.00`. */
fun moneyOf(major: Double): Money = (major * MINOR_UNITS_PER_MAJOR).roundToLong()

/** §6.4's "round up to the minor unit": the smallest Money that, times [n], covers [this]. */
fun Money.ceilDiv(n: Long): Money = if (n <= 0) this else (this + n - 1) / n
