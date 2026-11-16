package com.azuredevid.cassy.core.util

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.common.BitMatrix
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.io.OutputStream

/** Text and QR content of one tent card. Strings come from resources; the URL from [TableQrUrl]. */
data class TentCardContent(
    val storeName: String,
    val heading: String,
    val tableName: String,
    val section: String,
    val scanHint: String,
    val qrUrl: String
)

/**
 * Geometry of an A6 portrait tent card in PDF points (1/72 inch). The page folds across the middle;
 * each half carries the same face, the top half drawn upside down so both read upright once folded.
 * All values are local to one half (origin at the half's top-left), which keeps the math testable
 * without Android graphics.
 */
data class TentCardLayout(
    val pageWidth: Float = A6_WIDTH_PT,
    val pageHeight: Float = A6_HEIGHT_PT,
    val margin: Float = 16f
) {
    val halfHeight: Float get() = pageHeight / 2f
    val foldY: Float get() = halfHeight

    /** Side of the square QR: as large as the half allows after the text block, capped by width. */
    val qrSize: Float get() = minOf(pageWidth - 2 * margin, halfHeight - 2 * margin - TEXT_BLOCK).coerceAtLeast(MIN_QR)
    val qrLeft: Float get() = (pageWidth - qrSize) / 2f
    val qrTop: Float get() = margin + HEADER_BLOCK

    val storeBaseline: Float get() = margin + 10f
    val tableBaseline: Float get() = margin + 34f
    val hintBaseline: Float get() = qrTop + qrSize + 16f
    val sectionBaseline: Float get() = hintBaseline + 13f

    companion object {
        /** A6 is 105 x 148 mm. */
        const val A6_WIDTH_PT = 105f / 25.4f * 72f
        const val A6_HEIGHT_PT = 148f / 25.4f * 72f
        private const val HEADER_BLOCK = 42f
        private const val FOOTER_BLOCK = 34f
        const val TEXT_BLOCK = HEADER_BLOCK + FOOTER_BLOCK
        /** About 30 mm, the smallest size phone cameras read reliably from a table. */
        const val MIN_QR = 85f
    }
}

/**
 * Writes tent cards as a PDF, one A6 page per [TentCardContent]. QR modules are drawn as vector
 * rectangles, so the code stays crisp on any printer. Print colours are fixed black on white: this is
 * paper output, not themed UI.
 */
class TableTentCardPdfRenderer(private val layout: TentCardLayout = TentCardLayout()) {

    /**
     * @throws IllegalArgumentException when [cards] is empty.
     * @throws java.io.IOException when [output] fails; the stream is not closed here.
     */
    fun render(cards: List<TentCardContent>, output: OutputStream) {
        require(cards.isNotEmpty()) { "At least one tent card is required" }
        val document = PdfDocument()
        try {
            cards.forEachIndexed { index, card ->
                val info = PdfDocument.PageInfo.Builder(
                    layout.pageWidth.toInt(), layout.pageHeight.toInt(), index + 1
                ).create()
                val page = document.startPage(info)
                drawPage(page.canvas, card)
                document.finishPage(page)
            }
            document.writeTo(output)
        } finally {
            document.close()
        }
    }

    private fun drawPage(canvas: Canvas, card: TentCardContent) {
        val matrix = encodeQr(card.qrUrl)
        // Top half upside down: rotate about the centre of the top half.
        canvas.save()
        canvas.rotate(180f, layout.pageWidth / 2f, layout.halfHeight / 2f)
        drawFace(canvas, card, matrix)
        canvas.restore()
        // Bottom half upright.
        canvas.save()
        canvas.translate(0f, layout.halfHeight)
        drawFace(canvas, card, matrix)
        canvas.restore()
        canvas.drawLine(0f, layout.foldY, layout.pageWidth, layout.foldY, foldPaint)
    }

    private fun drawFace(canvas: Canvas, card: TentCardContent, matrix: BitMatrix) {
        val centerX = layout.pageWidth / 2f
        canvas.drawText(card.storeName, centerX, layout.storeBaseline, smallPaint)
        canvas.drawText("${card.heading} ${card.tableName}".trim(), centerX, layout.tableBaseline, titlePaint)
        val cell = layout.qrSize / matrix.width
        for (y in 0 until matrix.height) {
            for (x in 0 until matrix.width) {
                if (matrix[x, y]) {
                    val left = layout.qrLeft + x * cell
                    val top = layout.qrTop + y * cell
                    canvas.drawRect(left, top, left + cell, top + cell, inkPaint)
                }
            }
        }
        canvas.drawText(card.scanHint, centerX, layout.hintBaseline, bodyPaint)
        if (card.section.isNotBlank()) {
            canvas.drawText(card.section, centerX, layout.sectionBaseline, smallPaint)
        }
    }

    private fun encodeQr(url: String): BitMatrix {
        val hints = mapOf(
            EncodeHintType.CHARACTER_SET to "UTF-8",
            EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M,
            EncodeHintType.MARGIN to 2
        )
        // Size 0 returns one bit per module plus the quiet zone; scaling happens when drawing.
        return QRCodeWriter().encode(url, BarcodeFormat.QR_CODE, 0, 0, hints)
    }

    private fun textPaint(size: Float, bold: Boolean) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = INK
        textSize = size
        textAlign = Paint.Align.CENTER
        typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
    }

    private val inkPaint = Paint().apply { color = INK; style = Paint.Style.FILL }
    private val titlePaint = textPaint(18f, bold = true)
    private val bodyPaint = textPaint(10f, bold = true)
    private val smallPaint = textPaint(8f, bold = false)
    private val foldPaint = Paint().apply {
        color = FOLD_GREY
        strokeWidth = 0.5f
        pathEffect = DashPathEffect(floatArrayOf(4f, 3f), 0f)
    }

    private companion object {
        const val INK = 0xFF000000.toInt()
        const val FOLD_GREY = 0xFF9E9E9E.toInt()
    }
}
