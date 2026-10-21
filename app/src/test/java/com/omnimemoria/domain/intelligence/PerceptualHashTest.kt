package com.omnimemoria.domain.intelligence

import org.junit.Assert.*
import org.junit.Test

class PerceptualHashTest {
    @Test fun uniformImagesHaveStableFingerprint() {
        assertEquals("0000000000000000", PerceptualHash.compute(DoubleArray(1024) { 50.0 }))
        assertEquals(PerceptualHash.compute(DoubleArray(1024)), PerceptualHash.compute(DoubleArray(1024) { 200.0 }))
    }
    @Test fun smallBrightnessChangesDoNotChangeFingerprint() {
        val pixels = DoubleArray(1024) { ((it * 37) % 170).toDouble() }
        assertEquals(PerceptualHash.compute(pixels), PerceptualHash.compute(DoubleArray(1024) { pixels[it] + 20 }))
    }
    @Test fun requiresExactNormalizedSampleAndFiniteValues() {
        assertThrows(IllegalArgumentException::class.java) { PerceptualHash.compute(DoubleArray(100)) }
        assertThrows(IllegalArgumentException::class.java) { PerceptualHash.compute(DoubleArray(1024) { Double.NaN }) }
    }
}
