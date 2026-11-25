package com.eduvark.app.ui.format

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Counter style rule: `0` stays single digit, `1..9` gets one leading zero,
 * `10` and above keep all digits.
 */
class CounterFormatTest {

    @Test
    fun zeroStaysSingleDigit() {
        assertEquals("0", formatCounter(0))
    }

    @Test
    fun oneToNineArePaddedToTwoDigits() {
        assertEquals("01", formatCounter(1))
        assertEquals("02", formatCounter(2))
        assertEquals("09", formatCounter(9))
    }

    @Test
    fun tenAndAboveKeepAllDigits() {
        assertEquals("10", formatCounter(10))
        assertEquals("25", formatCounter(25))
        assertEquals("99", formatCounter(99))
        assertEquals("100", formatCounter(100))
        assertEquals("1000", formatCounter(1000))
    }

    @Test
    fun handlesFullAcceptanceMatrix() {
        val expected = mapOf(
            0 to "0",
            1 to "01",
            2 to "02",
            9 to "09",
            10 to "10",
            25 to "25",
            99 to "99",
            100 to "100",
        )
        expected.forEach { (raw, display) ->
            assertEquals("counter $raw", display, formatCounter(raw))
        }
    }

    @Test
    fun formattingDoesNotAlterRawValues() {
        val raw = listOf(0, 1, 9, 10, 99, 100)
        raw.forEach { formatCounter(it) }
        assertEquals(listOf(0, 1, 9, 10, 99, 100), raw)
    }
}
