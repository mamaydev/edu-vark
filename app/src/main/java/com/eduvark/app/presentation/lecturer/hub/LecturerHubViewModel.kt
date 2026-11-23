package com.eduvark.app.presentation.lecturer.hub

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.usecase.GetLecturerCaseUseCase
import com.eduvark.app.domain.usecase.GetTopicsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** A topic plus the case study that grading/analytics need. */
data class HubTopic(
    val topic: Topic,
    val caseId: String? = null,
    val submissionCount: Int? = null,
)

data class LecturerHubUiState(
    val isLoading: Boolean = true,
    val isRefreshing: Boolean = false,
    val topics: List<HubTopic> = emptyList(),
    val error: String? = null,
    val refreshError: String? = null,
)

/**
 * Shared state for the "Penilaian" and "Analitik" tabs: both start by picking a
 * topic. Grading additionally resolves the case study so the item can jump
 * straight to the submission list instead of the case editor.
 */
@HiltViewModel
class LecturerHubViewModel @Inject constructor(
    private val getTopicsUseCase: GetTopicsUseCase,
    private val getLecturerCaseUseCase: GetLecturerCaseUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LecturerHubUiState())
    val state: StateFlow<LecturerHubUiState> = _state.asStateFlow()

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
                    it.copy(isLoading = true, error = null)
                } else {
                    it.copy(isRefreshing = true, error = null, refreshError = null)
                }
            }
            try {
                val topics = getTopicsUseCase()
                val hubTopics = topics.map { topic ->
                    val case = runCatching { getLecturerCaseUseCase(topic.id) }.getOrNull()
                    HubTopic(
                        topic = topic,
                        caseId = case?.id,
                        submissionCount = case?.submissionCount,
                    )
                }
                _state.update {
                    it.copy(isLoading = false, isRefreshing = false, topics = hubTopics)
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
