package com.kanshu.reader.reader

/**
 * 写作草稿图片标记：
 * - [[IMG:path]]
 * - [[IMG:path|w=0.75]]  w 为相对正文宽度，0.3~1.0
 */
object WriteMarkers {
    val imageRegex = Regex("""\[\[IMG:([^|\]]+?)(?:\|w=([0-9]*\.?[0-9]+))?]]""")

    fun imageMarker(path: String, widthPercent: Float = 1f): String {
        val w = widthPercent.coerceIn(0.3f, 1f)
        return if (w >= 0.995f) {
            "[[IMG:$path]]"
        } else {
            "[[IMG:$path|w=${"%.2f".format(w)}]]"
        }
    }

    fun stripImagesForPlainText(content: String): String {
        return imageRegex.replace(content) { match ->
            val name = match.groupValues[1].substringAfterLast('/').substringAfterLast('\\')
            "【图片：$name】"
        }
    }
}

sealed class WriteBlock {
    data class Paragraph(val text: String) : WriteBlock()
    data class Image(val path: String, val widthPercent: Float = 1f) : WriteBlock()
}

object WriteBlocks {
    fun parse(content: String): List<WriteBlock> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return listOf(WriteBlock.Paragraph(""))
        val result = mutableListOf<WriteBlock>()
        var last = 0
        for (match in WriteMarkers.imageRegex.findAll(normalized)) {
            if (match.range.first > last) {
                val text = normalized.substring(last, match.range.first)
                    .trim('\n')
                if (text.isNotEmpty() || result.isEmpty()) {
                    result += WriteBlock.Paragraph(text)
                }
            }
            val path = match.groupValues[1].trim()
            val width = match.groupValues.getOrNull(2)
                ?.toFloatOrNull()
                ?.coerceIn(0.3f, 1f)
                ?: 1f
            if (path.isNotEmpty()) {
                result += WriteBlock.Image(path, width)
            }
            last = match.range.last + 1
        }
        if (last < normalized.length) {
            val text = normalized.substring(last).trimStart('\n')
            result += WriteBlock.Paragraph(text)
        }
        if (result.isEmpty()) result += WriteBlock.Paragraph("")
        // 末尾保证有一段可继续输入的文字块
        if (result.last() !is WriteBlock.Paragraph) {
            result += WriteBlock.Paragraph("")
        }
        return result
    }

    fun serialize(blocks: List<WriteBlock>): String {
        val parts = mutableListOf<String>()
        for (block in blocks) {
            when (block) {
                is WriteBlock.Paragraph -> {
                    val t = block.text
                    if (t.isNotEmpty() || parts.isEmpty()) parts += t
                }
                is WriteBlock.Image -> {
                    parts += WriteMarkers.imageMarker(block.path, block.widthPercent)
                }
            }
        }
        return parts.joinToString("\n\n")
            .replace(Regex("\n{3,}"), "\n\n")
            .trim()
    }

    fun contentLooksEmpty(blocks: List<WriteBlock>): Boolean {
        return blocks.none {
            when (it) {
                is WriteBlock.Paragraph -> it.text.isNotBlank()
                is WriteBlock.Image -> true
            }
        }
    }
}
