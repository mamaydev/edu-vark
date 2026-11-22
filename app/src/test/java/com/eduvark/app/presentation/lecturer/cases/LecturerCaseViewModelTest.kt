package com.eduvark.app.presentation.lecturer.cases

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.usecase.GetLecturerCaseUseCase
import com.eduvark.app.domain.usecase.UpsertCaseUseCase
import com.eduvark.app.testutil.FakeCaseRepository
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
class LecturerCaseViewModelTest {

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
    fun `loads and saves case`() = runTest {
        val repository = FakeCaseRepository()
        val viewModel = LecturerCaseViewModel(
            savedStateHandle = SavedStateHandle(mapOf("topicId" to "topic-1")),
            getLecturerCaseUseCase = GetLecturerCaseUseCase(repository),
            upsertCaseUseCase = UpsertCaseUseCase(repository),
        )
        advanceUntilIdle()

        viewModel.save(
            theory = "Teori baru",
            instructions = "Instruksi baru",
            deadlineAt = "2026-12-02T16:59:59Z",
        )
        advanceUntilIdle()

        assertEquals("Instruksi baru", viewModel.state.value.caseStudy?.instructions)
        assertEquals("Teori baru", viewModel.state.value.caseStudy?.theory)
    }
}
