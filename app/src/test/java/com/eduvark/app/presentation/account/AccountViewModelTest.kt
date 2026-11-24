package com.eduvark.app.presentation.account

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.usecase.GetMeUseCase
import com.eduvark.app.domain.usecase.LogoutUseCase
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
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AccountViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun viewModel(repository: FakeAuthRepository) = AccountViewModel(
        GetMeUseCase(repository),
        LogoutUseCase(repository),
    )

    @Test
    fun `logout navigates only after the server confirms revocation`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertTrue(viewModel.state.value.loggedOut)
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun `failed logout keeps the session and surfaces the failure`() = runTest {
        val repository = FakeAuthRepository(
            logoutFailure = AppFailure.Api(
                code = "INTERNAL_ERROR",
                message = "Sesi belum dapat diakhiri. Coba lagi.",
                statusCode = 500,
            ),
        )
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loggedOut)
        assertEquals("Sesi belum dapat diakhiri. Coba lagi.", viewModel.state.value.error)
        assertEquals(1, repository.logoutCalls)
    }

    @Test
    fun `network failure during logout does not pretend success`() = runTest {
        val repository = FakeAuthRepository(logoutFailure = AppFailure.Network)
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()

        assertFalse(viewModel.state.value.loggedOut)
        assertEquals(AppFailure.Network.message, viewModel.state.value.error)
    }

    @Test
    fun `consumeLoggedOut clears the navigation trigger`() = runTest {
        val repository = FakeAuthRepository()
        val viewModel = viewModel(repository)
        advanceUntilIdle()

        viewModel.logout()
        advanceUntilIdle()
        assertTrue(viewModel.state.value.loggedOut)

        viewModel.consumeLoggedOut()
        assertFalse(viewModel.state.value.loggedOut)
        assertNull(viewModel.state.value.error)
    }
}
