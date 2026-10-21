package com.omnimemoria.data.intelligence

import com.omnimemoria.domain.intelligence.OcrLine
import org.json.JSONArray
import org.json.JSONObject

object OcrRegions {
    fun read(raw: String): List<OcrLine> = runCatching {
        if (raw.length > 1_000_000) return@runCatching emptyList()
        val lines = JSONObject(raw).optJSONArray("lines") ?: return@runCatching emptyList()
        (0 until minOf(lines.length(), 1500)).mapNotNull { index ->
            val box = lines.optJSONObject(index) ?: return@mapNotNull null
            OcrLine(box.optString("text"), box.optDouble("left").toFloat(), box.optDouble("top").toFloat(),
                box.optDouble("right").toFloat(), box.optDouble("bottom").toFloat(), box.optString("language", "latin"),
                box.optDouble("confidence", 100.0).toFloat()).takeIf(OcrLine::isValid)
        }
    }.getOrDefault(emptyList())

    fun write(width: Int, height: Int, lines: List<OcrLine>): String {
        val boxes = JSONArray()
        lines.filter(OcrLine::isValid).take(1500).forEach { line ->
            boxes.put(JSONObject().put("text", line.text).put("left", line.left).put("top", line.top)
                .put("right", line.right).put("bottom", line.bottom).put("language", line.language).put("confidence", line.confidence))
        }
        return JSONObject().put("width", width).put("height", height).put("lines", boxes).toString()
    }
}
