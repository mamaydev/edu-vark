package com.eduvark.app.presentation.account

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.usecase.GetMeUseCase
import com.eduvark.app.domain.usecase.LogoutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class AccountUiState(
    val isLoading: Boolean = true,
    val user: UserProfile? = null,
    val error: String? = null,
    val loggedOut: Boolean = false,
)

@HiltViewModel
class AccountViewModel @Inject constructor(
    private val getMeUseCase: GetMeUseCase,
    private val logoutUseCase: LogoutUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(AccountUiState())
    val state: StateFlow<AccountUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, loggedOut = false) }
            try {
                val user = getMeUseCase()
                _state.update { it.copy(isLoading = false, user = user) }
            } catch (error: AppFailure) {
                if (error is AppFailure.Api && error.statusCode == 401) {
                    _state.update { it.copy(isLoading = false, loggedOut = true) }
                } else {
                    _state.update { it.copy(isLoading = false, error = error.message) }
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Terjadi kesalahan. Coba lagi.")
                }
            }
        }
    }

    fun logout() {
        viewModelScope.launch {
            _state.update { it.copy(error = null) }
            try {
                logoutUseCase()
                _state.update { it.copy(loggedOut = true) }
            } catch (error: AppFailure) {
                // The server session could not be revoked; keep the user signed
                // in and surface the failure instead of pretending to log out.
                _state.update { it.copy(error = error.message) }
            } catch (_: Exception) {
                _state.update {
                    it.copy(error = "Terjadi kesalahan. Coba lagi.")
                }
            }
        }
    }

    fun consumeLoggedOut() {
        _state.update { it.copy(loggedOut = false) }
    }
}
