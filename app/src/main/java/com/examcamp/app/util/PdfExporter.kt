package com.examcamp.app.util

import android.content.Context
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.style.StyleSpan
import android.text.TextPaint
import com.examcamp.app.data.Session
import com.examcamp.app.i18n.I18n
import com.examcamp.app.i18n.Lang
import com.examcamp.app.ui.screens.toBengaliDigits
import com.examcamp.app.ui.screens.usesLocalizedLabels
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Builds PDF files natively with android.graphics.pdf.PdfDocument. Text is drawn as text, not as images,
 * so the files stay sharp and small. Layout follows the website's PDF settings: A4 / Letter / Legal, 10 pt,
 * line height 1.3, dark text on white. Options use ক/খ/গ/ঘ for Bengali and English exams, as on the website.
 */
object PdfExporter {
    enum class Paper(val w: Int, val h: Int) { A4(595, 842), Letter(612, 792), Legal(612, 1008) }

    private const val MARGIN = 48f
    private const val FONT_SIZE = 10f
    private const val LINE = 1.3f
    private val labelsEn = listOf("A", "B", "C", "D")
    private val labelsBn = listOf("ক", "খ", "গ", "ঘ")

    private val headerLabelsBn = mapOf(
        "Obtained Marks" to "প্রাপ্ত নম্বরঃ", "Correct" to "সঠিকঃ", "Wrong" to "ভুলঃ",
        "Skipped" to "বাদ দেওয়াঃ", "Negative Marks" to "নেগেটিভ নম্বরঃ", "Percentage" to "শতকরাঃ",
    )

    /**
     * The website's Download / Print paper (see buildPageHeader + buildQuestionBlock in app.js): a result header on the
     * first page, every question with the correct option in green and a wrong pick in red, an optional explanation
     * under each question (Explanation Sheet), and "Page 01/05" in the footer.
     */
    fun markedSheet(context: Context, session: Session, lang: Lang, includeExplanations: Boolean, paper: Paper = Paper.A4): File {
        val counter = PaperWriter(paper, footerTotal = null)
        layoutMarked(counter, session, includeExplanations)
        val total = counter.discard()
        val w = PaperWriter(paper, footerTotal = total)
        layoutMarked(w, session, includeExplanations)
        val exam = session.exam
        val suffix = if (includeExplanations) "explanation-sheet" else "answer-sheet"
        return w.save(context, "${slug(exam.subject)}-$suffix.pdf")
    }

