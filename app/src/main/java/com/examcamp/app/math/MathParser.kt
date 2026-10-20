package com.examcamp.app.math

/*
 * Native LaTeX subset for exam text. The website renders $...$ and $$...$$ with KaTeX; this is a
 * pure-Kotlin (no Android) parser for the commands that AI-written MCQs actually use, so the
 * rendering is fully native, works offline and needs no WebView.
 */

enum class SymStyle { ITALIC, UPRIGHT, BOLD }

sealed interface MathNode
data class Sym(val text: String, val style: SymStyle = SymStyle.UPRIGHT) : MathNode
data class Script(val base: MathNode, val sup: List<MathNode>?, val sub: List<MathNode>?) : MathNode
data class Frac(val num: List<MathNode>, val den: List<MathNode>, val binom: Boolean = false) : MathNode
data class Sqrt(val body: List<MathNode>, val index: List<MathNode>?) : MathNode
data class Line(val body: List<MathNode>, val top: Boolean = true) : MathNode
data class Group(val body: List<MathNode>) : MathNode

sealed interface Piece
data class PlainPiece(val text: String) : Piece
data class MathPiece(val nodes: List<MathNode>, val display: Boolean) : Piece

class MathParseException(message: String) : Exception(message)

object MathSegmenter {
    private data class Delim(val left: String, val right: String, val display: Boolean)

    private val delims = listOf(
        Delim("$$", "$$", true),
        Delim("$", "$", false),
        Delim("\\(", "\\)", false),
        Delim("\\[", "\\]", true),
    )

    private sealed interface Raw
    private data class RawText(val text: String) : Raw
    private data class RawMath(val math: String, val left: String, val right: String, val display: Boolean) : Raw

    /** Same delimiter rules as KaTeX auto-render (the website's renderMathIn): $$ first, then $, \( \), \[ \]. */
    fun split(text: String): List<Piece> {
        if (!text.contains('$') && !text.contains('\\')) return listOf(PlainPiece(text))
        var parts: List<Raw> = listOf(RawText(text))
        for (d in delims) {
            parts = parts.flatMap { if (it is RawText) splitAt(it.text, d) else listOf(it) }
        }
        val out = mutableListOf<Piece>()
        for (p in parts) {
            when (p) {
                is RawText -> if (p.text.isNotEmpty()) out.add(PlainPiece(p.text))
                is RawMath -> {
                    val nodes = try { MathParser(p.math).parse() } catch (e: MathParseException) { null }
                    if (nodes == null || nodes.isEmpty()) out.add(PlainPiece(p.left + p.math + p.right))
                    else out.add(MathPiece(nodes, p.display))
                }
            }
        }
        return mergePlain(out)
    }

    private fun mergePlain(list: List<Piece>): List<Piece> {
        val out = mutableListOf<Piece>()
        for (p in list) {
            val last = out.lastOrNull()
            if (p is PlainPiece && last is PlainPiece) out[out.size - 1] = PlainPiece(last.text + p.text) else out.add(p)
        }
        return out
    }

    private fun splitAt(text: String, d: Delim): List<Raw> {
        val out = mutableListOf<Raw>()
        var rest = text
        while (true) {
            val start = rest.indexOf(d.left)
            if (start < 0) break
            val end = findEnd(d.right, rest, start + d.left.length)
            if (end < 0) break
            if (start > 0) out.add(RawText(rest.substring(0, start)))
            out.add(RawMath(rest.substring(start + d.left.length, end), d.left, d.right, d.display))
            rest = rest.substring(end + d.right.length)
        }
        if (rest.isNotEmpty()) out.add(RawText(rest))
        return out
    }

    private fun findEnd(right: String, text: String, from: Int): Int {
        var depth = 0
        var i = from
        while (i < text.length) {
            if (depth == 0 && text.startsWith(right, i)) return i
            when (text[i]) {
                '\\' -> i++
                '{' -> depth++
                '}' -> depth--
            }
            i++
        }
        return -1
    }
}

class MathParser(private val src: String) {
    private var i = 0

    fun parse(): List<MathNode> {
        val r = parseSeq(top = true)
        if (i < src.length) throw MathParseException("unbalanced")
        return r
    }

