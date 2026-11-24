package com.eduvark.app.presentation.student.cases

import androidx.lifecycle.SavedStateHandle
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
import org.junit.Assert.assertNotNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentCaseViewModelTest {

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
        repository: FakeCaseRepository,
        topicRepository: FakeTopicRepository = FakeTopicRepository(),
    ) = StudentCaseViewModel(
        savedStateHandle = SavedStateHandle(mapOf("topicId" to "topic-1")),
        getStudentCaseUseCase = GetStudentCaseUseCase(repository),
        getTopicUseCase = GetTopicUseCase(topicRepository),
        getMaterialsUseCase = GetMaterialsUseCase(topicRepository),
        submitCaseUseCase = SubmitCaseUseCase(repository),
        downloadMaterialContentUseCase = DownloadMaterialContentUseCase(
            FakeMaterialContentSource(topicRepository),
        ),
        getMaterialLinkTargetUseCase = GetMaterialLinkTargetUseCase(topicRepository),
        materialFileStore = FakeMaterialFileStore(),
    )

    @Test
    fun `loads case and submits once`() = runTest {
        val repository = FakeCaseRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        assertEquals(SubmissionStatus.BELUM_MENGUMPULKAN, viewModel.state.value.caseStudy?.status)

        viewModel.submit("Jawaban final.")
        advanceUntilIdle()

        assertEquals(SubmissionStatus.MENUNGGU_PENILAIAN, viewModel.state.value.caseStudy?.status)
        assertNotNull(viewModel.state.value.caseStudy?.submittedAt)
    }

    @Test
    fun `loads Kinesthetic supporting materials with the case`() = runTest {
        val topicRepository = FakeTopicRepository(
            materials = listOf(
                sampleMaterial().copy(
                    title = "Panduan PDF",
                    signedUrl = "https://signed.example/panduan.pdf",
                ),
            ),
        )
        val viewModel = createViewModel(FakeCaseRepository(), topicRepository)
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.materials.size)
        assertEquals("Panduan PDF", viewModel.state.value.materials.first().title)
        assertEquals(
            "https://signed.example/panduan.pdf",
            viewModel.state.value.materials.first().signedUrl,
        )
    }

    @Test
    fun `rejects empty answer locally`() = runTest {
        val viewModel = createViewModel(FakeCaseRepository())
        advanceUntilIdle()

        viewModel.submit("   ")
        advanceUntilIdle()

        assertEquals("Jawaban harus 1–10.000 karakter.", viewModel.state.value.submitError)
    }

    @Test
    fun `submit sends text normalized with the shared C1-04 rule`() = runTest {
        val repository = FakeCaseRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        // U+FEFF dan U+00A0 hanya dibuang oleh padanan ECMAScript trim.
        viewModel.submit("\uFEFF  Jawaban final.\u00A0 ")
        advanceUntilIdle()

        assertEquals(listOf("Jawaban final."), repository.submittedTexts)
    }
}
