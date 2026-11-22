// Vendored from AntNexo/antnexo-android v1.1.0 (4e1ed06e7e3d5977e2be854a3753d95750e50b2d) by scripts/vendor.sh. Do not edit.
package com.antnexo.core

import kotlin.math.roundToInt

data class Size(val width: Int, val height: Int)

/** The size that fits within max × max, keeping the aspect ratio; never scaled up. */
fun fittedSize(width: Int, height: Int, max: Int = 1600): Size {
    val longest = maxOf(width, height)
    if (longest <= max) return Size(width, height)
    val scale = max.toDouble() / longest
    // a long thin image keeps at least one pixel across (a 0 side can't be scaled to)
    fun side(v: Int) = if (v > 0) (v * scale).roundToInt().coerceAtLeast(1) else 0
    return Size(side(width), side(height))
}