    private fun parseSeq(top: Boolean): List<MathNode> {
        val out = mutableListOf<MathNode>()
        while (i < src.length) {
            if (src[i] == '}') {
                if (top) throw MathParseException("unmatched }")
                break
            }
            val atom = parseAtom() ?: continue
            var sup: List<MathNode>? = null
            var sub: List<MathNode>? = null
            while (true) {
                skipSpaces()
                if (i >= src.length) break
                val c = src[i]
                if (c == '^' && sup == null) { i++; sup = parseArg() }
                else if (c == '_' && sub == null) { i++; sub = parseArg() }
                else if (c == '\'' && sup == null) { i++; sup = listOf(Sym("′")) }
                else break
            }
            out.add(if (sup != null || sub != null) Script(atom, sup, sub) else atom)
        }
        return out
    }

    private fun skipSpaces() {
        while (i < src.length && src[i].isWhitespace()) i++
    }

    /** A script or command argument: {group} or one token. */
    private fun parseArg(): List<MathNode> {
        skipSpaces()
        if (i >= src.length) throw MathParseException("missing argument")
        if (src[i] == '{') {
            i++
            val body = parseSeq(top = false)
            if (i >= src.length || src[i] != '}') throw MathParseException("missing }")
            i++
            return body
        }
        val atom = parseAtom() ?: throw MathParseException("missing argument")
        return listOf(atom)
    }

    private fun readRawGroup(): String {
        skipSpaces()
        if (i >= src.length || src[i] != '{') throw MathParseException("expected {")
        i++
        val start = i
        var depth = 1
        while (i < src.length) {
            when (src[i]) {
                '\\' -> i++
                '{' -> depth++
                '}' -> { depth--; if (depth == 0) { val s = src.substring(start, i); i++; return s } }
            }
            i++
        }
        throw MathParseException("missing }")
    }

    private fun parseAtom(): MathNode? {
        val c = src[i]
        when {
            c.isWhitespace() -> { i++; return null }
            c == '{' -> return Group(parseArg())
            c == '^' || c == '_' -> return Sym("") // script with no base; the caller reads the script
            c == '\\' -> return parseCommand()
        }
        i++
        return when {
            c in 'a'..'z' || c in 'A'..'Z' -> Sym(c.toString(), SymStyle.ITALIC)
            c.isLetter() -> Sym(c.toString())
            c == '-' -> Sym(" − ")
            c == '+' || c == '=' || c == '<' || c == '>' -> Sym(" $c ")
            c == '*' -> Sym("∗")
            c == '~' -> Sym(" ")
            c == '&' -> Sym(" ")
            else -> Sym(c.toString())
        }
    }

    private fun parseCommand(): MathNode? {
        i++ // backslash
        if (i >= src.length) throw MathParseException("trailing backslash")
        val first = src[i]
        if (!first.isLetter()) {
            i++
            return when (first) {
                '\\' -> Sym(" ")
                ',', ':', ';', ' ' -> Sym(" ")
                '!' -> null
                '{', '}', '%', '$', '#', '_', '(', ')', '.', '[', ']' -> Sym(first.toString())
                '|' -> Sym("‖")
                else -> throw MathParseException("unknown \\$first")
            }
        }
        val start = i
        while (i < src.length && src[i].isLetter()) i++
        val name = src.substring(start, i)

        when (name) {
            "frac", "dfrac", "tfrac" -> return Frac(parseArg(), parseArg())
            "binom", "dbinom", "tbinom" -> return Frac(parseArg(), parseArg(), binom = true)
            "sqrt" -> {
                skipSpaces()
                var index: List<MathNode>? = null
                if (i < src.length && src[i] == '[') {
                    val end = src.indexOf(']', i)
                    if (end < 0) throw MathParseException("missing ]")
                    index = MathParser(src.substring(i + 1, end)).parse()
                    i = end + 1
                }
                return Sqrt(parseArg(), index)
            }
            "text", "textrm", "mathrm", "mbox", "textnormal", "operatorname" -> return Sym(readRawGroup())
            "textbf", "mathbf", "boldsymbol", "bm" -> return Sym(readRawGroup(), SymStyle.BOLD)
            "textit", "mathit", "mathcal", "mathscr", "mathsf", "mathtt" -> return Sym(readRawGroup(), SymStyle.ITALIC)
            "mathbb" -> {
                val raw = readRawGroup()
                return Sym(raw.map { BLACKBOARD[it] ?: it }.joinToString(""))
            }
            "overline", "bar" -> {
                val body = parseArg()
                return if (name == "bar" && body.size == 1 && body[0] is Sym && (body[0] as Sym).text.length == 1) {
                    (body[0] as Sym).let { Sym(it.text + "̄", it.style) }
                } else Line(body, top = true)
            }
            "underline" -> return Line(parseArg(), top = false)
            "vec", "hat", "tilde", "dot", "ddot", "widehat", "widetilde", "overrightarrow" -> {
                val body = parseArg()
                val mark = when (name) {
                    "vec", "overrightarrow" -> "⃗"
                    "hat", "widehat" -> "̂"
                    "tilde", "widetilde" -> "̃"
                    "dot" -> "̇"
                    else -> "̈"
                }
                val only = body.singleOrNull()
                return if (only is Sym && only.text.length == 1) Sym(only.text + mark, only.style)
                else Line(body, top = true)
            }
            "left", "right", "big", "Big", "bigg", "Bigg", "bigl", "bigr", "Bigl", "Bigr" -> {
                skipSpaces()
                if (i >= src.length) throw MathParseException("missing delimiter")
                if (src[i] == '.') { i++; return null }
                if (src[i] == '\\') {
                    i++
                    if (i >= src.length) throw MathParseException("missing delimiter")
                    if (src[i].isLetter()) {
                        val s = i
                        while (i < src.length && src[i].isLetter()) i++
                        return Sym(SYMBOLS[src.substring(s, i)] ?: throw MathParseException("bad delimiter"))
                    }
                    val d = src[i]; i++
                    return Sym(if (d == '|') "‖" else d.toString())
                }
                val d = src[i]; i++
                return Sym(d.toString())
            }
            "quad" -> return Sym(" ")
            "qquad" -> return Sym("  ")
            "displaystyle", "textstyle", "scriptstyle", "limits", "nolimits", "mathstrut", "relax" -> return null
        }
        OPERATORS[name]?.let { return Sym(it + " ") }
        GREEK_LOWER[name]?.let { return Sym(it, SymStyle.ITALIC) }
        GREEK_UPPER[name]?.let { return Sym(it) }
        SYMBOLS[name]?.let { return Sym(it) }
        throw MathParseException("unknown \\$name")
    }

