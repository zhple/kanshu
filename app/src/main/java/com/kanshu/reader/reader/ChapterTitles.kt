package com.kanshu.reader.reader

/**
 * 目录展示用：统一自动标顺序。
 * 左侧固定序号；若原标题已含「第x章 / Chapter n」等则不再重复加前缀。
 */
object ChapterTitles {
    private val numberedPrefix = Regex(
        """^\s*(?:第[\d零一二三四五六七八九十百千两]+[章节回部卷集]|Chapter\s+\d+|CHAPTER\s+\d+|\d{1,4}\s*[\.、．:：\)）])""",
        RegexOption.IGNORE_CASE
    )

    fun displayTitle(index: Int, rawTitle: String): String {
        val cleaned = rawTitle
            .replace('\n', ' ')
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { "未命名章节" }

        // 已自带章节序号时，只做清理，不再叠「第n章」
        if (numberedPrefix.containsMatchIn(cleaned)) {
            return cleaned
        }
        return "第${index + 1}章 $cleaned"
    }

    fun sequenceLabel(index: Int): String = (index + 1).toString().padStart(2, '0')
}
