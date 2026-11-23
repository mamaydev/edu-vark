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
    val isRefreshing: Boolean = false,
    val topics: List<Topic> = emptyList(),
    val activeVark: VarkType? = null,
    val error: String? = null,
    val refreshError: String? = null,
)

@HiltViewModel
class StudentTopicsViewModel @Inject constructor(
    private val getTopicsUseCase: GetTopicsUseCase,
    private val getMeUseCase: GetMeUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(StudentTopicsUiState())
    val state: StateFlow<StudentTopicsUiState> = _state.asStateFlow()

    private var requestInFlight = false

    init {
        load()
    }

    fun load() = fetch(showFullLoading = true)

    fun refresh() = fetch(showFullLoading = false)

    private fun fetch(showFullLoading: Boolean) {
        if (requestInFlight) return
        requestInFlight = true
        viewModelScope.launch {
            _state.update {
                if (showFullLoading) {
                    it.copy(isLoading = true, error = null, refreshError = null)
                } else {
                    it.copy(isRefreshing = true, error = null, refreshError = null)
                }
            }
            try {
                val topics = getTopicsUseCase()
                val user = getMeUseCase()
                _state.update {
                    it.copy(
                        isLoading = false,
                        isRefreshing = false,
                        topics = topics,
                        activeVark = user.activeVark,
                    )
                }
            } catch (error: AppFailure) {
                _state.update {
                    if (it.topics.isEmpty()) {
                        it.copy(isLoading = false, isRefreshing = false, error = error.message)
                    } else {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            refreshError = error.message,
                        )
                    }
                }
            } catch (error: Exception) {
                _state.update {
                    if (it.topics.isEmpty()) {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            error = "Terjadi kesalahan. Coba lagi.",
                        )
                    } else {
                        it.copy(
                            isLoading = false,
                            isRefreshing = false,
                            refreshError = "Terjadi kesalahan. Coba lagi.",
                        )
                    }
                }
            } finally {
                requestInFlight = false
            }
        }
    }
}
