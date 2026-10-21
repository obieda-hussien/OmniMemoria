package com.omnimemoria.domain.intelligence

import kotlin.math.cos

/** 64-bit DCT pHash. The DC term is excluded from the median and comparison. */
object PerceptualHash {
    private val basis = Array(8) { f -> DoubleArray(32) { x -> cos((2 * x + 1) * f * Math.PI / 64) } }
    fun compute(gray: DoubleArray): String {
        require(gray.size == 1024 && gray.all { it.isFinite() })
        val mean = gray.average()
        val dct = DoubleArray(64)
        for (u in 0..7) for (v in 0..7) {
            var sum = 0.0
            for (y in 0..31) for (x in 0..31) sum += (gray[y * 32 + x] - mean) * basis[u][x] * basis[v][y]
            dct[u * 8 + v] = sum
        }
        val median = dct.drop(1).sorted()[31]
        var bits = 0L
        for (i in 1..63) if (dct[i] > median) bits = bits or (1L shl i)
        return java.lang.Long.toHexString(bits).padStart(16, '0')
    }
}
