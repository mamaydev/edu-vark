package com.eduvark.app.presentation.lecturer.analytics

import android.graphics.Bitmap
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.printToString
import androidx.lifecycle.SavedStateHandle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.eduvark.app.domain.usecase.GetTopicAnalyticsUseCase
import com.eduvark.app.testutil.FakeAnalyticsRepository
import com.eduvark.app.testutil.sampleAnalyticsForUi
import com.eduvark.app.ui.theme.EduVarkTheme
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Regresi layar nyata C1-05 / FR-029–033 / AT-05/24/34: layar Analytics dosen
 * TIDAK boleh merender total "belum merespons" maupun "belum mengumpulkan",
 * tetapi WAJIB tetap menampilkan jumlah responden, definisi agregasi, pembeda
 * 0% vs belum merespons, filter kategori asal, dan jumlah+rerata nilai final
 * Kinesthetic.
 *
 * ViewModel di-inject langsung (parameter `viewModel` pada composable) sehingga
 * tes tidak bergantung pada Hilt/backend. Bukti tambahan: screenshot + dump
 * semantik disimpan ke external files dir perangkat.
 */
@RunWith(AndroidJUnit4::class)
class LecturerAnalyticsTotalsNotRenderedTest {

    @get:Rule
    val rule = createComposeRule()

    private val evidenceDir: File by lazy {
        File(
            InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null),
            "analytics-c1-05",
        ).apply { mkdirs() }
    }

    private fun viewModel(repository: FakeAnalyticsRepository): LecturerAnalyticsViewModel =
        LecturerAnalyticsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("topicId" to "topic-1")),
            getTopicAnalyticsUseCase = GetTopicAnalyticsUseCase(repository),
        )

    private fun compose(viewModel: LecturerAnalyticsViewModel) {
        rule.setContent {
            EduVarkTheme {
                LecturerAnalyticsScreen(onBack = {}, viewModel = viewModel)
            }
        }
    }

    private fun exists(text: String, substring: Boolean = false): Boolean =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()

    private fun count(text: String, substring: Boolean = false): Int =
        rule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().size

    private fun waitFor(text: String, substring: Boolean = false, timeoutMs: Long = 30_000) {
        rule.waitUntil(timeoutMs) { exists(text, substring) }
    }

    private fun capture(name: String) {
        rule.waitForIdle()
        Thread.sleep(600)
        val bitmap: Bitmap = InstrumentationRegistry.getInstrumentation().uiAutomation
            .takeScreenshot()
        val png = File(evidenceDir, "$name.png")
        png.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        File(evidenceDir, "$name-semantics.txt").writeText(rule.onRoot().printToString())
        println("EVIDENCE_SCREENSHOT $name ${png.absolutePath}")
        println("EVIDENCE_SEMANTICS $name ${File(evidenceDir, "$name-semantics.txt").absolutePath}")
    }

    @Test
    fun analyticsMainHidesTotalNotRespondedAndNotSubmitted() {
        compose(viewModel(FakeAnalyticsRepository()))
        waitFor("Responden: 2")

        // Dilarang: total "belum merespons" dan "belum mengumpulkan".
        assertEquals(0, count("Belum merespons:", substring = true))
        assertEquals(0, count("Field kompatibilitas API", substring = true))
        assertEquals(0, count("belum mengirim", substring = true))
        assertFalse(exists("Belum merespons: 1", substring = true))

        // Diizinkan/diwajibkan tetap tampil.
        assertTrue(exists("Responden: 2", substring = true))
        assertTrue(exists("Definisi agregasi", substring = true))
        assertTrue(exists("0% dihitung sebagai respons", substring = true))
        assertTrue(exists("nilai final tersimpan", substring = true))
        assertTrue(exists("Tidak digabungkan dengan feedback V/A/R.", substring = true))
        // Eyebrow dirender uppercase oleh komponen desain.
        assertTrue(exists("KINESTHETIC", substring = true))

        capture("analytics-main")
    }

    @Test
    fun feedbackListHidesTotalNotRespondedButKeepsPerStudentStatus() {
        compose(viewModel(FakeAnalyticsRepository()))
        waitFor("Lihat feedback mahasiswa")
        rule.onNodeWithText("Lihat feedback mahasiswa").performScrollTo().performClick()
        waitFor("3 mahasiswa pada topik ini")

        // Total "Belum merespons: N" dilarang.
        assertEquals(0, count("Belum merespons:", substring = true))
        // Status per mahasiswa (konsep 0% vs belum merespons) tetap tampil.
        assertTrue(exists("Belum merespons", substring = true))
        assertTrue(exists("Visual 75%", substring = true))
        assertTrue(exists("Visual 0%", substring = true))
        assertTrue(exists("0% adalah feedback valid", substring = true))
        assertTrue(exists("2 responden", substring = true))

        capture("analytics-feedback-list")
    }

    @Test
    fun emptyAnalyticsHidesTotalsButKeepsRespondentAndKinesthetic() {
        val repository = FakeAnalyticsRepository(
            analytics = sampleAnalyticsForUi().copy(
                respondentCount = 0,
                notRespondedCount = 3,
                mean = null,
                students = listOf(
                    sampleAnalyticsForUi().students.single { it.studentId == "student-3" },
                ),
            ),
        )
        compose(viewModel(repository))
        waitFor("0 responden")

        assertEquals(0, count("Belum merespons:", substring = true))
        assertFalse(exists("Belum merespons: 3", substring = true))
        assertEquals(0, count("Field kompatibilitas API", substring = true))

        // Pendefinisi konsep dan metrik yang diizinkan tetap tampil.
        assertTrue(exists("Belum merespons tidak dihitung sebagai 0%", substring = true))
        assertTrue(exists("Definisi agregasi", substring = true))
        assertTrue(exists("Nilai Kinesthetic", substring = true))

        capture("analytics-empty")
    }
}
