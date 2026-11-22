package com.eduvark.app.presentation.student.materials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.GetFeedbackUseCase
import com.eduvark.app.domain.usecase.GetMaterialsUseCase
import com.eduvark.app.domain.usecase.GetMeUseCase
import com.eduvark.app.domain.usecase.GetTopicUseCase
import com.eduvark.app.domain.usecase.SaveFeedbackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentMaterialsUiState(
    val isLoading: Boolean = true,
    val topic: Topic? = null,
    val materials: List<Material> = emptyList(),
    val activeVark: VarkType? = null,
    val feedback: Int? = null,
    val feedbackUpdatedAt: String? = null,
    val isSavingFeedback: Boolean = false,
    val feedbackError: String? = null,
    val error: String? = null,
)

@HiltViewModel
class StudentMaterialsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTopicUseCase: GetTopicUseCase,
    private val getMaterialsUseCase: GetMaterialsUseCase,
    private val getMeUseCase: GetMeUseCase,
    private val getFeedbackUseCase: GetFeedbackUseCase,
    private val saveFeedbackUseCase: SaveFeedbackUseCase,
) : ViewModel() {

    private val topicId: String = savedStateHandle.get<String>("topicId").orEmpty()

    private val _state = MutableStateFlow(StudentMaterialsUiState())
    val state: StateFlow<StudentMaterialsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (topicId.isBlank()) {
            _state.value = StudentMaterialsUiState(isLoading = false, error = "Topik tidak valid.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, feedbackError = null) }
            try {
                val topic = getTopicUseCase(topicId)
                val materials = getMaterialsUseCase(topicId)
                val user = getMeUseCase()
                val vark = user.activeVark

                var feedback: Int? = null
                var feedbackUpdatedAt: String? = null
                var feedbackError: String? = null
                if (vark != null && vark != VarkType.KINESTHETIC && materials.isNotEmpty()) {
                    try {
                        val stored = getFeedbackUseCase(topicId)
                        feedback = stored.value
                        feedbackUpdatedAt = stored.updatedAt
                    } catch (error: AppFailure) {
                        feedbackError = error.message
                    }
                }

                _state.value = StudentMaterialsUiState(
                    isLoading = false,
                    topic = topic,
                    materials = materials,
                    activeVark = vark,
                    feedback = feedback,
                    feedbackUpdatedAt = feedbackUpdatedAt,
                    feedbackError = feedbackError,
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

    fun saveFeedback(value: Int) {
        val vark = _state.value.activeVark
        if (vark == null || vark == VarkType.KINESTHETIC) {
            _state.update {
                it.copy(feedbackError = "Feedback hanya tersedia untuk Visual, Audio, dan Read/Write.")
            }
            return
        }
        if (_state.value.isSavingFeedback) return

        viewModelScope.launch {
            _state.update {
                it.copy(isSavingFeedback = true, feedbackError = null)
            }
            try {
                val feedback = saveFeedbackUseCase(topicId, value.coerceIn(0, 100))
                _state.update {
                    it.copy(
                        isSavingFeedback = false,
                        feedback = feedback.value,
                        feedbackUpdatedAt = feedback.updatedAt,
                        feedbackError = null,
                    )
                }
            } catch (error: AppFailure) {
                _state.update {
                    it.copy(isSavingFeedback = false, feedbackError = error.message)
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        isSavingFeedback = false,
                        feedbackError = "Gagal menyimpan feedback.",
                    )
                }
            }
        }
    }
}
