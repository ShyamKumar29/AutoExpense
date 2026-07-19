package com.autoexpense.app.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.autoexpense.app.domain.FinancialTransaction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExportFileGenerator {

    suspend fun generateCsvFile(
        context: Context,
        filename: String,
        transactions: List<FinancialTransaction>
    ): Pair<File, Uri> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) {
            throw IllegalStateException("No confirmed transactions found for this period.")
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, filename)
        val content = ExportFilterHelper.generateCsvContent(transactions)
        file.writeText(content, Charsets.UTF_8)

        // Also save to MediaStore on Q+ for permanent user accessibility without broad permissions
        trySaveToMediaStore(context, filename, "text/csv", file)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Pair(file, uri)
    }

    suspend fun generatePdfFile(
        context: Context,
        filename: String,
        transactions: List<FinancialTransaction>,
        periodText: String
    ): Pair<File, Uri> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) {
            throw IllegalStateException("No confirmed transactions found for this period.")
        }

        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(exportDir, filename)

        val pdfDocument = PdfDocument()
        val pageWidth = 595 // A4 width in points at 72 dpi
        val pageHeight = 842 // A4 height in points
        val margin = 40f
        var pageNumber = 1

        var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paintTitle = Paint().apply {
            color = Color.parseColor("#5B7FFF")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintSubtitle = Paint().apply {
            color = Color.parseColor("#1E1E1E")
            textSize = 15f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintText = Paint().apply {
            color = Color.parseColor("#333333")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            isAntiAlias = true
        }
        val paintBold = Paint().apply {
            color = Color.parseColor("#1E1E1E")
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintSection = Paint().apply {
            color = Color.parseColor("#5B7FFF")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintLine = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 1f
        }

        var yPos = margin + 20f

        val paintIncome = Paint(paintText).apply { color = Color.parseColor("#1BA97F") }
        val paintExpense = Paint(paintText).apply { color = Color.parseColor("#D94B4B") }

        // One-page financial summary
        val summary = ExportFilterHelper.calculateSummary(transactions, periodText)
        val exportDate = SimpleDateFormat("d MMM yyyy, h:mm a", Locale.US).format(Date())

        canvas.drawText("ZORS", margin, yPos, paintTitle)
        yPos += 20f
        canvas.drawText("Financial Report", margin, yPos, paintSubtitle)
        yPos += 16f
        canvas.drawText("Report period: ${summary.dateRangeLabel}", margin, yPos, paintText)
        yPos += 14f
        canvas.drawText("Export date: $exportDate", margin, yPos, paintText)
        yPos += 15f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)

        yPos += 30f
        val labelX = margin
        val valueX = margin + 150f
        fun drawSummaryRow(label: String, value: String, valuePaint: Paint = paintBold) {
            canvas.drawText(label, labelX, yPos, paintText)
            canvas.drawText(value, valueX, yPos, valuePaint)
            yPos += 18f
        }

        drawSummaryRow("Income", ExportFilterHelper.formatIndianCurrency(summary.income), paintIncome)
        drawSummaryRow("Expenses", ExportFilterHelper.formatIndianCurrency(summary.expenses), paintExpense)
        drawSummaryRow("Net Savings", ExportFilterHelper.formatIndianCurrency(summary.netSavings), if (summary.netSavings >= 0.0) paintIncome else paintExpense)
        drawSummaryRow("Cash Flow", ExportFilterHelper.formatIndianCurrency(summary.cashFlow), if (summary.cashFlow >= 0.0) paintIncome else paintExpense)
        yPos += 6f
        drawSummaryRow("Transactions", summary.transactionCount.toString(), paintBold)
        drawSummaryRow("Largest Income", ExportFilterHelper.formatIndianCurrency(summary.largestIncome), paintIncome)
        drawSummaryRow("Largest Expense", ExportFilterHelper.formatIndianCurrency(summary.largestExpense), paintExpense)

        yPos += 14f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
        yPos += 20f

        // Category breakdown Section
        val categoryMap = transactions.groupBy { it.category }.mapValues { entry ->
            entry.value.sumOf { kotlin.math.abs(ExportFilterHelper.signedAmount(it)) }
        }
        val categoryTotal = categoryMap.values.sum()

        canvas.drawText("Category Breakdown", margin, yPos, paintSection)
        yPos += 16f

        val colCat = margin
        val colAmt = margin + 200f
        val colPct = margin + 340f

        canvas.drawText("Category", colCat, yPos, paintBold)
        canvas.drawText("Amount", colAmt, yPos, paintBold)
        canvas.drawText("Percentage", colPct, yPos, paintBold)
        yPos += 6f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
        yPos += 14f

        val sortedCategories = categoryMap.entries.sortedByDescending { it.value }
        for (entry in sortedCategories) {
            if (yPos > pageHeight - margin - 40f) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = margin + 20f
                canvas.drawText("Category breakdown (cont.)", margin, yPos, paintSection)
                yPos += 16f
            }
            val pct = if (categoryTotal > 0) (entry.value / categoryTotal) * 100.0 else 0.0
            val pctStr = String.format(Locale.US, "%.1f%%", pct)
            val catText = truncateText(entry.key, paintText, 180f)
            canvas.drawText(catText, colCat, yPos, paintText)
            canvas.drawText(ExportFilterHelper.formatIndianCurrency(entry.value), colAmt, yPos, paintText)
            canvas.drawText(pctStr, colPct, yPos, paintText)
            yPos += 14f
        }

        yPos += 10f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
        yPos += 20f

        // Transaction table Section
        if (yPos > pageHeight - margin - 80f) {
            pdfDocument.finishPage(page)
            pageNumber++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
            page = pdfDocument.startPage(pageInfo)
            canvas = page.canvas
            yPos = margin + 20f
        }

        canvas.drawText("Transaction Details", margin, yPos, paintSection)
        yPos += 16f

        val cDate = margin
        val cTitle = margin + 58f
        val cCategory = margin + 158f
        val cType = margin + 250f
        val cMethod = margin + 335f
        val cAmount = margin + 430f

        fun drawTableHeader(c: Canvas, y: Float) {
            c.drawText("Date", cDate, y, paintBold)
            c.drawText("Title", cTitle, y, paintBold)
            c.drawText("Category", cCategory, y, paintBold)
            c.drawText("Type", cType, y, paintBold)
            c.drawText("Method", cMethod, y, paintBold)
            c.drawText("Amount", cAmount, y, paintBold)
        }

        drawTableHeader(canvas, yPos)
        yPos += 6f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
        yPos += 14f

        val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)

        for (t in transactions) {
            val dateStr = if (t.date > 0) dateFormat.format(Date(t.date)) else ""
            val title = t.title.ifBlank { t.merchant }.ifBlank { t.transactionType.name.replace('_', ' ') }
            val titleLines = wrapText(title, paintText, 88f)
            val categoryText = truncateText(t.category, paintText, 82f)
            val typeText = truncateText(t.transactionType.name.replace('_', ' '), paintText, 75f)
            val methodText = truncateText(ExportFilterHelper.paymentMethodLabel(t.paymentMethod), paintText, 78f)
            val amtStr = ExportFilterHelper.formatSignedAmount(t)
            val amtPaint = if (ExportFilterHelper.signedAmount(t) >= 0.0) paintIncome else paintExpense

            val maxLines = titleLines.size
            val rowHeight = maxLines * 12f + 4f

            if (yPos + rowHeight > pageHeight - margin) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = margin + 20f
                canvas.drawText("Transaction Details (cont.)", margin, yPos, paintSection)
                yPos += 16f
                drawTableHeader(canvas, yPos)
                yPos += 6f
                canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
                yPos += 14f
            }

            canvas.drawText(dateStr, cDate, yPos, paintText)
            canvas.drawText(categoryText, cCategory, yPos, paintText)
            canvas.drawText(typeText, cType, yPos, paintText)
            canvas.drawText(methodText, cMethod, yPos, paintText)
            canvas.drawText(amtStr, cAmount, yPos, amtPaint)

            for (i in 0 until maxLines) {
                val lineY = yPos + i * 12f
                if (i < titleLines.size) {
                    canvas.drawText(titleLines[i], cTitle, lineY, paintText)
                }
            }

            yPos += rowHeight
            canvas.drawLine(margin, yPos - 2f, pageWidth - margin, yPos - 2f, paintLine)
            yPos += 6f
        }

        pdfDocument.finishPage(page)

        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        trySaveToMediaStore(context, filename, "application/pdf", file)

        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        Pair(file, uri)
    }

    private fun truncateText(text: String, paint: Paint, maxWidth: Float): String {
        if (paint.measureText(text) <= maxWidth) return text
        var end = text.length
        while (end > 0 && paint.measureText(text.substring(0, end) + "...") > maxWidth) {
            end--
        }
        return if (end > 0) text.substring(0, end) + "..." else text
    }

    private fun wrapText(text: String, paint: Paint, maxWidth: Float): List<String> {
        if (text.isEmpty()) return listOf("")
        val lines = mutableListOf<String>()
        val words = text.split(" ")
        var currentLine = StringBuilder()
        for (word in words) {
            val testLine = if (currentLine.isEmpty()) word else "$currentLine $word"
            if (paint.measureText(testLine) <= maxWidth) {
                currentLine = StringBuilder(testLine)
            } else {
                if (currentLine.isNotEmpty()) {
                    lines.add(currentLine.toString())
                    currentLine = StringBuilder(word)
                } else {
                    lines.add(word)
                    currentLine = StringBuilder()
                }
            }
        }
        if (currentLine.isNotEmpty()) {
            lines.add(currentLine.toString())
        }
        return if (lines.isEmpty()) listOf("") else lines
    }

    private fun trySaveToMediaStore(context: Context, filename: String, mimeType: String, sourceFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            try {
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Zors")
                }
                val resolver = context.contentResolver
                val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                if (uri != null) {
                    resolver.openOutputStream(uri)?.use { out ->
                        sourceFile.inputStream().use { inp ->
                            inp.copyTo(out)
                        }
                    }
                }
            } catch (e: Exception) {
                // Silently ignore if MediaStore insertion is restricted on device/emulator
            }
        }
    }
}
