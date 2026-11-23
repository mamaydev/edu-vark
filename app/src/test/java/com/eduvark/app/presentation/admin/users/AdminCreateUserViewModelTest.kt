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
        viewModel.create("2025000200", "Mahasiswa Baru", UserRole.STUDENT, "short")
        advanceUntilIdle()
        assertEquals("Password sementara minimal 6 karakter.", viewModel.state.value.error)
    }

    @Test
    fun `creates user with valid form`() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = AdminCreateUserViewModel(CreateAdminUserUseCase(repository))
        viewModel.create("2025000200", "Mahasiswa Baru", UserRole.STUDENT, "abc123")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.success)
        assertEquals("2025000200", repository.user.loginId)
    }
    @Test
    fun `rejects academic identifiers outside 10 to 11 digits`() = runTest {
        for (invalidId in listOf("202500020", "202500020012")) {
            val repository = FakeAdminRepository()
            val viewModel = AdminCreateUserViewModel(CreateAdminUserUseCase(repository))

            viewModel.create(invalidId, "Mahasiswa Baru", UserRole.STUDENT, "abc123")
            advanceUntilIdle()

            assertEquals("NIM/NIP/NIDN harus 10–11 digit.", viewModel.state.value.error)
        }
    }

    @Test
    fun `admin account keeps a username instead of a NIM`() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = AdminCreateUserViewModel(CreateAdminUserUseCase(repository))

        viewModel.create("admin2", "Admin Kedua", UserRole.ADMIN, "abc123")
        advanceUntilIdle()

        assertTrue(viewModel.state.value.success)
        assertEquals("admin2", repository.user.loginId)
    }

}