    companion object {
        private val BLACKBOARD = mapOf('R' to 'ℝ', 'N' to 'ℕ', 'Z' to 'ℤ', 'Q' to 'ℚ', 'C' to 'ℂ', 'P' to 'ℙ', 'H' to 'ℍ')

        private val OPERATORS = listOf(
            "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos", "arctan", "sinh", "cosh", "tanh",
            "log", "ln", "lg", "exp", "lim", "max", "min", "sup", "inf", "det", "gcd", "deg", "dim", "ker", "arg", "mod",
        ).associateWith { it }

        private val GREEK_LOWER = mapOf(
            "alpha" to "α", "beta" to "β", "gamma" to "γ", "delta" to "δ", "epsilon" to "ϵ", "varepsilon" to "ε",
            "zeta" to "ζ", "eta" to "η", "theta" to "θ", "vartheta" to "ϑ", "iota" to "ι", "kappa" to "κ",
            "lambda" to "λ", "mu" to "μ", "nu" to "ν", "xi" to "ξ", "pi" to "π", "varpi" to "ϖ", "rho" to "ρ",
            "varrho" to "ϱ", "sigma" to "σ", "varsigma" to "ς", "tau" to "τ", "upsilon" to "υ", "phi" to "ϕ",
            "varphi" to "φ", "chi" to "χ", "psi" to "ψ", "omega" to "ω",
        )

        private val GREEK_UPPER = mapOf(
            "Gamma" to "Γ", "Delta" to "Δ", "Theta" to "Θ", "Lambda" to "Λ", "Xi" to "Ξ", "Pi" to "Π",
            "Sigma" to "Σ", "Upsilon" to "Υ", "Phi" to "Φ", "Psi" to "Ψ", "Omega" to "Ω",
        )

        private fun rel(s: String) = " $s "

        private val SYMBOLS = mapOf(
            "cdot" to "⋅", "times" to rel("×"), "div" to rel("÷"), "pm" to rel("±"), "mp" to rel("∓"),
            "leq" to rel("≤"), "le" to rel("≤"), "geq" to rel("≥"), "ge" to rel("≥"), "neq" to rel("≠"), "ne" to rel("≠"),
            "approx" to rel("≈"), "equiv" to rel("≡"), "sim" to rel("∼"), "simeq" to rel("≃"), "cong" to rel("≅"),
            "propto" to rel("∝"), "ll" to rel("≪"), "gg" to rel("≫"), "lt" to rel("<"), "gt" to rel(">"),
            "infty" to "∞", "rightarrow" to rel("→"), "to" to rel("→"), "leftarrow" to rel("←"),
            "Rightarrow" to rel("⇒"), "Leftarrow" to rel("⇐"), "leftrightarrow" to rel("↔"), "Leftrightarrow" to rel("⇔"),
            "longrightarrow" to rel("⟶"), "longleftarrow" to rel("⟵"), "implies" to rel("⟹"), "iff" to rel("⟺"),
            "mapsto" to rel("↦"), "uparrow" to "↑", "downarrow" to "↓", "rightleftharpoons" to rel("⇌"),
            "leftrightarrows" to rel("⇄"), "rightharpoonup" to "⇀", "circ" to "∘", "degree" to "°", "partial" to "∂",
            "in" to rel("∈"), "notin" to rel("∉"), "ni" to rel("∋"), "subset" to rel("⊂"), "subseteq" to rel("⊆"),
            "supset" to rel("⊃"), "supseteq" to rel("⊇"), "cup" to rel("∪"), "cap" to rel("∩"), "setminus" to rel("∖"),
            "emptyset" to "∅", "varnothing" to "∅", "forall" to "∀", "exists" to "∃", "nexists" to "∄",
            "neg" to "¬", "lnot" to "¬", "wedge" to rel("∧"), "land" to rel("∧"), "vee" to rel("∨"), "lor" to rel("∨"),
            "mid" to rel("∣"), "nmid" to rel("∤"), "parallel" to rel("∥"), "perp" to rel("⊥"), "angle" to "∠",
            "triangle" to "△", "square" to "□", "therefore" to rel("∴"), "because" to rel("∵"), "nabla" to "∇",
            "oplus" to rel("⊕"), "otimes" to rel("⊗"), "langle" to "⟨", "rangle" to "⟩", "lfloor" to "⌊", "rfloor" to "⌋",
            "lceil" to "⌈", "rceil" to "⌉", "sum" to "∑", "prod" to "∏", "int" to "∫", "oint" to "∮",
            "ldots" to "…", "dots" to "…", "cdots" to "⋯", "vdots" to "⋮", "hbar" to "ℏ", "ell" to "ℓ",
            "Re" to "ℜ", "Im" to "ℑ", "prime" to "′", "aleph" to "ℵ", "star" to "⋆", "bullet" to "∙",
            "lbrace" to "{", "rbrace" to "}", "vert" to "|", "Vert" to "‖", "backslash" to "\\",
        )
    }
}

