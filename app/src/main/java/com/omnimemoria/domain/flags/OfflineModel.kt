package com.omnimemoria.domain.flags

/** Stable IDs preserve previously saved model preferences. Artifact sizes are
 * deliberately unspecified until Phase 4 selects a verified runtime/quantization. */
enum class OfflineModel(val id: String, val title: String, val phase: String) {
    TESSERACT_ARABIC("tesseract_ara", "Tesseract Arabic", "Phase 4A"),
    EMBEDDING_GEMMA("mediapipe_embedder", "EmbeddingGemma", "Phase 4B");

    companion object {
        fun fromId(id: String): OfflineModel? = entries.firstOrNull { it.id == id }
    }
}
