package com.examcamp.app.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.core.content.FileProvider
import com.examcamp.app.data.Session
import java.io.File
import java.io.FileOutputStream

/** Shares exported files through the system share sheet (FileProvider, cache/exports). */
object ShareHelper {
    fun sharePdf(context: Context, file: File, chooserTitle: String) = share(context, file, "application/pdf", chooserTitle)

    fun sharePng(context: Context, file: File, chooserTitle: String) = share(context, file, "image/png", chooserTitle)

    private fun share(context: Context, file: File, mime: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, chooserTitle).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    /** A share card for the result: score, counts and the subject, drawn as a 1080 x 1350 PNG. */
    fun resultCard(context: Context, session: Session): File {
        val r = session.result ?: throw IllegalStateException("No result to share")
        val w = 1080
        val h = 1350
        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        val cv = Canvas(bmp)
        cv.drawColor(Color.WHITE)
        val band = Paint().apply { color = Color.parseColor("#146EF5") }
        cv.drawRect(0f, 0f, w.toFloat(), 520f, band)

        fun text(s: String, x: Float, y: Float, size: Float, color: Int, bold: Boolean = false) {
            val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                textSize = size
                typeface = if (bold) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            }
            cv.drawText(s, x, y, p)
        }

        text("Examcamp", 80f, 110f, 44f, Color.WHITE, bold = true)
        text(session.exam.subject.ifBlank { "Exam" }.take(40), 80f, 240f, 64f, Color.WHITE, bold = true)
        if (session.exam.topic.isNotBlank()) text(session.exam.topic.take(60), 80f, 320f, 40f, Color.WHITE)
        text("${String.format("%.1f", r.percentage)}%", 80f, 760f, 200f, Color.parseColor("#146EF5"), bold = true)
        text("Score", 84f, 840f, 44f, Color.parseColor("#5A5A5A"))
        text("${fmtNum(r.obtainedMarks)} / ${fmtNum(r.totalMarks)} marks", 80f, 960f, 52f, Color.parseColor("#080808"), bold = true)
        text("Correct ${r.correct}   Wrong ${r.wrong}   Unanswered ${r.unanswered}", 80f, 1040f, 42f, Color.parseColor("#5A5A5A"))
        text("examcamp", 80f, 1250f, 36f, Color.parseColor("#898989"))

        val dir = File(context.cacheDir, "exports").apply { mkdirs() }
        val file = File(dir, "result-card.png")
        FileOutputStream(file).use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bmp.recycle()
        return file
    }

    private fun fmtNum(v: Double): String = if (v % 1.0 == 0.0) v.toLong().toString() else String.format("%.2f", v)
}
