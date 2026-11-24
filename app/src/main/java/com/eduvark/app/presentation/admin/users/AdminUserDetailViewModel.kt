package com.eduvark.app.presentation.admin.users

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.core.common.LoginIdRules
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.EnrollmentStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.usecase.DeactivateAdminUserUseCase
import com.eduvark.app.domain.usecase.GetAdminUserUseCase
import com.eduvark.app.domain.usecase.ResetAdminPasswordUseCase
import com.eduvark.app.domain.usecase.UpdateAdminUserUseCase
import com.eduvark.app.domain.usecase.UpdateEnrollmentUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminUserDetailUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val user: AdminUser? = null,
    val error: String? = null,
    val actionMessage: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class AdminUserDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getAdminUserUseCase: GetAdminUserUseCase,
    private val updateAdminUserUseCase: UpdateAdminUserUseCase,
    private val resetAdminPasswordUseCase: ResetAdminPasswordUseCase,
    private val deactivateAdminUserUseCase: DeactivateAdminUserUseCase,
    private val updateEnrollmentUseCase: UpdateEnrollmentUseCase,
) : ViewModel() {

    private val userId: String = savedStateHandle.get<String>("userId").orEmpty()

    private val _state = MutableStateFlow(AdminUserDetailUiState())
    val state: StateFlow<AdminUserDetailUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (userId.isBlank()) {
            _state.value = AdminUserDetailUiState(isLoading = false, error = "Pengguna tidak valid.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val user = getAdminUserUseCase(userId)
                _state.value = AdminUserDetailUiState(isLoading = false, user = user)
            } catch (error: AppFailure) {
                _state.update { it.copy(isLoading = false, error = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isLoading = false, error = "Gagal memuat pengguna.") }
            }
        }
    }

    fun updateProfile(fullName: String, role: UserRole, newLoginId: String = "") {
        if (fullName.trim().isEmpty()) {
            _state.update { it.copy(actionError = "Nama wajib diisi.") }
            return
        }
        val currentRole = _state.value.user?.role
        val crossesAdminBoundary =
            (currentRole == UserRole.ADMIN) != (role == UserRole.ADMIN)

        // Admin → academic needs an explicit target NIM/NIDN; academic → Admin
        // and admin → admin use the server-issued identity (FR-034).
        val targetLoginId: String? = when {
            !crossesAdminBoundary -> null
            role == UserRole.ADMIN -> null
            else -> newLoginId.trim().takeIf { LoginIdRules.isAcademicId(it) }
        }
        if (currentRole == UserRole.ADMIN && role != UserRole.ADMIN && targetLoginId == null) {
            _state.update { it.copy(actionError = LoginIdRules.ACADEMIC_ID_MESSAGE) }
            return
        }

        perform {
            val updated = updateAdminUserUseCase(
                userId,
                fullName.trim(),
                role,
                targetLoginId,
            )
            _state.update {
                it.copy(
                    user = updated,
                    actionMessage = "Profil diperbarui. Perubahan role/identitas mencabut sesi pengguna.",
                    actionError = null,
                )
            }
        }
    }

    fun resetPassword(temporaryPassword: String) {
        if (temporaryPassword.length < 6) {
            _state.update {
                it.copy(actionError = "Password sementara minimal 6 karakter.")
            }
            return
        }
        perform {
            resetAdminPasswordUseCase(userId, temporaryPassword)
            _state.update {
                it.copy(
                    user = it.user?.copy(mustChangePassword = true),
                    actionMessage = "Password direset. Pengguna wajib mengganti password.",
                    actionError = null,
                )
            }
        }
    }

    fun deactivate() {
        perform {
            deactivateAdminUserUseCase(userId)
            _state.update {
                it.copy(
                    user = it.user?.copy(status = com.eduvark.app.domain.model.AdminAccountStatus.INACTIVE),
                    actionMessage = "Akun dinonaktifkan dan sesi dicabut.",
                    actionError = null,
                )
            }
        }
    }

    fun setEnrollment(active: Boolean) {
        val enrollmentId = _state.value.user?.enrollmentId ?: return
        perform {
            val result = updateEnrollmentUseCase(enrollmentId, active)
            _state.update {
                it.copy(
                    user = it.user?.copy(enrollmentStatus = result.status),
                    actionMessage = if (result.status == EnrollmentStatus.ACTIVE) {
                        "Enrollment diaktifkan kembali."
                    } else {
                        "Mahasiswa dikeluarkan dari kelas; histori tetap tersimpan."
                    },
                    actionError = null,
                )
            }
        }
    }

    fun clearAction() {
        _state.update { it.copy(actionMessage = null, actionError = null) }
    }

    private fun perform(block: suspend () -> Unit) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null, actionMessage = null) }
            try {
                block()
                _state.update { it.copy(isSaving = false) }
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Aksi gagal.") }
            }
        }
    }
}
