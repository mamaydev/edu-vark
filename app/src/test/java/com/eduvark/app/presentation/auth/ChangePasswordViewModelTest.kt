package com.eduvark.app.presentation.auth

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.usecase.ChangePasswordUseCase
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
class ChangePasswordViewModelTest {

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
    fun `valid change succeeds`() = runTest {
        val viewModel = ChangePasswordViewModel(
            ChangePasswordUseCase(FakeAuthRepository()),
        )

        viewModel.submit("abc123", "def456", "def456")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.success)
    }

    @Test
    fun `mismatched confirmation is rejected locally`() {
        val viewModel = ChangePasswordViewModel(
            ChangePasswordUseCase(FakeAuthRepository()),
        )

        viewModel.submit("StudentPass123", "StudentPass456", "DifferentPass456")

        assertEquals("Konfirmasi password tidak sama.", viewModel.state.value.error)
    }

    @Test
    fun `reusing the current password is rejected locally`() {
        val repository = FakeAuthRepository()
        val viewModel = ChangePasswordViewModel(ChangePasswordUseCase(repository))

        viewModel.submit("StudentPass123", "StudentPass123", "StudentPass123")

        assertEquals(
            "Password baru harus berbeda dari password saat ini.",
            viewModel.state.value.error,
        )
        assertEquals(null, repository.changedPassword)
    }

    @Test
    fun `confirmation is sent to the repository`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = ChangePasswordViewModel(ChangePasswordUseCase(repository))

        viewModel.submit("StudentPass123", "StudentPass456", "StudentPass456")
        advanceUntilIdle()

        assertEquals(
            Triple("StudentPass123", "StudentPass456", "StudentPass456"),
            repository.changedPassword,
        )
    }

    @Test
    fun `server failure is surfaced`() = runTest {
        val viewModel = ChangePasswordViewModel(
            ChangePasswordUseCase(
                FakeAuthRepository(
                    changePasswordFailure = AppFailure.Api(
                        code = "INVALID_PASSWORD",
                        message = "Password saat ini tidak sesuai.",
                        statusCode = 422,
                    ),
                ),
            ),
        )

        viewModel.submit("StudentPass123", "StudentPass456", "StudentPass456")
        advanceUntilIdle()

        assertEquals("Password saat ini tidak sesuai.", viewModel.state.value.error)
    }
}
