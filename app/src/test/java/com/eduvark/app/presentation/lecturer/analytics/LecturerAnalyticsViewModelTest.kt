package com.eduvark.app.presentation.lecturer.analytics

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.GetTopicAnalyticsUseCase
import com.eduvark.app.testutil.FakeAnalyticsRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LecturerAnalyticsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeAnalyticsRepository = FakeAnalyticsRepository()) =
        LecturerAnalyticsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("topicId" to "topic-raw")),
            getTopicAnalyticsUseCase = GetTopicAnalyticsUseCase(repository),
        )

    @Test
    fun `loads analytics and applies filters`() = runTest {
        val repository = FakeAnalyticsRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.analytics?.respondentCount)
        // Field kompatibilitas API tetap ada di model domain.
        assertEquals(1, viewModel.state.value.analytics?.notRespondedCount)
        assertEquals(2, viewModel.state.value.respondentCount)
        assertTrue(viewModel.state.value.aggregationDefinition.orEmpty().isNotBlank())

        viewModel.selectCategory(VarkType.VISUAL)
        advanceUntilIdle()
        assertEquals(VarkType.VISUAL, viewModel.state.value.selectedCategory)
        assertEquals(VarkType.VISUAL, repository.lastCategory)

        viewModel.selectStudent("student-1")
        advanceUntilIdle()
        assertEquals("student-1", viewModel.state.value.selectedStudentId)
        assertEquals("student-1", repository.lastStudentId)
    }

    @Test
    fun `filters students by stored origin category not by last login vark`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Tanpa filter seluruh populasi kelas tampil, termasuk yang belum
        // merespons (vark null), supaya null tidak hilang dari daftar.
        assertEquals(3, viewModel.state.value.visibleStudents.size)
        assertTrue(viewModel.state.value.visibleStudents.any { it.vark == null })

        val visual = viewModel.state.value
        assertEquals(2, visual.categoryStats.first { it.category == VarkType.VISUAL }.respondents)
        assertEquals(1, visual.categoryStats.first { it.category == VarkType.AURAL }.respondents)
        assertEquals(0, visual.categoryStats.first { it.category == VarkType.READ_WRITE }.respondents)

        viewModel.selectCategory(VarkType.VISUAL)
        advanceUntilIdle()
        assertEquals(
            listOf("student-1", "student-2"),
            viewModel.state.value.visibleStudents.map { it.studentId },
        )

        viewModel.selectCategory(VarkType.READ_WRITE)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.visibleStudents.isEmpty())
    }

    @Test
    fun `distinguishes stored zero percent from missing feedback`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val state = viewModel.state.value

        assertEquals(2, state.respondentCount)

        val visual = state.categoryStats.first { it.category == VarkType.VISUAL }
        // 75 dan 0 sama-sama dihitung sebagai respons.
        assertEquals(2, visual.respondents)
        assertEquals(37.5, visual.mean!!, 0.001)

        val readWrite = state.categoryStats.first { it.category == VarkType.READ_WRITE }
        // Tidak ada nilai Read/Write: mean null (bukan 0.0).
        assertEquals(0, readWrite.respondents)
        assertNull(readWrite.mean)

        val zeroStudent = state.allStudents.first { it.studentId == "student-2" }
        assertEquals(0, zeroStudent.valueFor(VarkType.VISUAL))

        val missingStudent = state.allStudents.first { it.studentId == "student-3" }
        assertNull(missingStudent.vark)
        assertTrue(missingStudent.originCategories.isEmpty())
        assertNull(missingStudent.valueFor(VarkType.VISUAL))
    }

    @Test
    fun `keeps kinesthetic metrics separate from subjective feedback`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.kinestheticAssessedCount)
        assertEquals(90.0, viewModel.state.value.kinestheticMeanScore!!, 0.001)

        // Filter kategori asal tidak mengubah metrik Kinesthetic objektif.
        viewModel.selectCategory(VarkType.VISUAL)
        advanceUntilIdle()
        assertEquals(2, viewModel.state.value.kinestheticAssessedCount)
        assertEquals(90.0, viewModel.state.value.kinestheticMeanScore!!, 0.001)

        // Field kompatibilitas API dipertahankan di model domain, walau tidak
        // dirender UI.
        assertEquals(1, viewModel.state.value.analytics?.kinesthetic?.notSubmittedCount)
    }

    @Test
    fun `ui state does not expose not responded or not submitted totals`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()

        // Regresi C1-05: state UI tidak boleh mengekspos total "belum merespons"
        // maupun "belum mengumpulkan" (field hanya dipertahankan di API/domain).
        val forbidden = setOf(
            "getNotRespondedCount",
            "getKinestheticNotSubmittedCount",
            "getNotSubmittedCount",
        )
        val exposedGetters = LecturerAnalyticsUiState::class.java.declaredMethods
            .filter { it.parameterTypes.isEmpty() && it.name.startsWith("get") }
            .map { it.name }
            .toSet()
        assertEquals(
            "State UI tidak boleh mengekspos total belum merespons/mengumpulkan",
            emptySet<String>(),
            forbidden.intersect(exposedGetters),
        )

        val forbiddenFields = setOf(
            "notRespondedCount",
            "kinestheticNotSubmittedCount",
            "notSubmittedCount",
        )
        val exposedFields = LecturerAnalyticsUiState::class.java.declaredFields
            .map { it.name }
            .toSet()
        assertEquals(emptySet<String>(), forbiddenFields.intersect(exposedFields))

        // Data yang diizinkan tetap tersedia untuk UI.
        assertTrue(viewModel.state.value.respondentCount > 0)
        assertTrue(viewModel.state.value.aggregationDefinition.orEmpty().isNotBlank())
        assertEquals(2, viewModel.state.value.kinestheticAssessedCount)
        assertEquals(90.0, viewModel.state.value.kinestheticMeanScore!!, 0.001)
    }

    @Test
    fun `api and domain models keep compatibility totals`() = runTest {
        val viewModel = viewModel()
        advanceUntilIdle()
        val analytics = requireNotNull(viewModel.state.value.analytics)

        // Kontrak API/domain wajib utuh: DTO/repository/model tidak dihapus.
        assertEquals(1, analytics.notRespondedCount)
        assertEquals(1, analytics.kinesthetic.notSubmittedCount)
        assertEquals(2, analytics.kinesthetic.assessedCount)
        assertEquals(90.0, analytics.kinesthetic.meanScore!!, 0.001)

        assertEquals(1, FakeAnalyticsRepository().analytics.notRespondedCount)
    }

    @Test
    fun `analytics screen source does not render total not responded or not submitted`() {
        val screen = analyticsSource("LecturerAnalyticsScreen.kt")
        val forbiddenTokens = listOf(
            "notRespondedCount",
            "notSubmittedCount",
            "p4_analytics_not_responded_count",
            "p4_analytics_kinesthetic_compat_count",
        )
        val remaining = forbiddenTokens.filter { screen.contains(it) }
        assertEquals("UI Analytics masih merender total terlarang: $remaining", emptyList<String>(), remaining)

        // Data yang diizinkan/diwajibkan tetap dirender.
        val requiredTokens = listOf(
            "lec_analytics_respondents_count",
            "p4_analytics_respondent_count",
            "p4_analytics_aggregation_definition",
            "p4_analytics_zero_vs_null_note",
            "p4_analytics_kinesthetic_final_count",
            "lec_analytics_respondent_note",
        )
        val missing = requiredTokens.filterNot { screen.contains(it) }
        assertEquals("UI Analytics kehilangan data yang diwajibkan: $missing", emptyList<String>(), missing)
    }

    private fun analyticsSource(fileName: String): String {
        val relative = "app/src/main/java/com/eduvark/app/presentation/lecturer/analytics/$fileName"
        var dir: File? = File("").absoluteFile
        while (dir != null) {
            val candidate = File(dir, relative)
            if (candidate.isFile) return candidate.readText()
            dir = dir.parentFile
        }
        throw AssertionError("Tidak menemukan source Analytics '$relative' dari ${File("").absolutePath}")
    }
}
