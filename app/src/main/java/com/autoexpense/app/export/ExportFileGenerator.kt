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
import com.autoexpense.app.data.TransactionEntity
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
        transactions: List<TransactionEntity>
    ): Pair<File, Uri> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) {
            throw IllegalStateException("No confirmed expenses found for this period.")
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
        transactions: List<TransactionEntity>,
        periodText: String
    ): Pair<File, Uri> = withContext(Dispatchers.IO) {
        if (transactions.isEmpty()) {
            throw IllegalStateException("No confirmed expenses found for this period.")
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
            color = Color.parseColor("#FF6E00") // ColorOrange
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
            color = Color.parseColor("#FF6E00")
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val paintLine = Paint().apply {
            color = Color.parseColor("#CCCCCC")
            strokeWidth = 1f
        }

        var yPos = margin + 20f

        // Title Header
        canvas.drawText("AutoExpense", margin, yPos, paintTitle)
        yPos += 20f
        canvas.drawText("Expense Report", margin, yPos, paintSubtitle)
        yPos += 16f
        canvas.drawText("Selected period: $periodText", margin, yPos, paintText)
        yPos += 15f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
        yPos += 20f

        // Summary Section
        val totalSpent = ExportFilterHelper.calculateTotalSpent(transactions)
        val categoryMap = transactions.groupBy { it.category }.mapValues { entry ->
            entry.value.sumOf { ExportFilterHelper.parseAmount(it.amount) }
        }
        val highestCategory = categoryMap.maxByOrNull { it.value }?.key ?: "None"
        val highestAmt = categoryMap[highestCategory] ?: 0.0

        canvas.drawText("Summary", margin, yPos, paintSection)
        yPos += 16f
        canvas.drawText("Total amount spent: ${ExportFilterHelper.formatIndianCurrency(totalSpent)}", margin, yPos, paintBold)
        yPos += 14f
        canvas.drawText("Number of transactions: ${transactions.size}", margin, yPos, paintText)
        yPos += 14f
        canvas.drawText("Highest spending category: $highestCategory (${ExportFilterHelper.formatIndianCurrency(highestAmt)})", margin, yPos, paintText)
        yPos += 20f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
        yPos += 20f

        // Category breakdown Section
        canvas.drawText("Category breakdown", margin, yPos, paintSection)
        yPos += 16f

        // Table Header: Category | Amount | Percentage of total spending
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
            val pct = if (totalSpent > 0) (entry.value / totalSpent) * 100.0 else 0.0
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

        canvas.drawText("Transaction table", margin, yPos, paintSection)
        yPos += 16f

        // Table Header: Date | Merchant | Category | Source | Note | Amount
        val cDate = margin
        val cMerchant = margin + 65f
        val cCategory = margin + 190f
        val cSource = margin + 295f
        val cNote = margin + 355f
        val cAmount = margin + 445f

        fun drawTableHeader(c: Canvas, y: Float) {
            c.drawText("Date", cDate, y, paintBold)
            c.drawText("Merchant", cMerchant, y, paintBold)
            c.drawText("Category", cCategory, y, paintBold)
            c.drawText("Source", cSource, y, paintBold)
            c.drawText("Note", cNote, y, paintBold)
            c.drawText("Amount", cAmount, y, paintBold)
        }

        drawTableHeader(canvas, yPos)
        yPos += 6f
        canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
        yPos += 14f

        val dateFormat = SimpleDateFormat("dd/MM/yy", Locale.US)

        for (t in transactions) {
            val dateStr = if (t.timestamp > 0) dateFormat.format(Date(t.timestamp)) else ""
            val merchantLines = wrapText(t.merchantOrRecipient, paintText, 115f)
            val categoryText = truncateText(t.category, paintText, 95f)
            val sourceText = truncateText(t.source, paintText, 50f)
            val noteLines = wrapText(if (t.note.isNotEmpty()) t.note else "-", paintText, 80f)
            val amtVal = ExportFilterHelper.parseAmount(t.amount)
            val amtStr = ExportFilterHelper.formatIndianCurrency(amtVal)

            val maxLines = maxOf(merchantLines.size, noteLines.size)
            val rowHeight = maxLines * 12f + 4f

            if (yPos + rowHeight > pageHeight - margin) {
                pdfDocument.finishPage(page)
                pageNumber++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNumber).create()
                page = pdfDocument.startPage(pageInfo)
                canvas = page.canvas
                yPos = margin + 20f
                canvas.drawText("Transaction table (cont.)", margin, yPos, paintSection)
                yPos += 16f
                drawTableHeader(canvas, yPos)
                yPos += 6f
                canvas.drawLine(margin, yPos, pageWidth - margin, yPos, paintLine)
                yPos += 14f
            }

            canvas.drawText(dateStr, cDate, yPos, paintText)
            canvas.drawText(categoryText, cCategory, yPos, paintText)
            canvas.drawText(sourceText, cSource, yPos, paintText)
            canvas.drawText(amtStr, cAmount, yPos, paintText)

            for (i in 0 until maxLines) {
                val lineY = yPos + i * 12f
                if (i < merchantLines.size) {
                    canvas.drawText(merchantLines[i], cMerchant, lineY, paintText)
                }
                if (i < noteLines.size) {
                    canvas.drawText(noteLines[i], cNote, lineY, paintText)
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
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/AutoExpense")
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
