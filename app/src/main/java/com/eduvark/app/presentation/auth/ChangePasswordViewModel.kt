package com.eduvark.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.usecase.ChangePasswordUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class ChangePasswordUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
)

@HiltViewModel
class ChangePasswordViewModel @Inject constructor(
    private val changePasswordUseCase: ChangePasswordUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ChangePasswordUiState())
    val state: StateFlow<ChangePasswordUiState> = _state.asStateFlow()

    fun submit(currentPassword: String, newPassword: String, confirmation: String) {
        if (currentPassword.isEmpty()) {
            _state.update { it.copy(error = "Password saat ini wajib diisi.") }
            return
        }
        if (newPassword.length < 6) {
            _state.update { it.copy(error = "Password minimal 6 karakter.") }
            return
        }
        if (newPassword != confirmation) {
            _state.update { it.copy(error = "Konfirmasi password tidak sama.") }
            return
        }
        if (newPassword == currentPassword) {
            _state.update {
                it.copy(error = "Password baru harus berbeda dari password saat ini.")
            }
            return
        }
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.value = ChangePasswordUiState(isLoading = true)
            try {
                changePasswordUseCase(currentPassword, newPassword, confirmation)
                _state.value = ChangePasswordUiState(success = true)
            } catch (error: AppFailure) {
                _state.value = ChangePasswordUiState(error = error.message)
            } catch (error: Exception) {
                _state.value = ChangePasswordUiState(error = "Terjadi kesalahan. Coba lagi.")
            }
        }
    }

    fun consumeSuccess() {
        _state.update { it.copy(success = false) }
    }
}
