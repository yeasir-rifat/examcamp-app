package com.examcamp.app.math

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MathParserTest {

    private fun flat(src: String) = MathFlatten.flatten(MathParser(src).parse())

    // ---------- parser + flattening ----------

    @Test fun superscriptDigitsBecomeUnicode() = assertEquals("x²", flat("x^2"))

    @Test fun subscriptDigitsBecomeUnicode() = assertEquals("a₁", flat("a_1"))

    @Test fun fractionIsFlattenedWithAFractionSlash() = assertEquals("(a)⁄(b)", flat("\\frac{a}{b}"))

    @Test fun squareRoot() = assertEquals("√(x)", flat("\\sqrt{x}"))

    @Test fun nestedGroupsAreBalanced() = assertEquals("(a²)⁄(b)", flat("\\frac{a^{2}}{b}"))

    @Test fun anUnmatchedClosingBraceIsAnError() {
        var failed = false
        try { MathParser("x}").parse() } catch (e: MathParseException) { failed = true }
        assertTrue(failed)
    }

    @Test fun aScriptWithoutAnArgumentIsAnError() {
        var failed = false
        try { MathParser("x^").parse() } catch (e: MathParseException) { failed = true }
        assertTrue(failed)
    }

    // ---------- splitting text and math (KaTeX auto-render delimiters) ----------

    @Test fun plainTextIsOnePiece() {
        val pieces = MathSegmenter.split("No formulas here.")
        assertEquals(listOf<Piece>(PlainPiece("No formulas here.")), pieces)
    }

    @Test fun inlineDollarMathIsSplitOut() {
        val pieces = MathSegmenter.split("Area is \$x^2\$ cm")
        assertEquals(3, pieces.size)
        assertEquals(PlainPiece("Area is "), pieces[0])
        val math = pieces[1] as MathPiece
        assertFalse(math.display)
        assertEquals("x²", MathFlatten.flatten(math.nodes))
        assertEquals(PlainPiece(" cm"), pieces[2])
    }

    @Test fun doubleDollarIsDisplayMath() {
        val pieces = MathSegmenter.split("\$\$\\frac{a}{b}\$\$")
        assertEquals(1, pieces.size)
        assertTrue((pieces[0] as MathPiece).display)
    }

    @Test fun parenAndBracketDelimitersWork() {
        val inline = MathSegmenter.split("\\(x^2\\)")
        assertFalse((inline[0] as MathPiece).display)
        val display = MathSegmenter.split("\\[x^2\\]")
        assertTrue((display[0] as MathPiece).display)
    }

    @Test fun aLoneDollarSignStaysText() {
        assertEquals(listOf<Piece>(PlainPiece("It costs \$5")), MathSegmenter.split("It costs \$5"))
    }

    @Test fun mathThatCannotBeParsedFallsBackToTheOriginalText() {
        val pieces = MathSegmenter.split("a \$x^\$ b") // "x^" has no script argument
        assertEquals(listOf<Piece>(PlainPiece("a \$x^\$ b")), pieces)
    }
}
