package com.omnimemoria.domain.model

/** A sort/filter pair is applied and restored as one value. */
data class MediaQueryConfig(val sort: SortConfig = SortConfig(), val filter: FilterConfig = FilterConfig())
