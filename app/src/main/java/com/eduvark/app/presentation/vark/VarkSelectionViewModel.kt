package com.eduvark.app.presentation.vark

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.SelectVarkUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class VarkUiState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val success: Boolean = false,
    val selected: VarkType? = null,
)

@HiltViewModel
class VarkSelectionViewModel @Inject constructor(
    private val selectVarkUseCase: SelectVarkUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(VarkUiState())
    val state: StateFlow<VarkUiState> = _state.asStateFlow()

    fun select(vark: VarkType) {
        if (_state.value.isLoading) return
        viewModelScope.launch {
            _state.value = VarkUiState(isLoading = true)
            try {
                val selected = selectVarkUseCase(vark)
                _state.value = VarkUiState(success = true, selected = selected)
            } catch (error: AppFailure) {
                _state.value = VarkUiState(error = error.message)
            } catch (error: Exception) {
                _state.value = VarkUiState(error = "Terjadi kesalahan. Coba lagi.")
            }
        }
    }

    fun consumeSuccess() {
        _state.update { it.copy(success = false) }
    }
}
