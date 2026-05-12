package com.omnimemoria.data.local.objectbox

import io.objectbox.annotation.Entity
import io.objectbox.annotation.HnswIndex
import io.objectbox.annotation.Id

@Entity
data class PhotoEmbedding(
    @Id var id: Long = 0,
    var photoIntelligenceId: Long = 0,
    var embeddedText: String = "",
    @HnswIndex(dimensions = 512) var embedding: FloatArray = FloatArray(512)
)
