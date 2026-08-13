package com.kanshu.reader.reader

import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import kotlin.math.ceil
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
        /** 与 Compose Text 的 lineHeight 一致（px） */
        lineHeightPx: Float,
        /** 预留误差，避免末行被裁切 */
        safetyPx: Int = 0
    ): List<ReaderPage> {
        if (chapters.isEmpty() || widthPx <= 0 || heightPx <= 0) return emptyList()

        val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
            textSize = textSizePx
            isAntiAlias = true
        }
        val usableHeight = (heightPx - safetyPx).coerceAtLeast(1)

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
                heightPx = usableHeight,
                paint = paint,
                lineHeightPx = lineHeightPx,
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
        lineHeightPx: Float,
        chapterIndex: Int,
        chapterTitle: String
    ): List<ReaderPage> {
        val layout = buildLayout(text, widthPx, paint, lineHeightPx)
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
        lineHeightPx: Float
    ): StaticLayout {
        val fm = paint.fontMetrics
        val fontHeight = fm.descent - fm.ascent
        // 对齐 Compose：lineHeight 为行盒高度，用 spacingAdd 补足
        val spacingAdd = (lineHeightPx - fontHeight).coerceAtLeast(0f)
        return StaticLayout.Builder
            .obtain(text, 0, text.length, paint, max(1, widthPx))
            .setAlignment(Layout.Alignment.ALIGN_NORMAL)
            .setLineSpacing(spacingAdd, 1f)
            .setIncludePad(false)
            .build()
    }

    /** 按行高估算一页大约多少行，用于安全边距 */
    fun suggestedSafetyPx(lineHeightPx: Float): Int {
        return ceil(lineHeightPx * 0.35f).toInt().coerceAtLeast(8)
    }
}
