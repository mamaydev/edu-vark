package com.eduvark.app.presentation.student.cases

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.domain.usecase.DownloadMaterialContentUseCase
import com.eduvark.app.domain.usecase.GetMaterialLinkTargetUseCase
import com.eduvark.app.domain.usecase.GetMaterialsUseCase
import com.eduvark.app.domain.usecase.GetStudentCaseUseCase
import com.eduvark.app.domain.usecase.GetTopicUseCase
import com.eduvark.app.domain.usecase.SubmitCaseUseCase
import com.eduvark.app.testutil.FakeCaseRepository
import com.eduvark.app.testutil.FakeMaterialContentSource
import com.eduvark.app.testutil.FakeMaterialFileStore
import com.eduvark.app.testutil.FakeTopicRepository
import com.eduvark.app.testutil.sampleMaterial
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
 * C1-03 (empty vs error) dan C1-04 (panjang jawaban dalam code point) untuk
 * layar studi kasus Kinesthetic.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class StudentCaseStateViewModelTest {

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
        caseRepository: FakeCaseRepository = FakeCaseRepository(),
        topicRepository: FakeTopicRepository = FakeTopicRepository(),
    ) = StudentCaseViewModel(
        savedStateHandle = SavedStateHandle(mapOf("topicId" to "topic-1")),
        getStudentCaseUseCase = GetStudentCaseUseCase(caseRepository),
        getTopicUseCase = GetTopicUseCase(topicRepository),
        getMaterialsUseCase = GetMaterialsUseCase(topicRepository),
        submitCaseUseCase = SubmitCaseUseCase(caseRepository),
        downloadMaterialContentUseCase = DownloadMaterialContentUseCase(
            FakeMaterialContentSource(topicRepository),
        ),
        getMaterialLinkTargetUseCase = GetMaterialLinkTargetUseCase(topicRepository),
        materialFileStore = FakeMaterialFileStore(),
    )

    @Test
    fun `published topic without supporting materials is empty not error`() = runTest {
        val viewModel = createViewModel(
            topicRepository = FakeTopicRepository(materials = emptyList()),
        )
        advanceUntilIdle()

        assertFalse(viewModel.state.value.materialsFailed)
        assertTrue(viewModel.state.value.materials.isEmpty())
        // Studi kasus tetap tampil meskipun materi pendukung kosong.
        assertEquals(SubmissionStatus.BELUM_MENGUMPULKAN, viewModel.state.value.caseStudy?.status)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `material fetch failure stays distinct from empty state`() = runTest {
        val viewModel = createViewModel(
            topicRepository = FakeTopicRepository(
                failure = AppFailure.Api("NETWORK_ERROR", "Tidak dapat terhubung ke server.", 0),
            ),
        )
        advanceUntilIdle()

        assertTrue(viewModel.state.value.materialsFailed)
        assertTrue(viewModel.state.value.materials.isEmpty())
        // Error materi tidak boleh menyembunyikan studi kasus.
        assertEquals(SubmissionStatus.BELUM_MENGUMPULKAN, viewModel.state.value.caseStudy?.status)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `reload materials recovers after a network failure`() = runTest {
        val topicRepository = FakeTopicRepository(
            materials = listOf(sampleMaterial().copy(title = "Panduan")),
            failure = AppFailure.Api("NETWORK_ERROR", "Tidak dapat terhubung ke server.", 0),
        )
        val viewModel = createViewModel(topicRepository = topicRepository)
        advanceUntilIdle()
        assertTrue(viewModel.state.value.materialsFailed)

        topicRepository.failure = null
        viewModel.reloadMaterials()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.materialsFailed)
        assertEquals(1, viewModel.state.value.materials.size)
    }

    @Test
    fun `ten thousand code points of emoji are accepted`() = runTest {
        val repository = FakeCaseRepository()
        val viewModel = createViewModel(caseRepository = repository)
        advanceUntilIdle()

        val emojiAnswer = "\uD83D\uDE00".repeat(10_000)
        viewModel.submit(emojiAnswer)
        advanceUntilIdle()

        assertNull(viewModel.state.value.submitError)
        assertEquals(SubmissionStatus.MENUNGGU_PENILAIAN, viewModel.state.value.caseStudy?.status)
    }

    @Test
    fun `ten thousand and one code points are rejected before the API`() = runTest {
        val repository = FakeCaseRepository()
        val viewModel = createViewModel(caseRepository = repository)
        advanceUntilIdle()

        viewModel.submit("x".repeat(10_001))
        advanceUntilIdle()

        assertEquals("Jawaban harus 1–10.000 karakter.", viewModel.state.value.submitError)
        assertEquals(
            SubmissionStatus.BELUM_MENGUMPULKAN,
            viewModel.state.value.caseStudy?.status,
        )
    }

    @Test
    fun `empty and whitespace answers are rejected`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.submit("   ")
        advanceUntilIdle()

        assertEquals("Jawaban harus 1–10.000 karakter.", viewModel.state.value.submitError)
        assertEquals(
            SubmissionStatus.BELUM_MENGUMPULKAN,
            viewModel.state.value.caseStudy?.status,
        )
    }

    @Test
    fun `one emoji is long enough after trimming`() = runTest {
        val viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.submit("  \uD83D\uDE00  ")
        advanceUntilIdle()

        assertNull(viewModel.state.value.submitError)
        assertEquals(SubmissionStatus.MENUNGGU_PENILAIAN, viewModel.state.value.caseStudy?.status)
    }
}
