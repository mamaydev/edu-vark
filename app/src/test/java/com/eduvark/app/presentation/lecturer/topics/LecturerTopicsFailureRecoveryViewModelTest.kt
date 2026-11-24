package com.eduvark.app.presentation.lecturer.topics

import com.eduvark.app.domain.error.AppFailure
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * Remediasi cross-check P5 (reorder/hapus): kegagalan HTTP dari `Response<Unit>`
 * harus muncul sebagai pesan gagal, dan reorder optimistis harus dipulihkan ke
 * urutan server (reload), bukan dibiarkan tampak tersimpan.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class LecturerTopicsFailureRecoveryViewModelTest {

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

    private fun topics(count: Int) = (1..count).map { index ->
        sampleTopic(id = "topic-$index", title = "Topik $index")
    }

    @Test
    fun `rejected reorder shows failure and restores server order`() = runTest {
        val repository = FakeTopicRepository(topics = topics(55))
        repository.reorderFailure = AppFailure.Api(
            code = "TOPIC_ORDER_INCOMPLETE",
            message = "Daftar urutan harus memuat seluruh topik aktif.",
            statusCode = 422,
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val serverOrder = viewModel.state.value.topics.map { it.id }

        viewModel.move(index = 0, delta = 1)
        advanceUntilIdle()

        assertEquals(
            "Daftar urutan harus memuat seluruh topik aktif.",
            viewModel.state.value.actionError,
        )
        // Urutan optimistis dibatalkan; state kembali mengikuti server.
        assertEquals(serverOrder, viewModel.state.value.topics.map { it.id })
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `successful reorder keeps the new order for more than 50 topics`() = runTest {
        val repository = FakeTopicRepository(topics = topics(55))
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val movedId = viewModel.state.value.topics[1].id

        viewModel.move(index = 0, delta = 1)
        advanceUntilIdle()

        assertEquals(55, repository.lastReorder?.size)
        assertEquals(movedId, viewModel.state.value.topics.first().id)
        assertNull(viewModel.state.value.actionError)
    }

    @Test
    fun `delete rejected because of submission keeps topic and shows message`() = runTest {
        val repository = FakeTopicRepository()
        repository.deleteFailure = AppFailure.Api(
            code = "TOPIC_HAS_ACADEMIC_DATA",
            message = "Topik sudah memiliki submission/nilai. Gunakan unpublish/arsip.",
            statusCode = 409,
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.delete("topic-1")
        advanceUntilIdle()

        assertEquals(
            "Topik sudah memiliki submission/nilai. Gunakan unpublish/arsip.",
            viewModel.state.value.actionError,
        )
        assertEquals(1, viewModel.state.value.topics.size)
        assertEquals("topic-1", viewModel.state.value.topics.first().id)
        assertFalse(viewModel.state.value.isSaving)
    }

    @Test
    fun `successful delete removes topic from the list`() = runTest {
        val repository = FakeTopicRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.delete("topic-1")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.topics.isEmpty())
        assertNull(viewModel.state.value.actionError)
    }

    @Test
    fun `clearing description persists as null after reopening`() = runTest {
        val repository = FakeTopicRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        assertEquals("Deskripsi topik", viewModel.state.value.topics.first().description)

        viewModel.updateTopic(
            topicId = "topic-1",
            title = "Topik Basis Data",
            description = "   ",
            published = true,
        )
        advanceUntilIdle()

        assertNull(viewModel.state.value.topics.first().description)
        assertNull(viewModel.state.value.actionError)
    }
}
