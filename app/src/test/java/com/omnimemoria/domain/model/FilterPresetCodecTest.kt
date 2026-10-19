package com.omnimemoria.domain.model

import org.junit.Assert.*
import org.junit.Test

class FilterPresetCodecTest {
    @Test fun roundTripsEveryFilterIncludingFalseAndEmptySelections() {
        val filter = FilterConfig(
            mediaTypes = setOf(MediaType.RAW, MediaType.GIF),
            mimeFormats = setOf("image/svg+xml", "image/x-adobe-dng", "image/اختبار&x=y"),
            minSizeBytes = 123, maxSizeBytes = Long.MAX_VALUE,
            dateRange = 0L..Long.MAX_VALUE, minResolutionMp = 12.5f,
            hasText = false, hasFaces = true, hasPhoneNumber = false, isFavorite = true
        )
        assertEquals(filter, FilterPresetCodec.decode(FilterPresetCodec.encode(filter)))
        val empty = FilterConfig(mediaTypes = emptySet(), isFavorite = false)
        assertEquals(empty, FilterPresetCodec.decode(FilterPresetCodec.encode(empty)))
    }

    @Test fun defaultAndLegacyPresetsLoadWithoutDataLoss() {
        assertEquals(FilterConfig(), FilterPresetCodec.decode(""))
        assertEquals(FilterConfig(), FilterPresetCodec.decode(FilterPresetCodec.encode(FilterConfig())))
    }

    @Test fun corruptAndFutureVersionsSafelyFallBack() {
        for (bad in listOf("garbage", "v=99", "v=1&types=UNKNOWN", "v=1&formats=%ZZ", "v=1&minSize=-1", "v=1&mp=NaN", "v=1&from=20&to=10")) {
            assertEquals(bad, FilterConfig(), FilterPresetCodec.decode(bad))
        }
    }

    @Test fun encodingIsIndependentOfSetInsertionOrder() {
        val a = FilterConfig(mediaTypes = linkedSetOf(MediaType.RAW, MediaType.IMAGE), mimeFormats = linkedSetOf("image/png", "image/jpeg"))
        val b = FilterConfig(mediaTypes = linkedSetOf(MediaType.IMAGE, MediaType.RAW), mimeFormats = linkedSetOf("image/jpeg", "image/png"))
        assertEquals(FilterPresetCodec.encode(a), FilterPresetCodec.encode(b))
    }
}
