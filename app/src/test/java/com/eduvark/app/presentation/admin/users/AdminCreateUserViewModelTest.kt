package com.eduvark.app.presentation.admin.users

import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.usecase.CreateAdminUserUseCase
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
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class AdminCreateUserViewModelTest {

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
    fun `rejects short temporary password`() = runTest {
        val viewModel = AdminCreateUserViewModel(
            CreateAdminUserUseCase(FakeAdminRepository()),
        )
        viewModel.create("stu-new", "Mahasiswa Baru", UserRole.STUDENT, "short")
        advanceUntilIdle()
        assertEquals("Password sementara minimal 6 karakter.", viewModel.state.value.error)
    }

    @Test
    fun `creates user with valid form`() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = AdminCreateUserViewModel(CreateAdminUserUseCase(repository))
        viewModel.create("stu-new", "Mahasiswa Baru", UserRole.STUDENT, "abc123")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.success)
        assertEquals("stu-new", repository.user.loginId)
    }
}
