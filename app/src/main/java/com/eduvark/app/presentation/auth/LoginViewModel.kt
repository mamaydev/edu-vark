package com.eduvark.app.presentation.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.core.common.LoginIdRules
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.usecase.LoginUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LoginSuccess(
    val mustChangePassword: Boolean,
    val varkRequired: Boolean,
)

data class LoginUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val blocked: Boolean = false,
    val success: LoginSuccess? = null,
)

@HiltViewModel
class LoginViewModel @Inject constructor(
    private val loginUseCase: LoginUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LoginUiState())
    val state: StateFlow<LoginUiState> = _state.asStateFlow()

    fun submit(loginId: String, password: String) {
        val trimmedLoginId = loginId.trim()
        if (trimmedLoginId.isEmpty() || password.isEmpty()) {
            _state.update { it.copy(error = "NIM/NIP/NIDN dan password wajib diisi.") }
            return
        }
        if (!LoginIdRules.isValidLoginId(trimmedLoginId)) {
            _state.update { it.copy(error = LoginIdRules.LOGIN_ID_MESSAGE) }
            return
        }
        if (_state.value.isLoading) return

        viewModelScope.launch {
            _state.value = LoginUiState(isLoading = true)
            try {
                val session = loginUseCase(trimmedLoginId, password)
                _state.value = LoginUiState(
                    success = LoginSuccess(
                        mustChangePassword = session.user.mustChangePassword,
                        varkRequired = session.varkRequired,
                    ),
                )
            } catch (error: AppFailure) {
                val api = error as? AppFailure.Api
                val blocked = api != null &&
                    (api.code == "ACCOUNT_INACTIVE" || api.statusCode == 403)
                _state.value = if (blocked) {
                    LoginUiState(blocked = true, error = error.message)
                } else {
                    LoginUiState(error = error.message)
                }
            } catch (error: Exception) {
                _state.value = LoginUiState(error = "Terjadi kesalahan. Coba lagi.")
            }
        }
    }

    fun clearBlocked() {
        _state.update { it.copy(blocked = false, error = null) }
    }

    fun consumeSuccess() {
        _state.update { it.copy(success = null) }
    }
}
