package com.kanshu.reader.reader

/**
 * TXT 清洗、分章与阅读排版。
 */
object TxtReadingFormatter {
    private val bannerRegex = Regex("""[★☆＊*]{2,}.*?[★☆＊*]{2,}""")
    private val metaLineRegex = Regex(
        """^(?:台版|网译版|转自|图源|录入|扫图|翻译|校订|轻书架|文库|来源)\s*[:：]?"""
    )
    private val chapterLineRegex = Regex(
        """^(?:""" +
            // 第一卷 / 第一卷 第一章 / 第一卷 ① / 第一卷 书名 序章 ...
            """第[\d零一二三四五六七八九十百千两]+卷(?:\s+\S.+)?|""" +
            """第[\d零一二三四五六七八九十百千两]+章\S*|""" +
            """第[\d零一二三四五六七八九十百千两]+回\S*|""" +
            """序章\S*|终章\S*|楔子\S*|尾声\S*|番外\S*|""" +
            """Chapter\s+\d+\S*|CHAPTER\s+\d+\S*""" +
            """)$"""
    )

    fun cleanRawText(raw: String): String {
        var text = raw.replace("\r\n", "\n").replace('\r', '\n')
        text = bannerRegex.replace(text, "")
        text = text.replace('\u3000', ' ') // ideographic space -> normal for processing
        text = text.lines().joinToString("\n") { line ->
            val trimmed = line.trimEnd()
            when {
                trimmed.isBlank() -> ""
                metaLineRegex.containsMatchIn(trimmed.trim()) -> ""
                trimmed.trim().matches(Regex("""^-{4,}$|—{4,}""")) -> ""
                else -> trimmed.trimEnd()
            }
        }
        // collapse 3+ blank lines to 1 blank
        text = Regex("""\n{3,}""").replace(text, "\n\n")
        return text.trim()
    }

    fun guessTitle(text: String, fallback: String): String {
        val cleanFallback = fallback
            .removeSuffix(".txt").removeSuffix(".TXT")
            .removePrefix("txt-")
            .trim()
        val lines = text.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(12)
        for (line in lines) {
            val m = Regex("""^第[\d零一二三四五六七八九十百千两]+卷\s+(.+)$""").find(line)
            if (m != null) {
                var rest = m.groupValues[1].trim()
                rest = Regex("""\s+(?:序章|终章|楔子|第[\d零一二三四五六七八九十百千两]+章).*$""")
                    .replace(rest, "")
                    .trim()
                if (rest.isNotBlank() && !rest.matches(Regex("""^[①②③④⑤⑥⑦⑧⑨⑩\d]+$"""))) {
                    return rest.take(40)
                }
            }
        }
        return cleanFallback.ifBlank { "未命名 TXT" }
    }

    fun splitChapters(text: String): List<Chapter> {
        val lines = text.split('\n')
        val indices = mutableListOf<Int>()
        var offset = 0
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && chapterLineRegex.matches(trimmed)) {
                indices += offset
            }
            offset += line.length + 1
        }

        if (indices.isEmpty()) {
            // Fallback: split by blank-line blocks into ~chunks of ~3000 chars for long files
            val body = formatBody(text)
            if (body.length < 6000) {
                return listOf(Chapter("全文", body.ifBlank { "（空文件）" }))
            }
            return chunkByParagraphs(body)
        }

        val chapters = mutableListOf<Chapter>()
        val first = indices.first()
        if (first > 0) {
            val preface = text.substring(0, first).trim()
            if (preface.isNotEmpty()) {
                chapters += Chapter("前言", preface)
            }
        }
        indices.forEachIndexed { i, start ->
            val end = indices.getOrNull(i + 1) ?: text.length
            val block = text.substring(start, end).trim()
            val firstLine = block.lineSequence().firstOrNull()?.trim().orEmpty()
            val title = firstLine.ifBlank { "第${i + 1}章" }
            val content = block.removePrefix(firstLine).trimStart('\n', '\r', ' ')
            chapters += Chapter(title, content.ifBlank { block })
        }
        return chapters.ifEmpty { listOf(Chapter("全文", text.trim())) }
    }

    fun formatBody(content: String): String {
        val paragraphs = content
            .replace("\r\n", "\n")
            .split(Regex("""\n\s*\n"""))
            .map { p ->
                p.lines()
                    .map { it.trim() }
                    .filter { it.isNotEmpty() }
                    .joinToString("")
            }
            .filter { it.isNotEmpty() }

        if (paragraphs.isEmpty()) {
            // soft-wrap style: keep single newlines as paragraph breaks when dense
            val soft = content.lines()
                .map { it.trim() }
                .filter { it.isNotEmpty() }
            return soft.joinToString("\n\n") { "　　$it" }
        }

        return paragraphs.joinToString("\n\n") { "　　$it" }
    }

    private fun chunkByParagraphs(body: String): List<Chapter> {
        val parts = body.split("\n\n")
        val chapters = mutableListOf<Chapter>()
        val buf = StringBuilder()
        var index = 1
        fun flush() {
            if (buf.isNotBlank()) {
                chapters += Chapter("第${index}节", buf.toString().trim())
                index++
                buf.clear()
            }
        }
        for (p in parts) {
            if (buf.length + p.length > 3500 && buf.isNotEmpty()) flush()
            if (buf.isNotEmpty()) buf.append("\n\n")
            buf.append(p)
        }
        flush()
        return chapters.ifEmpty { listOf(Chapter("全文", body)) }
    }
}
