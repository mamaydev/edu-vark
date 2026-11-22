package com.eduvark.app.presentation.student.materials

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.GetFeedbackUseCase
import com.eduvark.app.domain.usecase.GetMaterialsUseCase
import com.eduvark.app.domain.usecase.GetMeUseCase
import com.eduvark.app.domain.usecase.GetTopicUseCase
import com.eduvark.app.domain.usecase.SaveFeedbackUseCase
import com.eduvark.app.testutil.FakeAuthRepository
import com.eduvark.app.testutil.FakeFeedbackRepository
import com.eduvark.app.testutil.FakeTopicRepository
import com.eduvark.app.testutil.sampleMaterial
import com.eduvark.app.testutil.sampleSession
import com.eduvark.app.testutil.sampleTopic
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

@OptIn(ExperimentalCoroutinesApi::class)
class StudentMaterialsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(
        topicRepository: FakeTopicRepository = FakeTopicRepository(),
        feedbackRepository: FakeFeedbackRepository = FakeFeedbackRepository(),
        activeVark: VarkType? = null,
    ): StudentMaterialsViewModel = StudentMaterialsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("topicId" to "topic-1")),
        getTopicUseCase = GetTopicUseCase(topicRepository),
        getMaterialsUseCase = GetMaterialsUseCase(topicRepository),
        getMeUseCase = GetMeUseCase(
            FakeAuthRepository(sampleSession(activeVark = activeVark)),
        ),
        getFeedbackUseCase = GetFeedbackUseCase(feedbackRepository),
        saveFeedbackUseCase = SaveFeedbackUseCase(feedbackRepository),
    )

    @Test
    fun `loads topic and materials`() = runTest {
        val repository = FakeTopicRepository(
            topics = listOf(sampleTopic(id = "topic-1")),
            materials = listOf(sampleMaterial(topicId = "topic-1")),
        )
        val viewModel = createViewModel(topicRepository = repository)
        advanceUntilIdle()

        assertEquals("topic-1", viewModel.state.value.topic?.id)
        assertEquals(1, viewModel.state.value.materials.size)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `loads and saves feedback`() = runTest {
        val feedback = FakeFeedbackRepository(value = 75)
        val viewModel = createViewModel(
            topicRepository = FakeTopicRepository(
                topics = listOf(sampleTopic(id = "topic-1")),
                materials = listOf(sampleMaterial(topicId = "topic-1")),
            ),
            feedbackRepository = feedback,
            activeVark = VarkType.VISUAL,
        )
        advanceUntilIdle()

        assertEquals(75, viewModel.state.value.feedback)

        viewModel.saveFeedback(80)
        advanceUntilIdle()

        assertEquals(80, viewModel.state.value.feedback)
        assertEquals(80, feedback.value)
    }

    @Test
    fun `surfaces material not available`() = runTest {
        val message = "materi belum tersedia, harap hubungi dosen terlebih dahulu"
        val viewModel = createViewModel(
            topicRepository = FakeTopicRepository(
                failure = AppFailure.Api("MATERIAL_NOT_AVAILABLE", message, 404),
            ),
        )
        advanceUntilIdle()

        assertEquals(message, viewModel.state.value.error)
    }
}
