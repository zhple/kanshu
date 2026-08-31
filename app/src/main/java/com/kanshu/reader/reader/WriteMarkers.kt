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

    /** 硬分页（写作时「下一页」插入）；序列化为 [[PAGE]]。 */
    data class PageBreak(
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

    /** 单页大约容纳的文字量；编辑时会按视口再收紧。 */
    const val PAGE_CHAR_BUDGET = 1600
    private const val IMAGE_WEIGHT = 500
    const val PAGE_BREAK_MARKER = "[[PAGE]]"

    fun newId(): String = UUID.randomUUID().toString()

    private fun isChapterHeadingLine(line: String): Boolean {
        val t = line.trim()
        return t.isNotEmpty() && t.length <= 48 && chapterLineRegex.containsMatchIn(t)
    }

    private fun pushParagraphChunks(result: MutableList<WriteBlock>, text: String) {
        val trimmed = text.trim('\n')
        if (trimmed.isEmpty() && result.isNotEmpty()) return
        val chunks = if (trimmed.isEmpty()) listOf("") else trimmed.split(Regex("\n\n+"))
        for (chunk in chunks) {
            val c = chunk.trim()
            if (c == PAGE_BREAK_MARKER) {
                result += WriteBlock.PageBreak()
                continue
            }
            if (c.isNotEmpty() || result.isEmpty()) {
                result += WriteBlock.Paragraph(c)
            }
        }
        if (result.isEmpty()) result += WriteBlock.Paragraph("")
    }

    /** 修复旧稿：段落中间出现的章节标题行拆成独立块 */
    fun expandBlocksByChapterHeadings(blocks: List<WriteBlock>): List<WriteBlock> {
        val out = mutableListOf<WriteBlock>()
        for (block in blocks) {
            if (block !is WriteBlock.Paragraph) {
                out += block
                continue
            }
            val lines = block.text.split('\n')
            val buf = StringBuilder()
            var pushed = false
            fun flushBuf() {
                val body = buf.toString().trim()
                buf.clear()
                if (body.isNotEmpty()) {
                    out += WriteBlock.Paragraph(body)
                    pushed = true
                }
            }
            for (line in lines) {
                if (isChapterHeadingLine(line)) {
                    flushBuf()
                    out += WriteBlock.Paragraph(line.trim())
                    pushed = true
                } else {
                    if (buf.isNotEmpty()) buf.append('\n')
                    buf.append(line)
                }
            }
            flushBuf()
            if (!pushed) out += block
        }
        return if (out.isNotEmpty()) out else blocks
    }

    fun parse(content: String): List<WriteBlock> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        if (normalized.isBlank()) return listOf(WriteBlock.Paragraph(""))
        val result = mutableListOf<WriteBlock>()
        var last = 0
        for (match in WriteMarkers.imageRegex.findAll(normalized)) {
            if (match.range.first > last) {
                pushParagraphChunks(result, normalized.substring(last, match.range.first))
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
            pushParagraphChunks(result, normalized.substring(last))
        }
        if (result.isEmpty()) result += WriteBlock.Paragraph("")
        if (result.last() !is WriteBlock.Paragraph) {
            result += WriteBlock.Paragraph("")
        }
        return expandBlocksByChapterHeadings(result)
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
                is WriteBlock.PageBreak -> {
                    parts += PAGE_BREAK_MARKER
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
                is WriteBlock.PageBreak -> false
            }
        }
    }

    fun blockWeight(block: WriteBlock): Int = when (block) {
        is WriteBlock.Paragraph -> block.text.count { !it.isWhitespace() }
        is WriteBlock.Image -> IMAGE_WEIGHT
        is WriteBlock.PageBreak -> 0
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
                is WriteBlock.PageBreak -> 0
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
     * 按「章节标题 / 分页标记」硬分页，章节内再按 [budget] 软分页。
     * budget 应接近当前视口可写字数，否则写满拆页后又会被合并回同一页。
     */
    fun buildPages(
        blocks: List<WriteBlock>,
        budget: Int = PAGE_CHAR_BUDGET
    ): List<WriteEditPage> {
        if (blocks.isEmpty()) return listOf(WriteEditPage("正文", 0, 0))
        val pageBudget = budget.coerceAtLeast(80)
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
            if (block is WriteBlock.PageBreak) {
                flush(index)
                start = index + 1
                weight = 0
                title = "续·$pageOrdinal"
                return@forEachIndexed
            }
            val chapter = isChapterStart(block)
            if (chapter && index > start) {
                flush(index)
                title = chapterTitleOf(block)
            } else if (chapter && index == start) {
                title = chapterTitleOf(block)
            }
            val w = blockWeight(block)
            if (weight > 0 && weight + w > pageBudget) {
                flush(index)
                title = if (chapter) chapterTitleOf(block) else "续·$pageOrdinal"
            }
            weight += w
        }
        flush(blocks.size)
        if (pages.isEmpty()) {
            // 全文只有分页符等：给一个空页壳，方便继续写
            val emptyAt = blocks.indexOfFirst { it is WriteBlock.Paragraph }.takeIf { it >= 0 }
                ?: blocks.size.coerceAtLeast(0)
            if (emptyAt < blocks.size) {
                pages += WriteEditPage("正文", emptyAt, emptyAt + 1)
            } else {
                pages += WriteEditPage("正文", 0, blocks.size)
            }
        }
        return pages
    }

    fun pagePlainText(blocks: List<WriteBlock>, page: WriteEditPage?): String {
        if (page == null) return ""
        return blocks.subList(page.startIndex, page.endExclusive)
            .filterIsInstance<WriteBlock.Paragraph>()
            .joinToString("\n\n") { it.text }
    }

    fun setPagePlainText(blocks: MutableList<WriteBlock>, page: WriteEditPage, text: String) {
        val segment = blocks.subList(page.startIndex, page.endExclusive).toList()
        val next = mutableListOf<WriteBlock>()
        var textApplied = false
        for (block in segment) {
            when (block) {
                is WriteBlock.Image -> next += block
                is WriteBlock.PageBreak -> next += block
                is WriteBlock.Paragraph -> if (!textApplied) {
                    next += block.copy(text = text)
                    textApplied = true
                }
            }
        }
        if (!textApplied) {
            next.add(0, WriteBlock.Paragraph(text))
        }
        blocks.subList(page.startIndex, page.endExclusive).clear()
        blocks.addAll(page.startIndex, next)
    }

    data class TextOverflow(val keep: String, val overflow: String)

    fun splitTextOverflow(text: String, budget: Int = PAGE_CHAR_BUDGET): TextOverflow {
        var count = 0
        for (i in text.indices) {
            if (!text[i].isWhitespace()) count++
            if (count > budget) {
                var splitAt = i
                val nl = text.lastIndexOf('\n', i)
                if (nl > i - 160) splitAt = nl + 1
                val keep = text.substring(0, splitAt).trimEnd()
                val overflow = text.substring(splitAt).trimStart()
                if (overflow.isEmpty()) return TextOverflow(text, "")
                return TextOverflow(keep, overflow)
            }
        }
        return TextOverflow(text, "")
    }
}
