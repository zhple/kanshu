package com.kanshu.reader.reader

import org.jsoup.Jsoup
import java.io.BufferedInputStream
import java.io.File
import java.io.InputStream
import java.nio.charset.Charset
import java.util.zip.ZipFile
import javax.xml.parsers.DocumentBuilderFactory

enum class BookFormat(val extension: String) {
    TXT(".txt"),
    EPUB(".epub"),
    PDF(".pdf");

    companion object {
        fun fromFileName(name: String): BookFormat? {
            val lower = name.lowercase()
            return entries.firstOrNull { lower.endsWith(it.extension) }
        }

        fun fromStored(name: String): BookFormat =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: TXT
    }
}

data class BookMetadata(
    val title: String,
    val author: String
)

data class Chapter(
    val title: String,
    val content: String
)

data class ParsedBook(
    val metadata: BookMetadata,
    val chapters: List<Chapter>
)

object BookParser {
    fun readMetadata(file: File, format: BookFormat, fallbackTitle: String): BookMetadata {
        return when (format) {
            BookFormat.TXT -> BookMetadata(
                title = fallbackTitle.removeSuffix(".txt").removeSuffix(".TXT"),
                author = "未知作者"
            )
            BookFormat.PDF -> BookMetadata(
                title = fallbackTitle
                    .removeSuffix(".pdf")
                    .removeSuffix(".PDF"),
                author = "PDF"
            )
            BookFormat.EPUB -> {
                val parsed = parseEpub(file)
                BookMetadata(
                    title = parsed.metadata.title.ifBlank {
                        fallbackTitle.removeSuffix(".epub").removeSuffix(".EPUB")
                    },
                    author = parsed.metadata.author.ifBlank { "未知作者" }
                )
            }
        }
    }

    fun parse(file: File, format: BookFormat, fallbackTitle: String): ParsedBook {
        return when (format) {
            BookFormat.TXT -> parseTxt(file, fallbackTitle)
            BookFormat.PDF -> error("PDF 请使用专用阅读器打开")
            BookFormat.EPUB -> {
                val parsed = parseEpub(file)
                parsed.copy(
                    metadata = BookMetadata(
                        title = parsed.metadata.title.ifBlank {
                            fallbackTitle.removeSuffix(".epub").removeSuffix(".EPUB")
                        },
                        author = parsed.metadata.author.ifBlank { "未知作者" }
                    ),
                    chapters = parsed.chapters.ifEmpty {
                        listOf(Chapter("正文", "（无法解析章节内容）"))
                    }
                )
            }
        }
    }

    private fun parseTxt(file: File, fallbackTitle: String): ParsedBook {
        val raw = readTextWithGuess(file)
        val text = TxtReadingFormatter.cleanRawText(raw)
        val title = TxtReadingFormatter.guessTitle(text, fallbackTitle)
        val chapters = TxtReadingFormatter.splitChapters(text).map { chapter ->
            chapter.copy(content = TxtReadingFormatter.formatBody(chapter.content))
        }
        return ParsedBook(
            metadata = BookMetadata(title = title, author = "未知作者"),
            chapters = chapters
        )
    }

    private fun readTextWithGuess(file: File): String {
        val bytes = file.readBytes()
        // BOM
        if (bytes.size >= 3 && bytes[0] == 0xEF.toByte() && bytes[1] == 0xBB.toByte() && bytes[2] == 0xBF.toByte()) {
            return String(bytes, 3, bytes.size - 3, Charsets.UTF_8)
        }
        val candidates = listOf(
            Charset.forName("GB18030"),
            Charset.forName("GBK"),
            Charsets.UTF_8,
            Charsets.UTF_16LE,
            Charsets.UTF_16BE
        )
        var best: Pair<String, Int>? = null
        for (charset in candidates) {
            try {
                val decoded = String(bytes, charset)
                val score = scoreDecodedText(decoded)
                if (best == null || score > best.second) {
                    best = decoded to score
                }
            } catch (_: Exception) {
                // try next
            }
        }
        return best?.first ?: String(bytes, Charsets.UTF_8)
    }

