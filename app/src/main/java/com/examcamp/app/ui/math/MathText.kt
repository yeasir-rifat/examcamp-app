package com.examcamp.app.ui.math

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.examcamp.app.math.Frac
import com.examcamp.app.math.Group
import com.examcamp.app.math.Line
import com.examcamp.app.math.MathFlatten
import com.examcamp.app.math.MathNode
import com.examcamp.app.math.MathPiece
import com.examcamp.app.math.MathSegmenter
import com.examcamp.app.math.Piece
import com.examcamp.app.math.PlainPiece
import com.examcamp.app.math.Script
import com.examcamp.app.math.Sqrt
import com.examcamp.app.math.Sym
import com.examcamp.app.math.SymStyle

/*
 * Text with $...$ / $$...$$ math, drawn natively (the website uses KaTeX).
 *  - Text without fractions or roots stays ONE Text, so it wraps like normal text. Sub and
 *    superscripts, Greek letters and symbols are inline spans.
 *  - Fractions, roots and over/underlines are real layouts. Text containing them flows word by
 *    word, with those parts placed inline; $$ display math gets its own centred line.
 */

private fun isComplex(n: MathNode): Boolean = when (n) {
    is Frac, is Sqrt, is Line -> true
    is Group -> n.body.any { isComplex(it) }
    is Script -> n.sup != null && n.sub != null // both scripts: stacked like KaTeX
    is Sym -> false
}

private fun fontSizeOf(style: TextStyle): Float = if (style.fontSize.isSpecified) style.fontSize.value else 16f

private fun AnnotatedString.Builder.appendNodes(nodes: List<MathNode>, size: Float, depth: Int) {
    nodes.forEach { appendNode(it, size, depth) }
}

private fun AnnotatedString.Builder.appendNode(n: MathNode, size: Float, depth: Int) {
    when (n) {
        is Sym -> {
            val span = when (n.style) {
                SymStyle.ITALIC -> SpanStyle(fontStyle = FontStyle.Italic)
                SymStyle.BOLD -> SpanStyle(fontWeight = FontWeight.Bold)
                SymStyle.UPRIGHT -> SpanStyle()
            }
            withStyle(span) { append(n.text) }
        }
        is Group -> appendNodes(n.body, size, depth)
        is Script -> {
            appendNode(n.base, size, depth)
            n.sub?.let { script(it, size, depth, up = false) }
            n.sup?.let { script(it, size, depth, up = true) }
        }
        // Complex shapes only reach here inside a script or a tiny layout: use their plain form.
        is Frac, is Sqrt, is Line -> append(MathFlatten.flatten(listOf(n)))
    }
}

private fun AnnotatedString.Builder.script(body: List<MathNode>, size: Float, depth: Int, up: Boolean) {
    val span = if (depth == 0) {
        SpanStyle(fontSize = (size * 0.7f).sp, baselineShift = BaselineShift(if (up) 0.5f else -0.25f))
    } else {
        SpanStyle(fontSize = (size * 0.5f).sp)
    }
    withStyle(span) { appendNodes(body, size, depth + 1) }
}

private fun AnnotatedString.Builder.appendPieces(pieces: List<Piece>, size: Float) {
    pieces.forEach {
        when (it) {
            is PlainPiece -> append(it.text)
            is MathPiece -> appendNodes(it.nodes, size, 0)
        }
    }
}

private fun splitWords(a: AnnotatedString): List<AnnotatedString> {
    val out = mutableListOf<AnnotatedString>()
    var start = -1
    for (idx in a.text.indices) {
        val ch = a.text[idx]
        if (ch == ' ' || ch == '\n') {
            if (start >= 0) { out.add(a.subSequence(start, idx)); start = -1 }
        } else if (start < 0) start = idx
    }
    if (start >= 0) out.add(a.subSequence(start, a.text.length))
    return out
}

private sealed interface FlowItem
private class Word(val text: AnnotatedString) : FlowItem
private class Block(val nodes: List<MathNode>, val display: Boolean) : FlowItem

private fun buildItems(pieces: List<Piece>, size: Float, prefix: String?, prefixColor: Color): List<FlowItem> {
    val items = mutableListOf<FlowItem>()
    var cur = AnnotatedString.Builder()
    fun flush() {
        splitWords(cur.toAnnotatedString()).forEach { items.add(Word(it)) }
        cur = AnnotatedString.Builder()
    }
    if (prefix != null) {
        cur.withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Bold)) { append(prefix) }
        cur.append(" ")
    }
    for (p in pieces) {
        when {
            p is PlainPiece -> cur.append(p.text)
            p is MathPiece && (p.display || p.nodes.any { isComplex(it) }) -> {
                flush()
                items.add(Block(p.nodes, p.display))
            }
            p is MathPiece -> cur.appendNodes(p.nodes, size, 0)
        }
    }
    flush()
    return items
}

