package com.kanshu.reader.reader

import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import java.io.File
import kotlin.math.min

/**
 * 把写作正文（含 [[IMG:...]]）导出为可在 App 内打开的 PDF。
 */
object TextPdfExporter {
    private const val PAGE_WIDTH = 595 // A4 points
    private const val PAGE_HEIGHT = 842
    private const val MARGIN = 48f
    private const val TITLE_SIZE = 18f
    private const val BODY_SIZE = 12f
    private const val LINE_GAP = 6f
    private const val PARAGRAPH_GAP = 10f

    fun export(
        title: String,
        content: String,
        dest: File,
        resolveImage: (String) -> File?
    ) {
        dest.parentFile?.mkdirs()
        if (dest.exists()) dest.delete()

        val titlePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = TITLE_SIZE
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            color = 0xFF222222.toInt()
        }
        val bodyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = BODY_SIZE
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            color = 0xFF222222.toInt()
        }
        val chapterPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textSize = 14f
            typeface = Typeface.create(Typeface.SERIF, Typeface.BOLD)
            color = 0xFF222222.toInt()
        }

        val contentWidth = PAGE_WIDTH - MARGIN * 2
        val maxY = PAGE_HEIGHT - MARGIN
        val document = PdfDocument()
        var pageNumber = 1
        var page = document.startPage(
            PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        )
        var canvas = page.canvas
        var y = MARGIN

        fun newPage() {
            document.finishPage(page)
            pageNumber++
            page = document.startPage(
                PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
            )
            canvas = page.canvas
            y = MARGIN
        }

        fun ensureSpace(needed: Float) {
            if (y + needed > maxY) newPage()
        }

        fun drawWrapped(text: String, paint: Paint) {
            if (text.isBlank()) {
                y += PARAGRAPH_GAP
                return
            }
            var start = 0
            while (start < text.length) {
                ensureSpace(paint.textSize + LINE_GAP)
                val count = paint.breakText(text, start, text.length, true, contentWidth, null)
                if (count <= 0) break
                val line = text.substring(start, start + count)
                canvas.drawText(line, MARGIN, y + paint.textSize, paint)
                y += paint.textSize + LINE_GAP
                start += count
            }
            y += PARAGRAPH_GAP / 2
        }

        ensureSpace(titlePaint.textSize + 20f)
        drawWrapped(title.trim().ifBlank { "未命名文稿" }, titlePaint)
        y += 8f

        val blocks = splitBlocks(content)
        for (block in blocks) {
            when (block) {
                is Block.Text -> {
                    val line = block.value.trim()
                    if (line.isEmpty()) {
                        y += PARAGRAPH_GAP
                        continue
                    }
                    val paint = if (isChapterTitle(line)) chapterPaint else bodyPaint
                    drawWrapped(line, paint)
                }
                is Block.Image -> {
                    val file = resolveImage(block.path)
                    if (file == null || !file.exists()) {
                        drawWrapped("【图片缺失：${block.path}】", bodyPaint)
                        continue
                    }
                    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
                    BitmapFactory.decodeFile(file.absolutePath, bounds)
                    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
                        drawWrapped("【无法读取图片】", bodyPaint)
                        continue
                    }
                    val maxImgW = contentWidth * block.widthPercent.coerceIn(0.3f, 1f)
                    val maxImgH = PAGE_HEIGHT - MARGIN * 2 - 80f
                    var sample = 1
                    while (
                        bounds.outWidth / sample > maxImgW * 2 ||
                        bounds.outHeight / sample > maxImgH * 2
                    ) {
                        sample *= 2
                    }
                    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
                    val bitmap = BitmapFactory.decodeFile(file.absolutePath, opts)
                    if (bitmap == null) {
                        drawWrapped("【无法读取图片】", bodyPaint)
                    } else {
                        try {
                            val scale = min(maxImgW / bitmap.width, maxImgH / bitmap.height)
                                .coerceAtMost(1f)
                            val drawW = bitmap.width * scale
                            val drawH = bitmap.height * scale
                            ensureSpace(drawH + PARAGRAPH_GAP)
                            val left = MARGIN + (contentWidth - drawW) / 2f
                            val destRect = android.graphics.RectF(left, y, left + drawW, y + drawH)
                            canvas.drawBitmap(bitmap, null, destRect, null)
                            y += drawH + PARAGRAPH_GAP
                        } finally {
                            bitmap.recycle()
                        }
                    }
                }
            }
        }

        document.finishPage(page)
        dest.outputStream().use { document.writeTo(it) }
        document.close()
    }

    private sealed class Block {
        data class Text(val value: String) : Block()
        data class Image(val path: String, val widthPercent: Float) : Block()
    }

    private fun splitBlocks(content: String): List<Block> {
        val normalized = content.replace("\r\n", "\n").replace('\r', '\n')
        val result = mutableListOf<Block>()
        var last = 0
        for (match in WriteMarkers.imageRegex.findAll(normalized)) {
            if (match.range.first > last) {
                normalized.substring(last, match.range.first)
                    .split('\n')
                    .forEach { result += Block.Text(it) }
            }
            val path = match.groupValues[1].trim()
            val width = match.groupValues.getOrNull(2)
                ?.toFloatOrNull()
                ?.coerceIn(0.3f, 1f)
                ?: 1f
            result += Block.Image(path, width)
            last = match.range.last + 1
        }
        if (last < normalized.length) {
            normalized.substring(last)
                .split('\n')
                .forEach { result += Block.Text(it) }
        }
        return result
    }

    private fun isChapterTitle(line: String): Boolean {
        return line.matches(
            Regex("""^第[\d零一二三四五六七八九十百千两]+章\S*$""")
        ) || line.matches(Regex("""^(?:序章|终章|楔子|尾声|番外)\S*$"""))
    }
}
