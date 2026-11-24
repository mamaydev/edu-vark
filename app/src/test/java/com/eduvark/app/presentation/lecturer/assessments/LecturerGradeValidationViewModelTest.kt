package com.eduvark.app.presentation.lecturer.assessments

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.domain.usecase.CreateAssessmentUseCase
import com.eduvark.app.domain.usecase.GetSubmissionUseCase
import com.eduvark.app.testutil.FakeAssessmentRepository
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
import org.junit.Before
import org.junit.Test

/**
 * FR-043 / AT-15: nilai adalah integer 0–100, komentar opsional maksimal
 * 1.000 karakter (code point). Input tidak valid tidak boleh diubah diam-diam.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LecturerGradeValidationViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: FakeAssessmentRepository) = LecturerGradeViewModel(
        savedStateHandle = SavedStateHandle(mapOf("submissionId" to "submission-1")),
        getSubmissionUseCase = GetSubmissionUseCase(repository),
        createAssessmentUseCase = CreateAssessmentUseCase(repository),
    )

    @Test
    fun `comment over one thousand code points is rejected without saving`() = runTest {
        val repository = FakeAssessmentRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.save(score = 90, comment = "\uD83D\uDE00".repeat(1_001))
        advanceUntilIdle()

        assertEquals("Komentar maksimal 1.000 karakter.", viewModel.state.value.saveError)
        assertEquals(SubmissionStatus.MENUNGGU_PENILAIAN, viewModel.state.value.detail?.status)
        assertNull(viewModel.state.value.detail?.score)
    }

    @Test
    fun `one thousand emoji comment counts as one thousand code points`() = runTest {
        val repository = FakeAssessmentRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        val comment = "\uD83D\uDE00".repeat(1_000)
        viewModel.save(score = 90, comment = comment)
        advanceUntilIdle()

        assertNull(viewModel.state.value.saveError)
        assertEquals(SubmissionStatus.SUDAH_DINILAI, viewModel.state.value.detail?.status)
        assertEquals(comment, viewModel.state.value.detail?.comment)
    }

    @Test
    fun `empty comment is optional`() = runTest {
        val repository = FakeAssessmentRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.save(score = 70, comment = "   ")
        advanceUntilIdle()

        assertNull(viewModel.state.value.saveError)
        assertEquals(SubmissionStatus.SUDAH_DINILAI, viewModel.state.value.detail?.status)
        assertNull(viewModel.state.value.detail?.comment)
    }

    @Test
    fun `score below zero is rejected without saving`() = runTest {
        val repository = FakeAssessmentRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.save(score = -1, comment = null)
        advanceUntilIdle()

        assertEquals("Nilai harus 0–100.", viewModel.state.value.saveError)
        assertEquals(SubmissionStatus.MENUNGGU_PENILAIAN, viewModel.state.value.detail?.status)
    }

    @Test
    fun `score above one hundred is rejected without saving`() = runTest {
        val repository = FakeAssessmentRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.save(score = 101, comment = null)
        advanceUntilIdle()

        assertEquals("Nilai harus 0–100.", viewModel.state.value.saveError)
        assertEquals(SubmissionStatus.MENUNGGU_PENILAIAN, viewModel.state.value.detail?.status)
    }

    @Test
    fun `boundary scores zero and one hundred are accepted`() = runTest {
        val repository = FakeAssessmentRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.save(score = 0, comment = null)
        advanceUntilIdle()
        assertEquals(0, viewModel.state.value.detail?.score)
        assertEquals(SubmissionStatus.SUDAH_DINILAI, viewModel.state.value.detail?.status)

        val secondRepository = FakeAssessmentRepository()
        val secondViewModel = createViewModel(secondRepository)
        advanceUntilIdle()
        secondViewModel.save(score = 100, comment = null)
        advanceUntilIdle()
        assertEquals(100, secondViewModel.state.value.detail?.score)
        assertEquals(SubmissionStatus.SUDAH_DINILAI, secondViewModel.state.value.detail?.status)
    }
}
