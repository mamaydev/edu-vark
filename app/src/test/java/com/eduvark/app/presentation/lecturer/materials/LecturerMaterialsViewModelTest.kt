package com.eduvark.app.presentation.lecturer.materials

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.model.UploadPayload
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.CreateLinkMaterialUseCase
import com.eduvark.app.domain.usecase.DeleteMaterialUseCase
import com.eduvark.app.domain.usecase.GetLecturerMaterialsUseCase
import com.eduvark.app.domain.usecase.GetLecturerTopicUseCase
import com.eduvark.app.domain.usecase.UploadMaterialUseCase
import com.eduvark.app.testutil.FakeTopicRepository
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
class LecturerMaterialsViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: FakeTopicRepository) = LecturerMaterialsViewModel(
        savedStateHandle = SavedStateHandle(mapOf("topicId" to "topic-1")),
        getTopicUseCase = GetLecturerTopicUseCase(repository),
        getMaterialsUseCase = GetLecturerMaterialsUseCase(repository),
        createLinkMaterialUseCase = CreateLinkMaterialUseCase(repository),
        uploadMaterialUseCase = UploadMaterialUseCase(repository),
        deleteMaterialUseCase = DeleteMaterialUseCase(repository),
    )

    @Test
    fun `loads a draft topic through the lecturer endpoint without VARK`() = runTest {
        val repository = FakeTopicRepository().apply { failStudentTopic = true }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals("Topik Basis Data", viewModel.state.value.topic?.title)
        assertEquals(null, viewModel.state.value.error)
    }

    @Test
    fun `upload rejects a whitespace-only title before calling the API`() = runTest {
        val repository = FakeTopicRepository(materials = emptyList())
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.upload(
            title = "  \n ",
            vark = VarkType.KINESTHETIC,
            payload = UploadPayload("materi.pdf", "application/pdf", byteArrayOf(1)),
        )
        advanceUntilIdle()

        assertEquals("Judul materi wajib diisi.", viewModel.state.value.actionError)
        assertEquals(0, repository.materials.size)
    }

    @Test
    fun `upload trims the title and stores a PDF material`() = runTest {
        val repository = FakeTopicRepository(materials = emptyList())
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.upload(
            title = "  Panduan Kinesthetic  ",
            vark = VarkType.KINESTHETIC,
            payload = UploadPayload("panduan.pdf", "application/pdf", byteArrayOf(1)),
        )
        advanceUntilIdle()

        assertEquals("Panduan Kinesthetic", repository.materials.single().title)
    }

    @Test
    fun `link rejects titles longer than 200 characters`() = runTest {
        val viewModel = createViewModel(FakeTopicRepository())
        advanceUntilIdle()

        viewModel.addLink(
            title = "a".repeat(201),
            vark = VarkType.VISUAL,
            url = "https://example.com/materi",
        )

        assertEquals(
            "Judul materi maksimal 200 karakter.",
            viewModel.state.value.actionError,
        )
    }
}
