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
}
