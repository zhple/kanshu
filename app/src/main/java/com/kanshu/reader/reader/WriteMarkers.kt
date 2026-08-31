package com.kanshu.reader.reader

import java.util.UUID

/**
 * 写作草稿图片标记：
 * - [[IMG:path]]
 * - [[IMG:path|w=0.75]]  w 为相对正文宽度，0.3~1.0
 *
 * 编辑器采用 Notion 式「块」模型（文字块 / 图片块），便于拖拽排序与分页。
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
    abstract val id: String

    data class Paragraph(
        val text: String,
        override val id: String = UUID.randomUUID().toString()
    ) : WriteBlock()

    data class Image(
        val path: String,
        val widthPercent: Float = 1f,
        override val id: String = UUID.randomUUID().toString()
    ) : WriteBlock()
}

/** 编辑器翻页用：按章节 / 篇幅切开的一页。 */
data class WriteEditPage(
    val title: String,
    val startIndex: Int,
    val endExclusive: Int
) {
    val size: Int get() = (endExclusive - startIndex).coerceAtLeast(0)
}

object WriteBlocks {
    private val chapterLineRegex = Regex(
        """^第[\d零一二三四五六七八九十百千两]+章|^序章|^终章|^楔子|^尾声|^番外|^Chapter\s+\d+"""
    )

    /** 单页大约容纳的文字量；超出则软分页，避免超长一屏。 */
    private const val PAGE_CHAR_BUDGET = 1600
    private const val IMAGE_WEIGHT = 500

    fun newId(): String = UUID.randomUUID().toString()

    fun parse(content: String): List<WriteBlock> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return listOf(WriteBlock.Paragraph(""))
        val result = mutableListOf<WriteBlock>()
        var last = 0
        for (match in WriteMarkers.imageRegex.findAll(normalized)) {
            if (match.range.first > last) {
                val text = normalized.substring(last, match.range.first).trim('\n')
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

    fun blockWeight(block: WriteBlock): Int = when (block) {
        is WriteBlock.Paragraph -> block.text.length.coerceAtLeast(40)
        is WriteBlock.Image -> IMAGE_WEIGHT
    }

    fun isChapterStart(block: WriteBlock): Boolean {
        if (block !is WriteBlock.Paragraph) return false
        val first = block.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: return false
        return chapterLineRegex.containsMatchIn(first)
    }

    fun chapterTitleOf(block: WriteBlock): String {
        if (block !is WriteBlock.Paragraph) return "正文"
        val first = block.text.lineSequence().map { it.trim() }.firstOrNull { it.isNotEmpty() }
            ?: return "正文"
        return first.take(24)
    }

    data class OutlineItem(
        val title: String,
        val blockIndex: Int,
        val pageIndex: Int,
        val charCount: Int
    )

    /** 纯文字字数（不含空白），按中文写作常见「字数」统计。 */
    fun charCount(blocks: List<WriteBlock>): Int {
        return blocks.sumOf { block ->
            when (block) {
                is WriteBlock.Paragraph -> block.text.count { !it.isWhitespace() }
                is WriteBlock.Image -> 0
            }
        }
    }

    fun paragraphCount(blocks: List<WriteBlock>): Int {
        return blocks.count { it is WriteBlock.Paragraph && it.text.isNotBlank() }
    }

    fun imageCount(blocks: List<WriteBlock>): Int {
        return blocks.count { it is WriteBlock.Image }
    }

    fun outline(blocks: List<WriteBlock>): List<OutlineItem> {
        val pages = buildPages(blocks)
        val items = mutableListOf<OutlineItem>()
        blocks.forEachIndexed { index, block ->
            if (!isChapterStart(block)) return@forEachIndexed
            val pageIndex = pages.indexOfFirst { index in it.startIndex until it.endExclusive }
                .coerceAtLeast(0)
            val page = pages.getOrNull(pageIndex)
            val chars = if (page != null) {
                charCount(blocks.subList(page.startIndex, page.endExclusive))
            } else {
                0
            }
            items += OutlineItem(
                title = chapterTitleOf(block),
                blockIndex = index,
                pageIndex = pageIndex,
                charCount = chars
            )
        }
        return items
    }

    /**
     * 按「章节标题」硬分页，章节内再按篇幅软分页。
     * 参考 Notion 分块编辑：不把整篇塞进一条超长滚动条。
     */
    fun buildPages(blocks: List<WriteBlock>): List<WriteEditPage> {
        if (blocks.isEmpty()) return listOf(WriteEditPage("正文", 0, 0))
        val pages = mutableListOf<WriteEditPage>()
        var start = 0
        var title = "开头"
        var weight = 0
        var pageOrdinal = 1

        fun flush(end: Int) {
            if (end <= start) return
            pages += WriteEditPage(title, start, end)
            start = end
            weight = 0
            pageOrdinal++
        }

        blocks.forEachIndexed { index, block ->
            val chapter = isChapterStart(block)
            if (chapter && index > start) {
                flush(index)
                title = chapterTitleOf(block)
            } else if (chapter && index == start) {
                title = chapterTitleOf(block)
            }
            val w = blockWeight(block)
            if (weight > 0 && weight + w > PAGE_CHAR_BUDGET) {
                flush(index)
                title = if (chapter) chapterTitleOf(block) else "续·$pageOrdinal"
            }
            weight += w
        }
        flush(blocks.size)
        if (pages.isEmpty()) {
            pages += WriteEditPage("正文", 0, blocks.size)
        }
        return pages
    }
}
