package com.eduvark.app.presentation.admin.users

import com.eduvark.app.domain.usecase.ListAdminUsersUseCase
import com.eduvark.app.testutil.FakeAdminRepository
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
class AdminUsersViewModelTest {

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
    fun `loads and filters users`() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = AdminUsersViewModel(ListAdminUsersUseCase(repository))
        viewModel.load()
        advanceUntilIdle()

        assertEquals(1, viewModel.state.value.users.size)
        assertEquals(1, viewModel.state.value.total)

        viewModel.search("Mahasiswa")
        advanceUntilIdle()
        assertEquals("Mahasiswa", repository.lastQuery)
    }
}
