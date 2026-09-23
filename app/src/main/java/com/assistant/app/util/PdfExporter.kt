package com.assistant.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.Environment
import android.widget.Toast
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object PdfExporter {

    fun exportToPdf(context: Context, title: String, content: String): File? {
        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // استاندارد A4
        var page = pdfDocument.startPage(pageInfo)
        var canvas = page.canvas

        val paintTitle = Paint().apply {
            color = Color.BLACK
            textSize = 16f
            isFakeBoldText = true
        }

        val paintBody = Paint().apply {
            color = Color.DKGRAY
            textSize = 11f
        }

        var y = 60f
        canvas.drawText("عنوان: $title", 50f, y, paintTitle)
        y += 35f

        val lines = content.split("\n")
        var pageNumber = 1

        for (line in lines) {
            // شکستن خطوط طولانی
            val words = line.split(" ")
            var currentLine = ""
            for (word in words) {
                if (paintBody.measureText("$currentLine $word") < 495) {
                    currentLine = if (currentLine.isEmpty()) word else "$currentLine $word"
                } else {
                    canvas.drawText(currentLine, 50f, y, paintBody)
                    y += 18f
                    currentLine = word
                    if (y > 780f) {
                        pdfDocument.finishPage(page)
                        pageNumber++
                        val nextPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                        page = pdfDocument.startPage(nextPageInfo)
                        canvas = page.canvas
                        y = 60f
                    }
                }
            }
            if (currentLine.isNotEmpty()) {
                canvas.drawText(currentLine, 50f, y, paintBody)
                y += 18f
            }

            if (y > 780f) {
                pdfDocument.finishPage(page)
                pageNumber++
                val nextPageInfo = PdfDocument.PageInfo.Builder(595, 842, pageNumber).create()
                page = pdfDocument.startPage(nextPageInfo)
                canvas = page.canvas
                y = 60f
            }
        }

        pdfDocument.finishPage(page)

        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val fileName = "Doc_${timeStamp}.pdf"
        val downloadsDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val file = File(downloadsDir, fileName)

        return try {
            val fos = FileOutputStream(file)
            pdfDocument.writeTo(fos)
            pdfDocument.close()
            fos.close()
            Toast.makeText(context, "فایل در پوشه Downloads ذخیره شد: $fileName", Toast.LENGTH_LONG).show()
            file
        } catch (e: Exception) {
            pdfDocument.close()
            Toast.makeText(context, "خطا در ذخیره PDF: ${e.message}", Toast.LENGTH_SHORT).show()
            null
        }
    }
}