    private fun layoutMarked(w: PaperWriter, session: Session, includeExplanations: Boolean) {
        val r = session.result ?: throw IllegalStateException("No result to export")
        val exam = session.exam
        val bn = usesLocalizedLabels(exam.language)
        val labels = if (bn) labelsBn else labelsEn
        val ink = Color.parseColor("#111111")
        val green = Color.parseColor("#0A7D43")
        val red = Color.parseColor("#C62828")
        fun label(key: String) = if (bn) headerLabelsBn.getValue(key) else "$key:"
        val negative = Math.round(exam.negativeMarking * r.wrong * 100) / 100.0

        w.text(exam.subject.ifBlank { "Exam" }, size = 16f, bold = true)
        if (exam.topic.isNotBlank()) w.text(exam.topic, size = 12f)
        w.gap(4f)
        w.text(
            listOf(
                "${label("Obtained Marks")} ${fmt(r.obtainedMarks, bn)}",
                "${label("Correct")} ${num(r.correct, bn)}",
                "${label("Wrong")} ${num(r.wrong, bn)}",
                "${label("Skipped")} ${num(r.unanswered, bn)}",
                "${label("Negative Marks")} ${fmt(negative, bn)}",
                "${label("Percentage")} ${fmt(r.percentage, bn)}%",
            ).joinToString("  |  "),
            size = 9f,
        )
        w.gap(12f)
        exam.questions.forEachIndexed { i, q ->
            val picked = session.answers[q.id]
            w.text("${num(i + 1, bn)}. ${q.question}", bold = true, color = ink)
            q.options.forEachIndexed { j, opt ->
                val color = when {
                    j == q.correctAnswer -> green
                    j == picked -> red // a wrong pick (the correct one was handled above)
                    else -> ink
                }
                val strong = color != ink
                w.text("${labels.getOrElse(j) { "" }}. $opt", indent = 16f, bold = strong, color = color)
            }
            if (includeExplanations && q.explanation.isNotBlank()) {
                val head = if (bn) "ব্যাখ্যাঃ " else "Explanation: "
                val sb = SpannableStringBuilder(head).append(q.explanation)
                sb.setSpan(StyleSpan(Typeface.BOLD), 0, head.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                w.text(sb, indent = 16f, size = 9f, color = Color.parseColor("#444444"))
            }
            w.gap(8f)
        }
    }

    /** Question paper, with the answer key on its own page at the end. */
    fun questionPaper(context: Context, session: Session, lang: Lang, paper: Paper = Paper.A4): File {
        val exam = session.exam
        val bn = usesLocalizedLabels(exam.language)
        val labels = if (bn) labelsBn else labelsEn
        val w = PaperWriter(paper)
        w.text(exam.subject.ifBlank { "Exam" }, size = 16f, bold = true)
        if (exam.topic.isNotBlank()) w.text(exam.topic, size = 12f)
        w.text(
            "${I18n.get(lang, "pdf.questions")}: ${num(exam.questions.size, bn)}   " +
                "${I18n.get(lang, "pdf.marks")}: ${fmt(exam.marksPerQuestion * exam.questions.size, bn)}   " +
                "${I18n.get(lang, "pdf.time")}: ${num(exam.duration, bn)} ${I18n.get(lang, "pdf.minutes")}",
            size = 9f,
        )
        w.gap(14f)
        exam.questions.forEachIndexed { i, q ->
            w.text("${num(i + 1, bn)}. ${q.question}", bold = true)
            q.options.forEachIndexed { j, opt -> w.text("${labels.getOrElse(j) { "" }}. $opt", indent = 16f) }
            w.gap(8f)
        }
        w.newPage()
        w.text("${I18n.get(lang, "pdf.answerKey")}: ${exam.subject}", size = 13f, bold = true)
        w.gap(8f)
        exam.questions.forEachIndexed { i, q ->
            w.text("${num(i + 1, bn)}. ${labels.getOrElse(q.correctAnswer) { "-" }}", bold = true)
            if (q.explanation.isNotBlank()) w.text(q.explanation, indent = 16f, size = 9f)
            w.gap(4f)
        }
        return w.save(context, "${slug(exam.subject)}-question-paper.pdf")
    }

    /** Result report: summary, then every question with the member's answer, the correct answer and the explanation. */
    fun resultReport(context: Context, session: Session, lang: Lang, paper: Paper = Paper.A4): File {
        val r = session.result ?: throw IllegalStateException("No result to export")
        val exam = session.exam
        val bn = usesLocalizedLabels(exam.language)
        val labels = if (bn) labelsBn else labelsEn
        val w = PaperWriter(paper)
        w.text(exam.subject.ifBlank { "Exam" }, size = 16f, bold = true)
        if (exam.topic.isNotBlank()) w.text(exam.topic, size = 12f)
        w.text("${I18n.get(lang, "pdf.date")}: ${dateText(r.submittedAt)}", size = 9f)
        w.gap(10f)
        w.text(
            "${I18n.get(lang, "pdf.score")}: ${fmt(r.obtainedMarks, bn)} / ${fmt(r.totalMarks, bn)}   (${fmt(r.percentage, bn)}%)",
            size = 12f,
            bold = true,
        )
        w.text(
            "${I18n.get(lang, "pdf.correct")}: ${num(r.correct, bn)}   " +
                "${I18n.get(lang, "pdf.wrong")}: ${num(r.wrong, bn)}   " +
                "${I18n.get(lang, "pdf.unanswered")}: ${num(r.unanswered, bn)}",
            size = 10f,
        )
        w.gap(14f)
        exam.questions.forEachIndexed { i, q ->
            val picked = session.answers[q.id]
            val mark = when {
                picked == null -> I18n.get(lang, "pdf.notAnswered")
                picked == q.correctAnswer -> "✓"
                else -> "✗"
            }
            w.text("${num(i + 1, bn)}. ${q.question}   $mark", bold = true)
            q.options.forEachIndexed { j, opt -> w.text("${labels.getOrElse(j) { "" }}. $opt", indent = 16f) }
            val yours = picked?.let { labels.getOrElse(it) { "-" } } ?: I18n.get(lang, "pdf.notAnswered")
            w.text("${I18n.get(lang, "pdf.yourAnswer")}: $yours   ${I18n.get(lang, "pdf.correctAnswer")}: ${labels.getOrElse(q.correctAnswer) { "-" }}", indent = 16f, size = 9f)
            if (q.explanation.isNotBlank()) w.text(q.explanation, indent = 16f, size = 9f)
            w.gap(8f)
        }
        return w.save(context, "${slug(exam.subject)}-result.pdf")
    }

    private fun num(v: Int, bn: Boolean): String = if (bn) toBengaliDigits(v.toString()) else v.toString()

    private fun fmt(v: Double, bn: Boolean): String {
        val s = if (v % 1.0 == 0.0) v.toLong().toString() else String.format(Locale.US, "%.2f", v)
        return if (bn) toBengaliDigits(s) else s
    }

    private fun dateText(ms: Long): String =
        if (ms <= 0L) "" else SimpleDateFormat("d MMM yyyy", Locale.US).format(Date(ms))

    private fun slug(s: String): String =
        s.lowercase().replace(Regex("[^a-z0-9]+"), "-").trim('-').ifBlank { "exam" }

    /** Lays out text top to bottom and starts a new page when the next block does not fit. */
    private class PaperWriter(private val paper: Paper, private val footerTotal: Int? = null) {
        private val doc = PdfDocument()
        private var pageNo = 0
        private var page: PdfDocument.Page? = null
        private var y = MARGIN
        private val bottom get() = paper.h - MARGIN
        private val width get() = paper.w - 2 * MARGIN

        init {
            newPage()
        }

        private fun drawFooter(p: PdfDocument.Page) {
            val total = footerTotal ?: return
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                color = Color.parseColor("#666666")
                typeface = Typeface.SERIF
                textSize = 8f
                textAlign = Paint.Align.CENTER
            }
            fun pad(n: Int) = n.toString().padStart(2, '0')
            p.canvas.drawText("Page ${pad(pageNo)}/${pad(total)}", paper.w / 2f, paper.h - 24f, paint)
        }

        /** Closes the writer without saving and returns how many pages it laid out (first pass of a two-pass layout). */
        fun discard(): Int {
            page?.let { doc.finishPage(it) }
            page = null
            doc.close()
            return pageNo
        }

        fun newPage() {
            page?.let { drawFooter(it); doc.finishPage(it) }
            pageNo++
            val info = PdfDocument.PageInfo.Builder(paper.w, paper.h, pageNo).create()
            val p = doc.startPage(info)
            p.canvas.drawColor(Color.WHITE)
            page = p
            y = MARGIN
        }

        fun gap(px: Float) {
            y += px
            if (y > bottom) newPage()
        }

        fun text(str: CharSequence, size: Float = FONT_SIZE, bold: Boolean = false, indent: Float = 0f, color: Int = Color.parseColor("#111111")) {
            val paint = TextPaint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                typeface = Typeface.SERIF
                isFakeBoldText = bold
                textSize = size
            }
            val layout = StaticLayout.Builder
                .obtain(str, 0, str.length, paint, maxOf(1, (width - indent).toInt()))
                .setAlignment(Layout.Alignment.ALIGN_NORMAL)
                .setLineSpacing(0f, LINE)
                .setIncludePad(false)
                .build()
            if (y + layout.height > bottom && y > MARGIN) newPage()
            val canvas = page!!.canvas
            canvas.save()
            canvas.translate(MARGIN + indent, y)
            layout.draw(canvas)
            canvas.restore()
            y += layout.height + size * 0.4f
        }

        fun save(context: Context, name: String): File {
            page?.let { drawFooter(it); doc.finishPage(it) }
            page = null
            val dir = File(context.cacheDir, "exports").apply { mkdirs() }
            val file = File(dir, name)
            FileOutputStream(file).use { doc.writeTo(it) }
            doc.close()
            return file
        }
    }
}
