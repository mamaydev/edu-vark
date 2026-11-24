package com.eduvark.app.presentation.admin.users

import androidx.lifecycle.SavedStateHandle
import com.eduvark.app.domain.model.EnrollmentStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.usecase.DeactivateAdminUserUseCase
import com.eduvark.app.domain.usecase.GetAdminUserUseCase
import com.eduvark.app.domain.usecase.ResetAdminPasswordUseCase
import com.eduvark.app.domain.usecase.UpdateAdminUserUseCase
import com.eduvark.app.domain.usecase.UpdateEnrollmentUseCase
import com.eduvark.app.testutil.FakeAdminRepository
import com.eduvark.app.testutil.sampleAdminUser
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
class AdminUserDetailViewModelTest {

    private val dispatcher = UnconfinedTestDispatcher()

    @Before
    fun setUp() {
        Dispatchers.setMain(dispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(repository: FakeAdminRepository) = AdminUserDetailViewModel(
        savedStateHandle = SavedStateHandle(mapOf("userId" to "user-1")),
        getAdminUserUseCase = GetAdminUserUseCase(repository),
        updateAdminUserUseCase = UpdateAdminUserUseCase(repository),
        resetAdminPasswordUseCase = ResetAdminPasswordUseCase(repository),
        deactivateAdminUserUseCase = DeactivateAdminUserUseCase(repository),
        updateEnrollmentUseCase = UpdateEnrollmentUseCase(repository),
    )

    @Test
    fun `updates profile and enrollment`() = runTest {
        val repository = FakeAdminRepository()
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateProfile("Nama Baru", UserRole.LECTURER)
        advanceUntilIdle()
        assertEquals("Nama Baru", viewModel.state.value.user?.fullName)
        assertEquals(UserRole.LECTURER, viewModel.state.value.user?.role)

        viewModel.setEnrollment(false)
        advanceUntilIdle()
        assertEquals(EnrollmentStatus.LEFT, viewModel.state.value.user?.enrollmentStatus)
    }

    @Test
    fun `admin role needs a valid target NIM before switching to academic`() = runTest {
        val repository = FakeAdminRepository(
            user = sampleAdminUser(role = UserRole.ADMIN, loginId = "01401400001"),
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateProfile("Admin Satu", UserRole.STUDENT)
        advanceUntilIdle()

        assertEquals("NIM/NIDN harus berupa 10–11 digit angka.", viewModel.state.value.actionError)
        assertEquals(UserRole.ADMIN, viewModel.state.value.user?.role)
        assertEquals("01401400001", viewModel.state.value.user?.loginId)
    }

    @Test
    fun `admin to academic applies the supplied target NIM`() = runTest {
        val repository = FakeAdminRepository(
            user = sampleAdminUser(role = UserRole.ADMIN, loginId = "01401400001"),
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateProfile("Admin Satu", UserRole.STUDENT, "2025000200")
        advanceUntilIdle()

        val user = viewModel.state.value.user
        assertEquals(UserRole.STUDENT, user?.role)
        assertEquals("2025000200", user?.loginId)
        assertEquals("2025000200", repository.lastUpdatedLoginId)
    }

    @Test
    fun `academic to admin leaves identity allocation to the server`() = runTest {
        val repository = FakeAdminRepository(
            user = sampleAdminUser(role = UserRole.STUDENT, loginId = "2025000200"),
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateProfile("Mahasiswa Baru", UserRole.ADMIN, "2025000299")
        advanceUntilIdle()

        assertEquals(UserRole.ADMIN, viewModel.state.value.user?.role)
        assertEquals("01401400002", viewModel.state.value.user?.loginId)
        assertNull(repository.lastUpdatedLoginId)
    }

    @Test
    fun `academic to academic keeps the login id`() = runTest {
        val repository = FakeAdminRepository(
            user = sampleAdminUser(role = UserRole.LECTURER, loginId = "2025000200"),
        )
        val viewModel = createViewModel(repository)
        advanceUntilIdle()

        viewModel.updateProfile("Dosen Baru", UserRole.LECTURER, "9999999999")
        advanceUntilIdle()

        assertEquals("2025000200", viewModel.state.value.user?.loginId)
        assertNull(repository.lastUpdatedLoginId)
    }
}
