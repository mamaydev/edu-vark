package com.eduvark.app.presentation.auth

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.usecase.LoginUseCase
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
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class LoginViewModelTest {

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
    fun `success exposes navigation flags`() = runTest {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))

        viewModel.submit("2025000001", "StudentPass123")
        advanceUntilIdle()

        assertEquals(LoginSuccess(mustChangePassword = false, varkRequired = true), viewModel.state.value.success)
        assertNull(viewModel.state.value.error)
    }

    @Test
    fun `empty fields produce validation error`() {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))

        viewModel.submit("", "")

        assertEquals("NIM/NIDN dan password wajib diisi.", viewModel.state.value.error)
    }

    @Test
    fun `api failure is surfaced`() = runTest {
        val viewModel = LoginViewModel(
            LoginUseCase(
                FakeAuthRepository(
                    loginFailure = AppFailure.Api(
                        code = "INVALID_CREDENTIALS",
                        message = "NIM/NIP/NIDN atau password tidak valid.",
                        statusCode = 401,
                    ),
                ),
            ),
        )

        viewModel.submit("2025000001", "wrong")
        advanceUntilIdle()

        assertEquals("NIM/NIP/NIDN atau password tidak valid.", viewModel.state.value.error)
    }
    @Test
    fun `rejects non numeric identifiers before calling the API`() {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))

        viewModel.submit("stu1", "StudentPass123")

        assertEquals("NIM/NIDN harus berupa 10–11 digit angka.", viewModel.state.value.error)
        assertNull(viewModel.state.value.success)
    }

    @Test
    fun `rejects identifiers outside 10 to 11 digits`() {
        for (invalid in listOf("202500000", "202500000012")) {
            val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))

            viewModel.submit(invalid, "StudentPass123")

            assertEquals("NIM/NIDN harus berupa 10–11 digit angka.", viewModel.state.value.error)
        }
    }

    @Test
    fun `rejects the retired admin username`() {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))

        viewModel.submit("admin", "AdminPass1234")

        assertEquals("NIM/NIDN harus berupa 10–11 digit angka.", viewModel.state.value.error)
        assertNull(viewModel.state.value.success)
    }

    @Test
    fun `accepts the admin login id pattern`() = runTest {
        val viewModel = LoginViewModel(LoginUseCase(FakeAuthRepository()))

        viewModel.submit("01401400001", "AdminPass1234")
        advanceUntilIdle()

        assertEquals(
            LoginSuccess(mustChangePassword = false, varkRequired = true),
            viewModel.state.value.success,
        )
    }

}
