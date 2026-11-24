package com.eduvark.app.core.text

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class P5TextRulesTest {

    @Test
    fun `counts Unicode code points not UTF-16 units`() {
        assertEquals(3, P5TextRules.codePointCount("abc"))
        // Emoji astral: 1 code point tetapi 2 unit UTF-16.
        assertEquals(1, P5TextRules.codePointCount("\uD83D\uDE00"))
        assertEquals(2, P5TextRules.codePointCount("\uD83D\uDE00\uD83D\uDE00"))
        // Combining mark: 2 code points.
        assertEquals(2, P5TextRules.codePointCount("e\u0301"))
    }

    @Test
    fun `answer length trims leading and trailing spaces`() {
        assertEquals(0, P5TextRules.answerLength("   "))
        assertEquals(1, P5TextRules.answerLength("  x  "))
        assertEquals(1, P5TextRules.answerLength(" \uD83D\uDE00 "))
    }

    @Test
    fun `ten thousand emoji count as ten thousand code points`() {
        val emoji = "\uD83D\uDE00".repeat(10_000)
        assertEquals(20_000, emoji.length)
        assertEquals(10_000, P5TextRules.answerLength(emoji))
        assertTrue(P5TextRules.isAnswerLengthValid(emoji))
    }

    @Test
    fun `answer boundary is one to ten thousand code points after trim`() {
        assertFalse(P5TextRules.isAnswerLengthValid(""))
        assertFalse(P5TextRules.isAnswerLengthValid("    "))
        assertTrue(P5TextRules.isAnswerLengthValid("x"))
        assertTrue(P5TextRules.isAnswerLengthValid("x".repeat(10_000)))
        assertFalse(P5TextRules.isAnswerLengthValid("x".repeat(10_001)))
        assertTrue(P5TextRules.isAnswerLengthValid(" " + "x".repeat(10_000) + " "))
    }

    @Test
    fun `comment boundary uses the same code point unit`() {
        assertTrue(P5TextRules.isCommentLengthValid(""))
        assertTrue(P5TextRules.isCommentLengthValid("\uD83D\uDE00".repeat(1_000)))
        assertFalse(P5TextRules.isCommentLengthValid("\uD83D\uDE00".repeat(1_001)))
        assertEquals(1_000, P5TextRules.commentLength(" " + "\uD83D\uDE00".repeat(1_000) + " "))
    }

    @Test
    fun `ecma trim removes BOM and NBSP like JavaScript trim`() {
        // String.trim() Kotlin TIDAK membuang karakter ini, backend JS membuangnya.
        assertEquals("abc", P5TextRules.ecmaTrim("\uFEFFabc\uFEFF"))
        assertEquals("abc", P5TextRules.ecmaTrim("\u00A0abc\u00A0"))
        assertEquals("abc", P5TextRules.ecmaTrim("\u2007abc\u202F"))
        assertEquals("abc", P5TextRules.ecmaTrim("\u1680abc\u205F"))
        // Tidak ada whitespace di dalam; hanya tepi yang dibuang.
        assertEquals("a b", P5TextRules.ecmaTrim("\u00A0a b\uFEFF"))
    }

    @Test
    fun `ecma trim keeps characters Kotlin trims but JavaScript keeps`() {
        // U+001C..U+001F: Character.isWhitespace() Kotlin true, ECMAScript false.
        assertEquals("\u001Cabc", P5TextRules.ecmaTrim("\u001Cabc"))
        assertEquals("abc\u001F", P5TextRules.ecmaTrim("abc\u001F"))
        assertEquals(4, P5TextRules.answerLength("\u001Cabc"))
        // Jawaban hanya berisi U+001C tidak dianggap kosong (JS trim tidak membuangnya).
        assertTrue(P5TextRules.isAnswerLengthValid("\u001C"))
    }

    @Test
    fun `answer length boundary is consistent with ECMAScript trim`() {
        // BOM & NBSP di tepi tidak menambah panjang (backend JS trim membuangnya).
        assertEquals(1, P5TextRules.answerLength("\uFEFFx\uFEFF"))
        assertEquals(1, P5TextRules.answerLength("\u00A0x\u00A0"))
        assertEquals(10_000, P5TextRules.answerLength("\uFEFF" + "x".repeat(10_000) + "\u00A0"))
        assertTrue(
            P5TextRules.isAnswerLengthValid("\uFEFF" + "x".repeat(10_000) + "\uFEFF"),
        )
        // 10.001 code point tetap ditolak walau dibungkus BOM di kedua tepi.
        assertFalse(P5TextRules.isAnswerLengthValid("\uFEFF" + "x".repeat(10_001) + "\uFEFF"))
        assertEquals(10_000, P5TextRules.answerLength("\uFEFF" + "\uD83D\uDE00".repeat(10_000)))
        // Hanya BOM/NBSP tidak menghasilkan jawaban sah (setelah trim kosong).
        assertFalse(P5TextRules.isAnswerLengthValid("\uFEFF\u00A0"))
    }

    @Test
    fun `normalize helpers return ECMAScript-trimmed text`() {
        assertEquals("jawaban", P5TextRules.normalizeAnswerText("\uFEFF jawaban \u00A0"))
        assertEquals("komentar", P5TextRules.normalizeCommentText("\u00A0komentar\uFEFF"))
        assertEquals("", P5TextRules.normalizeAnswerText("\uFEFF\u00A0 \u3000"))
        assertEquals(1_000, P5TextRules.commentLength("\uFEFF" + "\uD83D\uDE00".repeat(1_000) + "\u00A0"))
        assertFalse(P5TextRules.isCommentLengthValid("\u001C" + "x".repeat(1_000)))
    }

    @Test
    fun `score input is a plain integer zero to one hundred`() {
        assertEquals(0, P5TextRules.parseScore("0"))
        assertEquals(100, P5TextRules.parseScore("100"))
        assertEquals(85, P5TextRules.parseScore(" 85 "))
        assertEquals(7, P5TextRules.parseScore("007"))

        // Tidak boleh dibulatkan/dipotong diam-diam menjadi angka lain.
        assertNull(P5TextRules.parseScore("-1"))
        assertNull(P5TextRules.parseScore("101"))
        assertNull(P5TextRules.parseScore("85.5"))
        assertNull(P5TextRules.parseScore("7e1"))
        assertNull(P5TextRules.parseScore("1 0"))
        assertNull(P5TextRules.parseScore("abc"))
        assertNull(P5TextRules.parseScore(""))
        assertNull(P5TextRules.parseScore("   "))
    }
}
