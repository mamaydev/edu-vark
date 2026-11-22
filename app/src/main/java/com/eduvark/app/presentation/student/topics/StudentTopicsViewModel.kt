package com.eduvark.app.presentation.student.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.GetMeUseCase
import com.eduvark.app.domain.usecase.GetTopicsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentTopicsUiState(
    val isLoading: Boolean = true,
    val topics: List<Topic> = emptyList(),
    val activeVark: VarkType? = null,
    val error: String? = null,
)

@HiltViewModel
class StudentTopicsViewModel @Inject constructor(
    private val getTopicsUseCase: GetTopicsUseCase,
    private val getMeUseCase: GetMeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(StudentTopicsUiState())
    val state: StateFlow<StudentTopicsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val topics = getTopicsUseCase()
                val user = getMeUseCase()
                _state.value = StudentTopicsUiState(
                    isLoading = false,
                    topics = topics,
                    activeVark = user.activeVark,
                )
            } catch (error: AppFailure) {
                _state.update { it.copy(isLoading = false, error = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Terjadi kesalahan. Coba lagi.")
                }
            }
        }
    }
}