    private fun scoreDecodedText(text: String): Int {
        if (text.isEmpty()) return Int.MIN_VALUE / 2
        val sample = text.take(8000)
        var score = 0
        var replacement = 0
        var cjk = 0
        var control = 0
        for (ch in sample) {
            when {
                ch == '\uFFFD' -> replacement++
                ch in '\u4e00'..'\u9fff' -> cjk++
                ch.code < 32 && ch != '\n' && ch != '\r' && ch != '\t' -> control++
            }
        }
        score += cjk * 3
        score -= replacement * 40
        score -= control * 20
        // Prefer texts that look like novels
        if (sample.contains("第") && (sample.contains("章") || sample.contains("卷"))) score += 50
        return score
    }


    private fun parseEpub(file: File): ParsedBook {
        ZipFile(file).use { zip ->
            val containerEntry = zip.getEntry("META-INF/container.xml")
                ?: return ParsedBook(BookMetadata("未知书名", "未知作者"), emptyList())
            val opfPath = zip.getInputStream(containerEntry).use { input ->
                findOpfPath(input)
            } ?: return ParsedBook(BookMetadata("未知书名", "未知作者"), emptyList())

            val opfEntry = zip.getEntry(opfPath)
                ?: return ParsedBook(BookMetadata("未知书名", "未知作者"), emptyList())
            val opfDir = opfPath.substringBeforeLast('/', missingDelimiterValue = "")
            val (metadata, spineHrefs) = zip.getInputStream(opfEntry).use { input ->
                parseOpf(input, opfDir)
            }

            val chapters = spineHrefs.mapNotNull { href ->
                val entry = zip.getEntry(href) ?: return@mapNotNull null
                val html = zip.getInputStream(entry).bufferedReader(Charsets.UTF_8).use { it.readText() }
                val doc = Jsoup.parse(html)
                val title = doc.selectFirst("h1,h2,h3,title")?.text()?.trim().orEmpty()
                    .ifBlank { "章节 ${spineHrefs.indexOf(href) + 1}" }
                val content = doc.body()?.text()?.trim().orEmpty()
                if (content.isBlank()) null else Chapter(title, content)
            }

            return ParsedBook(metadata, chapters)
        }
    }

    private fun findOpfPath(input: InputStream): String? {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder().parse(BufferedInputStream(input))
        val nodes = doc.getElementsByTagNameNS("*", "rootfile")
        if (nodes.length == 0) return null
        return nodes.item(0).attributes.getNamedItem("full-path")?.nodeValue
    }

    private fun parseOpf(input: InputStream, opfDir: String): Pair<BookMetadata, List<String>> {
        val factory = DocumentBuilderFactory.newInstance().apply {
            isNamespaceAware = true
        }
        val doc = factory.newDocumentBuilder().parse(BufferedInputStream(input))

        fun firstText(localName: String): String {
            val nodes = doc.getElementsByTagNameNS("*", localName)
            return if (nodes.length > 0) nodes.item(0).textContent?.trim().orEmpty() else ""
        }

        val title = firstText("title").ifBlank { "未知书名" }
        val author = firstText("creator").ifBlank { "未知作者" }

        val idToHref = mutableMapOf<String, String>()
        val itemNodes = doc.getElementsByTagNameNS("*", "item")
        for (i in 0 until itemNodes.length) {
            val node = itemNodes.item(i)
            val id = node.attributes.getNamedItem("id")?.nodeValue ?: continue
            val href = node.attributes.getNamedItem("href")?.nodeValue ?: continue
            idToHref[id] = resolvePath(opfDir, href)
        }

        val spine = mutableListOf<String>()
        val itemRefs = doc.getElementsByTagNameNS("*", "itemref")
        for (i in 0 until itemRefs.length) {
            val idref = itemRefs.item(i).attributes.getNamedItem("idref")?.nodeValue ?: continue
            idToHref[idref]?.let { spine += it }
        }

        return BookMetadata(title, author) to spine
    }

    private fun resolvePath(baseDir: String, href: String): String {
        val cleaned = href.substringBefore('#').replace('\\', '/')
        if (baseDir.isBlank()) return cleaned
        return "$baseDir/$cleaned".replace("//", "/")
    }
}