/** Plain-unicode form of a math tree: used inside scripts, for tiny layouts, and for accessibility labels. */
object MathFlatten {
    private val SUP = mapOf(
        '0' to '⁰', '1' to '¹', '2' to '²', '3' to '³', '4' to '⁴', '5' to '⁵', '6' to '⁶', '7' to '⁷', '8' to '⁸', '9' to '⁹',
        '+' to '⁺', '-' to '⁻', '=' to '⁼', '(' to '⁽', ')' to '⁾', 'n' to 'ⁿ', 'i' to 'ⁱ',
    )
    private val SUB = mapOf(
        '0' to '₀', '1' to '₁', '2' to '₂', '3' to '₃', '4' to '₄', '5' to '₅', '6' to '₆', '7' to '₇', '8' to '₈', '9' to '₉',
        '+' to '₊', '-' to '₋', '=' to '₌', '(' to '₍', ')' to '₎',
    )

    fun flatten(nodes: List<MathNode>): String {
        val sb = StringBuilder()
        nodes.forEach { append(sb, it) }
        return sb.toString()
    }

    private fun mapScript(text: String, table: Map<Char, Char>, fallbackPrefix: String): String {
        val trimmed = text.replace(" ", "").replace("−", "-")
        return if (trimmed.all { it in table }) trimmed.map { table.getValue(it) }.joinToString("") else "$fallbackPrefix($trimmed)"
    }

    private fun append(sb: StringBuilder, n: MathNode) {
        when (n) {
            is Sym -> sb.append(n.text)
            is Group -> n.body.forEach { append(sb, it) }
            is Script -> {
                append(sb, n.base)
                n.sub?.let { sb.append(mapScript(flatten(it), SUB, "_")) }
                n.sup?.let { sb.append(mapScript(flatten(it), SUP, "^")) }
            }
            is Frac -> {
                if (n.binom) sb.append("(").append(flatten(n.num)).append(" ").append(flatten(n.den)).append(")")
                else sb.append("(").append(flatten(n.num)).append(")⁄(").append(flatten(n.den)).append(")")
            }
            is Sqrt -> {
                n.index?.let { sb.append(flatten(it)) }
                sb.append("√(").append(flatten(n.body)).append(")")
            }
            is Line -> sb.append(flatten(n.body))
        }
    }
}