/**
 * [prefix] (the question number) is drawn first, bold and in [prefixColor].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MathText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    prefix: String? = null,
    prefixColor: Color = color,
) {
    val pieces = remember(text) { MathSegmenter.split(text) }
    val size = fontSizeOf(style)
    val needsFlow = pieces.any { it is MathPiece && (it.display || it.nodes.any { n -> isComplex(n) }) }
    if (!needsFlow) {
        val annotated = remember(pieces, size, prefix, prefixColor) {
            buildAnnotatedString {
                if (prefix != null) {
                    withStyle(SpanStyle(color = prefixColor, fontWeight = FontWeight.Bold)) { append(prefix) }
                    append(" ")
                }
                appendPieces(pieces, size)
            }
        }
        Text(annotated, modifier = modifier, color = color, style = style)
        return
    }
    val items = remember(pieces, size, prefix, prefixColor) { buildItems(pieces, size, prefix, prefixColor) }
    val gap = with(LocalDensity.current) { (size * 0.3f).sp.toDp() }
    FlowRow(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        items.forEach { item ->
            when (item) {
                is Word -> Text(item.text, modifier = Modifier.align(Alignment.CenterVertically), color = color, style = style)
                is Block ->
                    if (item.display) {
                        Box(Modifier.fillMaxWidth().padding(vertical = 4.dp), contentAlignment = Alignment.Center) {
                            MathRow(item.nodes, style, color)
                        }
                    } else {
                        Box(Modifier.align(Alignment.CenterVertically)) { MathRow(item.nodes, style, color) }
                    }
            }
        }
    }
}

// ---------- math layout ----------

private sealed interface Seg
private class Run(val nodes: List<MathNode>) : Seg
private class Complex(val node: MathNode) : Seg

private fun segments(nodes: List<MathNode>): List<Seg> {
    val out = mutableListOf<Seg>()
    var run = mutableListOf<MathNode>()
    for (n in nodes) {
        if (isComplex(n)) {
            if (run.isNotEmpty()) { out.add(Run(run)); run = mutableListOf() }
            out.add(Complex(n))
        } else run.add(n)
    }
    if (run.isNotEmpty()) out.add(Run(run))
    return out
}

private fun TextStyle.noLineHeight() = copy(lineHeight = TextUnit.Unspecified)
private fun TextStyle.scaled(f: Float) = copy(fontSize = (fontSizeOf(this) * f).sp, lineHeight = TextUnit.Unspecified)

@Composable
private fun MathRow(nodes: List<MathNode>, style: TextStyle, color: Color, modifier: Modifier = Modifier) {
    val segs = remember(nodes) { segments(nodes) }
    val size = fontSizeOf(style)
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        segs.forEach { seg ->
            when (seg) {
                is Run -> {
                    val a = remember(seg) { buildAnnotatedString { appendNodes(seg.nodes, size, 0) } }
                    Text(a, color = color, style = style.noLineHeight())
                }
                is Complex -> ComplexView(seg.node, style, color)
            }
        }
    }
}

@Composable
private fun ComplexView(n: MathNode, style: TextStyle, color: Color) {
    when (n) {
        is Frac -> FracView(n, style, color)
        is Sqrt -> SqrtView(n, style, color)
        is Line -> LineView(n, style, color)
        is Group -> MathRow(n.body, style, color)
        is Script -> StackedView(n, style, color)
        is Sym -> Text(n.text, color = color, style = style.noLineHeight())
    }
}

@Composable
private fun FracView(f: Frac, style: TextStyle, color: Color) {
    val small = style.scaled(0.85f)
    if (f.binom) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("(", color = color, style = style.scaled(1.6f))
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MathRow(f.num, small, color)
                MathRow(f.den, small, color)
            }
            Text(")", color = color, style = style.scaled(1.6f))
        }
        return
    }
    Column(
        Modifier.padding(horizontal = 2.dp).width(IntrinsicSize.Max),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        MathRow(f.num, small, color)
        Box(Modifier.padding(vertical = 1.dp).fillMaxWidth().height(1.dp).background(color))
        MathRow(f.den, small, color)
    }
}

@Composable
private fun SqrtView(s: Sqrt, style: TextStyle, color: Color) {
    val stroke = 1.dp
    Row(verticalAlignment = Alignment.CenterVertically) {
        s.index?.let { MathRow(it, style.scaled(0.6f), color, Modifier.padding(end = 1.dp)) }
        Text("√", color = color, style = style.scaled(1.25f))
        Box(
            Modifier
                .drawBehind {
                    drawLine(color, Offset(0f, 0f), Offset(size.width, 0f), strokeWidth = stroke.toPx())
                }
                .padding(top = 2.dp, start = 1.dp, end = 2.dp),
        ) { MathRow(s.body, style, color) }
    }
}

@Composable
private fun LineView(l: Line, style: TextStyle, color: Color) {
    Box(
        Modifier
            .drawBehind {
                val y = if (l.top) 0f else size.height
                drawLine(color, Offset(0f, y), Offset(size.width, y), strokeWidth = 1.dp.toPx())
            }
            .padding(vertical = 1.dp),
    ) { MathRow(l.body, style, color) }
}

/** A base with both a superscript and a subscript, e.g. a sum or integral with limits. */
@Composable
private fun StackedView(s: Script, style: TextStyle, color: Color) {
    val small = style.scaled(0.7f)
    Row(verticalAlignment = Alignment.CenterVertically) {
        MathRow(listOf(s.base), style, color)
        Column {
            s.sup?.let { MathRow(it, small, color) }
            s.sub?.let { MathRow(it, small, color) }
        }
    }
}
