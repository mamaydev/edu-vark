package com.eduvark.app.presentation.admin.users

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.core.common.LoginIdRules
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.usecase.CreateAdminUserUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AdminCreateUserUiState(
    val isSaving: Boolean = false,
    val success: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class AdminCreateUserViewModel @Inject constructor(
    private val createAdminUserUseCase: CreateAdminUserUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AdminCreateUserUiState())
    val state: StateFlow<AdminCreateUserUiState> = _state.asStateFlow()

    fun create(
        loginId: String,
        fullName: String,
        role: UserRole,
        temporaryPassword: String,
    ) {
        if (fullName.trim().isEmpty()) {
            _state.update { it.copy(error = "Nama wajib diisi.") }
            return
        }
        // Admins never provide an identity: the server allocates it (FR-036).
        if (role != UserRole.ADMIN && !LoginIdRules.isAcademicId(loginId)) {
            _state.update { it.copy(error = LoginIdRules.ACADEMIC_ID_MESSAGE) }
            return
        }
        if (temporaryPassword.length < 6) {
            _state.update { it.copy(error = "Password sementara minimal 6 karakter.") }
            return
        }
        if (_state.value.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, error = null) }
            try {
                createAdminUserUseCase(
                    loginId = if (role == UserRole.ADMIN) null else loginId.trim(),
                    fullName = fullName.trim(),
                    role = role,
                    temporaryPassword = temporaryPassword,
                )
                _state.update { it.copy(isSaving = false, success = true) }
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, error = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, error = "Gagal membuat pengguna.") }
            }
        }
    }

    fun consumeSuccess() {
        _state.update { it.copy(success = false) }
    }
}
