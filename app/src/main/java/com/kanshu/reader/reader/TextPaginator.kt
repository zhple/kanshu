package com.kanshu.reader.reader

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.max

data class ReaderPage(
    val text: String,
    val chapterIndex: Int,
    val chapterTitle: String
)

object TextPaginator {
    fun paginateBook(
        chapters: List<Chapter>,
        widthPx: Int,
        heightPx: Int,
        textSizePx: Float,
        lineSpacingMultiplier: Float = 1.35f
    ): List<ReaderPage> {
        if (chapters.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptyList()

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
        }

        val pages = mutableListOf<ReaderPage>()
        chapters.forEachIndexed { chapterIndex, chapter ->
            val displayTitle = ChapterTitles.displayTitle(chapterIndex, chapter.title.trim())
            val body = buildString {
                append(displayTitle)
                append("\n\n")
                append(chapter.content.trim())
            }.ifBlank { "（空章节）" }

            pages += paginateText(
                text = body,
                widthPx = widthPx,
                heightPx = heightPx,
                paint = paint,
                lineSpacingMultiplier = lineSpacingMultiplier,
                chapterIndex = chapterIndex,
                chapterTitle = displayTitle
            )
        }
        return pages
    }

    private fun paginateText(
        text: String,
        widthPx: Int,
        heightPx: Int,
        paint: TextPaint,
        lineSpacingMultiplier: Float,
        chapterIndex: Int,
        chapterTitle: String
    ): List<ReaderPage> {
        val layout = buildLayout(text, widthPx, paint, lineSpacingMultiplier)
        if (layout.lineCount == 0) {
            return listOf(ReaderPage(text, chapterIndex, chapterTitle))
        }

        val pages = mutableListOf<ReaderPage>()
        var startLine = 0
        while (startLine < layout.lineCount) {
            val startOffset = layout.getLineStart(startLine)
            var endLine = startLine
            var lastFitting = startLine

            while (endLine < layout.lineCount) {
                val top = layout.getLineTop(startLine)
                val bottom = layout.getLineBottom(endLine)
                if (bottom - top > heightPx) break
                lastFitting = endLine
                endLine++
            }

            // Always consume at least one line to avoid infinite loop on huge lines
            if (endLine == startLine) {
                lastFitting = startLine
            }

            val endOffset = layout.getLineEnd(lastFitting)
            val pageText = text.substring(startOffset, endOffset).trimEnd()
            pages += ReaderPage(
                text = pageText.ifBlank { " " },
                chapterIndex = chapterIndex,
                chapterTitle = chapterTitle
            )
            startLine = lastFitting + 1
        }
        return pages.ifEmpty {
            listOf(ReaderPage(text, chapterIndex, chapterTitle))
        }
    }

    private fun buildLayout(
        text: String,
        widthPx: Int,
        paint: TextPaint,
        lineSpacingMultiplier: Float
    ): StaticLayout {
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, max(1, widthPx))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(0f, lineSpacingMultiplier)
            .setIncludePad(false)
            .build()
    }
}
