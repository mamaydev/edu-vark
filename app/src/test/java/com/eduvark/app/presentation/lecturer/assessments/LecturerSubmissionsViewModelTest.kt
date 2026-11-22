package com.eduvark.app.presentation.lecturer.assessments

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.usecase.ListCaseSubmissionsUseCase
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
class LecturerSubmissionsViewModelTest {

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
    fun `loads case submissions`() = runTest {
        val viewModel = LecturerSubmissionsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("caseId" to "case-1")),
            listCaseSubmissionsUseCase = ListCaseSubmissionsUseCase(
                FakeAssessmentRepository(),
            ),
        )
        viewModel.load()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.submissions.size)
        assertEquals("submission-1", viewModel.state.value.submissions.first().submissionId)
    }
}
