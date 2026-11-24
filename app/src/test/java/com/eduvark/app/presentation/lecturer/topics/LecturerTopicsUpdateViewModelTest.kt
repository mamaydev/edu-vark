package com.eduvark.app.presentation.lecturer.topics

import com.eduvark.app.domain.model.TopicStatus
import com.eduvark.app.domain.usecase.CreateTopicUseCase
import com.eduvark.app.domain.usecase.DeleteTopicUseCase
import com.eduvark.app.domain.usecase.GetTopicsUseCase
import com.eduvark.app.domain.usecase.PublishTopicUseCase
import com.eduvark.app.domain.usecase.ReorderTopicsUseCase
import com.eduvark.app.domain.usecase.UnpublishTopicUseCase
import com.eduvark.app.domain.usecase.UpdateTopicUseCase
import com.eduvark.app.testutil.FakeTopicRepository
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
class LecturerTopicsUpdateViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: FakeTopicRepository) = LecturerTopicsViewModel(
        getTopicsUseCase = GetTopicsUseCase(repository),
        createTopicUseCase = CreateTopicUseCase(repository),
        updateTopicUseCase = UpdateTopicUseCase(repository),
        publishTopicUseCase = PublishTopicUseCase(repository),
        unpublishTopicUseCase = UnpublishTopicUseCase(repository),
        deleteTopicUseCase = DeleteTopicUseCase(repository),
        reorderTopicsUseCase = ReorderTopicsUseCase(repository),
    )

    @Test
    fun `editing an existing topic updates title and description on the same id`() = runTest {
        val repository = FakeTopicRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopic(
            topicId = "topic-1",
            title = "Normalisasi Basis Data",
            description = "Tujuan baru topik.",
            published = true,
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.topics.size)
        val updated = viewModel.state.value.topics.first()
        assertEquals("topic-1", updated.id)
        assertEquals("Normalisasi Basis Data", updated.title)
        assertEquals("Tujuan baru topik.", updated.description)
        // Publikasi yang sudah ada tidak berubah.
        assertEquals(TopicStatus.PUBLISHED, updated.status)
        assertNull(viewModel.state.value.actionError)
    }

    @Test
    fun `edited values survive reopening the editor`() = runTest {
        val repository = FakeTopicRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopic("topic-1", "Judul Baru", "Deskripsi Baru", published = true)
        advanceUntilIdle()

        // Membuka ulang editor membaca state.topics (sumber data yang sama).
        val reopened = viewModel.state.value.topics.first { it.id == "topic-1" }
        assertEquals("Judul Baru", reopened.title)
        assertEquals("Deskripsi Baru", reopened.description)
    }

    @Test
    fun `unpublishing keeps the edited content and only changes status`() = runTest {
        val repository = FakeTopicRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopic("topic-1", "Judul Draf", "Deskripsi Draf", published = false)
        advanceUntilIdle()

        val updated = viewModel.state.value.topics.first()
        assertEquals("Judul Draf", updated.title)
        assertEquals("Deskripsi Draf", updated.description)
        assertEquals(TopicStatus.DRAFT, updated.status)
    }

    @Test
    fun `empty title is rejected before persisting`() = runTest {
        val repository = FakeTopicRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateTopic("topic-1", "   ", "Deskripsi", published = true)
        advanceUntilIdle()

        assertEquals("Judul topik wajib diisi.", viewModel.state.value.actionError)
        assertEquals("Topik Basis Data", repository.topics.first().title)
    }

    @Test
    fun `long topic list keeps backend order after reorder`() = runTest {
        val topics = (1..55).map { index ->
            sampleTopic(id = "topic-" + index, title = "Topik " + index)
        }
        val repository = FakeTopicRepository(topics = topics)
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals(55, viewModel.state.value.topics.size)

        viewModel.move(index = 0, delta = 1)
        advanceUntilIdle()

        assertEquals(55, repository.lastReorder?.size)
        assertEquals(viewModel.state.value.topics.map { it.id }, repository.lastReorder)
    }
}
