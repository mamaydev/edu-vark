package com.eduvark.app.presentation.vark

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.SelectVarkUseCase
import com.eduvark.app.testutil.FakeAuthRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class VarkSelectionViewModelTest {

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
    fun `selection succeeds and exposes chosen VARK`() = runTest {
        val viewModel = VarkSelectionViewModel(
            SelectVarkUseCase(
                FakeAuthRepository(selectVarkResult = VarkType.AURAL),
            ),
        )

        viewModel.select(VarkType.AURAL)
        advanceUntilIdle()

        assertTrue(viewModel.state.value.success)
        assertEquals(VarkType.AURAL, viewModel.state.value.selected)
    }

    @Test
    fun `selection failure is surfaced`() = runTest {
        val viewModel = VarkSelectionViewModel(
            SelectVarkUseCase(
                FakeAuthRepository(
                    selectVarkFailure = AppFailure.Api(
                        code = "VARK_ALREADY_SET",
                        message = "VARK sesi ini sudah dikunci.",
                        statusCode = 409,
                    ),
                ),
            ),
        )

        viewModel.select(VarkType.VISUAL)
        advanceUntilIdle()

        assertEquals("VARK sesi ini sudah dikunci.", viewModel.state.value.error)
    }
}
