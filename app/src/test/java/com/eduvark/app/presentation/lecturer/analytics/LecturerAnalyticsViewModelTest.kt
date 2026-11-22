package com.eduvark.app.presentation.lecturer.analytics

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.GetTopicAnalyticsUseCase
import com.eduvark.app.testutil.FakeAnalyticsRepository
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

    @Test
    fun `loads analytics and applies filters`() = runTest {
        val repository = FakeAnalyticsRepository()
        val viewModel = LecturerAnalyticsViewModel(
            savedStateHandle = SavedStateHandle(mapOf("topicId" to "topic-1")),
            getTopicAnalyticsUseCase = GetTopicAnalyticsUseCase(repository),
        )
        advanceUntilIdle()

        assertEquals(2, viewModel.state.value.analytics?.respondentCount)
        assertEquals(1, viewModel.state.value.analytics?.notRespondedCount)

        viewModel.selectCategory(VarkType.VISUAL)
        advanceUntilIdle()
        assertEquals(VarkType.VISUAL, viewModel.state.value.selectedCategory)
        assertEquals(VarkType.VISUAL, repository.lastCategory)

        viewModel.selectStudent("student-1")
        advanceUntilIdle()
        assertEquals("student-1", viewModel.state.value.selectedStudentId)
        assertEquals("student-1", repository.lastStudentId)
    }
}
