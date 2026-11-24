package com.eduvark.app.presentation.p5

import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eduvark.app.R
import com.eduvark.app.core.text.P5TextRules
import com.eduvark.app.ui.theme.EduVarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation ringan (tanpa backend) untuk aturan UI Paket 5:
 * C1-03 empty vs error state, FR-016 teks persis, C1-04 code point,
 * dan FR-043 validasi nilai manual.
 */
@RunWith(AndroidJUnit4::class)
class P5ValidationInstrumentedTest {

    @get:Rule
    val composeRule = createComposeRule()

    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun publishedTopicWithoutMaterialsShowsExactIndonesianMessage() {
        val emptyText = context.getString(R.string.p5_case_supporting_empty)
        val errorText = context.getString(R.string.p5_case_materials_error)

        assertEquals(
            "materi belum tersedia, harap hubungi dosen terlebih dahulu",
            emptyText,
        )
        // Error state harus berbeda dari empty state (C1-03).
        assertNotEquals(emptyText, errorText)

        composeRule.setContent {
            EduVarkTheme {
                Column {
                    Text(text = emptyText)
                    Text(text = errorText)
                }
            }
        }

        composeRule.onNodeWithText(emptyText).assertIsDisplayed()
    }

    @Test
    fun questionCounterUsesUnicodeCodePointsIncludingEmoji() {
        assertEquals(1, P5TextRules.answerLength("\uD83D\uDE00"))
        assertEquals(2, P5TextRules.answerLength("\uD83D\uDE00\uD83D\uDE00"))
        assertEquals(10_000, P5TextRules.answerLength("\uD83D\uDE00".repeat(10_000)))
        assertTrue(P5TextRules.isAnswerLengthValid("\uD83D\uDE00"))
        assertFalse(P5TextRules.isAnswerLengthValid("\uD83D\uDE00".repeat(10_001)))
        assertFalse(P5TextRules.isAnswerLengthValid("   "))
    }

    @Test
    fun manualGradeInputIsNotSilentlyAltered() {
        // 0 dan 100 valid.
        assertEquals(0, P5TextRules.parseScore("0"))
        assertEquals(100, P5TextRules.parseScore("100"))
        // Di luar rentang / bukan integer ditolak, bukan di-clamp/truncate.
        assertNull(P5TextRules.parseScore("-1"))
        assertNull(P5TextRules.parseScore("101"))
        assertNull(P5TextRules.parseScore("85.5"))
        assertNull(P5TextRules.parseScore("1e2"))
        assertNull(P5TextRules.parseScore(""))
        // Komentar maksimal 1.000 code point.
        assertTrue(P5TextRules.isCommentLengthValid("\uD83D\uDE00".repeat(1_000)))
        assertFalse(P5TextRules.isCommentLengthValid("\uD83D\uDE00".repeat(1_001)))
    }
}
