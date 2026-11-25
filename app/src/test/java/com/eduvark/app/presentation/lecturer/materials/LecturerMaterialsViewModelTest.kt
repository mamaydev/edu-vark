package com.eduvark.app.presentation.lecturer.materials

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.R
import com.eduvark.app.domain.model.FileDeletionStatus
import com.eduvark.app.domain.model.UploadPayload
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.CreateLinkMaterialUseCase
import com.eduvark.app.domain.usecase.DeleteMaterialUseCase
import com.eduvark.app.domain.usecase.GetLecturerMaterialsUseCase
import com.eduvark.app.domain.usecase.GetLecturerTopicUseCase
import com.eduvark.app.domain.usecase.ReplaceMaterialFileUseCase
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
import org.junit.Assert.assertNotEquals
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
        replaceMaterialFileUseCase = ReplaceMaterialFileUseCase(repository),
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

    @Test
    fun `delete with pending storage deletion shows the retry notice`() = runTest {
        val repository = FakeTopicRepository().apply {
            deletionStatus = FileDeletionStatus.PENDING
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val materialId = viewModel.state.value.materials.first().id

        viewModel.delete(materialId)
        advanceUntilIdle()

        assertEquals(0, repository.materials.size)
        val notice = viewModel.state.value.fileDeletionNotice
        assertEquals(FileDeletionStatus.PENDING, notice?.status)
        assertEquals(R.string.lec_material_file_pending_body, notice?.messageRes)

        viewModel.clearFileDeletionNotice()
        assertEquals(null, viewModel.state.value.fileDeletionNotice)
    }

    @Test
    fun `delete maps every physical status and only pending promises retry`() = runTest {
        val expected = linkedMapOf(
            FileDeletionStatus.NONE to null,
            FileDeletionStatus.COMPLETED to null,
            FileDeletionStatus.PENDING to R.string.lec_material_file_pending_body,
            FileDeletionStatus.SKIPPED to R.string.lec_material_file_skipped_body,
            FileDeletionStatus.FAILED to R.string.lec_material_file_failed_body,
            FileDeletionStatus.UNKNOWN to R.string.lec_material_file_unknown_body,
        )

        for ((status, expectedMessage) in expected) {
            val repository = FakeTopicRepository().apply { deletionStatus = status }
            val viewModel = createViewModel(repository)
            advanceUntilIdle()

            viewModel.delete(viewModel.state.value.materials.first().id)
            advanceUntilIdle()

            val notice = viewModel.state.value.fileDeletionNotice
            if (expectedMessage == null) {
                // NONE/COMPLETED tidak menampilkan pemberitahuan apa pun.
                assertEquals(null, notice)
                continue
            }
            assertEquals(status, notice?.status)
            assertEquals(expectedMessage, notice?.messageRes)
            if (status != FileDeletionStatus.PENDING) {
                // Hanya PENDING yang boleh memakai pesan "dicoba ulang otomatis".
                assertNotEquals(
                    R.string.lec_material_file_pending_body,
                    notice?.messageRes,
                )
            }
        }
    }

    @Test
    fun `replace maps every physical status and only pending promises retry`() = runTest {
        val expected = linkedMapOf(
            FileDeletionStatus.NONE to null,
            FileDeletionStatus.COMPLETED to null,
            FileDeletionStatus.PENDING to R.string.lec_material_file_replace_pending_body,
            FileDeletionStatus.SKIPPED to R.string.lec_material_file_replace_skipped_body,
            FileDeletionStatus.FAILED to R.string.lec_material_file_replace_failed_body,
            FileDeletionStatus.UNKNOWN to R.string.lec_material_file_replace_unknown_body,
        )

        for ((status, expectedMessage) in expected) {
            val repository = FakeTopicRepository().apply {
                replaceDeletionStatus = status
            }
            val viewModel = createViewModel(repository)
            advanceUntilIdle()

            viewModel.replaceFile(
                viewModel.state.value.materials.first().id,
                UploadPayload("pengganti.pdf", "application/pdf", byteArrayOf(1, 2, 3)),
            )
            advanceUntilIdle()

            val notice = viewModel.state.value.fileDeletionNotice
            if (expectedMessage == null) {
                // NONE/COMPLETED tidak menampilkan pemberitahuan apa pun.
                assertEquals(null, notice)
                continue
            }
            assertEquals(status, notice?.status)
            assertEquals(expectedMessage, notice?.messageRes)
            if (status != FileDeletionStatus.PENDING) {
                assertNotEquals(
                    R.string.lec_material_file_replace_pending_body,
                    notice?.messageRes,
                )
            }
        }
    }

    @Test
    fun `successful delete keeps the notice and hidden material when list refresh fails`() = runTest {
        val repository = FakeTopicRepository().apply {
            deletionStatus = FileDeletionStatus.PENDING
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val materialId = viewModel.state.value.materials.first().id

        repository.failMaterialsOnRefresh = true
        viewModel.delete(materialId)
        advanceUntilIdle()

        // Mutasi sudah commit; kegagalan hanya pada pemuatan ulang daftar.
        assertEquals(0, repository.materials.size)
        assertEquals(FileDeletionStatus.PENDING, viewModel.state.value.fileDeletionNotice?.status)
        assertEquals(
            "Perubahan sudah tersimpan, tetapi daftar materi gagal dimuat ulang. " +
                "Muat ulang halaman untuk melihat data terbaru.",
            viewModel.state.value.actionError,
        )
        assertEquals(false, viewModel.state.value.isSaving)
    }

    @Test
    fun `successful replace keeps the failed status notice when list refresh fails`() = runTest {
        val repository = FakeTopicRepository().apply {
            replaceDeletionStatus = FileDeletionStatus.FAILED
        }
        val viewModel = createViewModel(repository)
        advanceUntilIdle()
        val material = viewModel.state.value.materials.first()

        repository.failMaterialsOnRefresh = true
        viewModel.replaceFile(
            material.id,
            UploadPayload("pengganti.pdf", "application/pdf", byteArrayOf(4, 5)),
        )
        advanceUntilIdle()

        assertEquals(FileDeletionStatus.FAILED, viewModel.state.value.fileDeletionNotice?.status)
        assertEquals(
            "Perubahan sudah tersimpan, tetapi daftar materi gagal dimuat ulang. " +
                "Muat ulang halaman untuk melihat data terbaru.",
            viewModel.state.value.actionError,
        )
        assertEquals(false, viewModel.state.value.isSaving)
        assertNotEquals("Gagal mengganti berkas.", viewModel.state.value.actionError)
    }
}
