package com.omnimemoria.domain.intelligence

import kotlin.math.sqrt

object OcrImageSizing {
    const val MAX_PIXELS = 4_000_000
    const val MAX_EDGE = 3200
    fun target(width: Int, height: Int, enlargeSmallText: Boolean = false): Pair<Int, Int> {
        require(width > 0 && height > 0)
        val desired = if (enlargeSmallText) maxOf(1.0, minOf(2.5, 1400.0 / width)) else 1.0
        val scale = minOf(desired, MAX_EDGE.toDouble() / maxOf(width, height),
            sqrt(MAX_PIXELS.toDouble() / width.toDouble() / height.toDouble()))
        return maxOf(1, (width * scale).toInt()) to maxOf(1, (height * scale).toInt())
    }
}
