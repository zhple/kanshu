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
        val text = readTextWithGuess(file).replace("\r\n", "\n").replace('\r', '\n')
        val chapters = splitTxtChapters(text)
        return ParsedBook(
            metadata = BookMetadata(
                title = fallbackTitle.removeSuffix(".txt").removeSuffix(".TXT"),
                author = "未知作者"
            ),
            chapters = chapters
        )
    }

    private fun splitTxtChapters(text: String): List<Chapter> {
        val pattern = Regex(
            """(?m)^(第[\d零一二三四五六七八九十百千两]+[章节回部卷集].*|Chapter\s+\d+.*|CHAPTER\s+\d+.*)$"""
        )
        val matches = pattern.findAll(text).toList()
        if (matches.isEmpty()) {
            return listOf(Chapter("全文", text.trim().ifBlank { "（空文件）" }))
        }

        val chapters = mutableListOf<Chapter>()
        val firstStart = matches.first().range.first
        if (firstStart > 0) {
            val preface = text.substring(0, firstStart).trim()
            if (preface.isNotEmpty()) {
                chapters += Chapter("前言", preface)
            }
        }
        matches.forEachIndexed { index, match ->
            val start = match.range.first
            val end = matches.getOrNull(index + 1)?.range?.first ?: text.length
            val body = text.substring(start, end).trim()
            val title = match.value.trim().ifBlank { "第${index + 1}章" }
            chapters += Chapter(title, body)
        }
        return chapters.ifEmpty { listOf(Chapter("全文", text.trim())) }
    }

    private fun readTextWithGuess(file: File): String {
        val bytes = file.readBytes()
        val charsets = listOf(
            Charsets.UTF_8,
            Charset.forName("GBK"),
            Charset.forName("GB18030"),
            Charsets.UTF_16
        )
        for (charset in charsets) {
            try {
                val decoded = String(bytes, charset)
                if (!decoded.contains('\uFFFD') || charset == Charsets.UTF_8) {
                    if (decoded.count { it == '\uFFFD' } < decoded.length / 50) {
                        return decoded
                    }
                }
            } catch (_: Exception) {
                // try next
            }
        }
        return String(bytes, Charsets.UTF_8)
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
