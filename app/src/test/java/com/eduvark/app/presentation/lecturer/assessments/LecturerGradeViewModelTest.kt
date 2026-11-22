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
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LecturerGradeViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `loads and saves final assessment`() = runTest {
        val repository = FakeAssessmentRepository()
        val viewModel = LecturerGradeViewModel(
            savedStateHandle = SavedStateHandle(mapOf("submissionId" to "submission-1")),
            getSubmissionUseCase = GetSubmissionUseCase(repository),
            createAssessmentUseCase = CreateAssessmentUseCase(repository),
        )
        advanceUntilIdle()

        assertEquals(SubmissionStatus.MENUNGGU_PENILAIAN, viewModel.state.value.detail?.status)

        viewModel.save(score = 90, comment = "Baik.")
        advanceUntilIdle()

        assertEquals(SubmissionStatus.SUDAH_DINILAI, viewModel.state.value.detail?.status)
        assertEquals(90, viewModel.state.value.detail?.score)
        assertEquals("Baik.", viewModel.state.value.detail?.comment)
    }
}
