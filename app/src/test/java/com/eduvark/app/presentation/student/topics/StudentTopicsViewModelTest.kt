package com.eduvark.app.presentation.student.topics

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.usecase.GetMeUseCase
import com.eduvark.app.domain.usecase.GetTopicsUseCase
import com.eduvark.app.testutil.FakeAuthRepository
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StudentTopicsViewModelTest {

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
    fun `loads student topics`() = runTest {
        val viewModel = StudentTopicsViewModel(
            getTopicsUseCase = GetTopicsUseCase(FakeTopicRepository()),
            getMeUseCase = GetMeUseCase(FakeAuthRepository()),
        )
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.topics.size)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `surfaces API failure`() = runTest {
        val viewModel = StudentTopicsViewModel(
            getTopicsUseCase = GetTopicsUseCase(
                FakeTopicRepository(
                    failure = AppFailure.Api("NOT_ENROLLED", "Belum terdaftar.", 403),
                ),
            ),
            getMeUseCase = GetMeUseCase(FakeAuthRepository()),
        )
        advanceUntilIdle()

        assertEquals("Belum terdaftar.", viewModel.state.value.error)
    }
}
