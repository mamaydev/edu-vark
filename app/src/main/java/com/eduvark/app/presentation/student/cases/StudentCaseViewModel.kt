package com.eduvark.app.presentation.student.cases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.CaseStudy
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.usecase.GetMaterialsUseCase
import com.eduvark.app.domain.usecase.GetStudentCaseUseCase
import com.eduvark.app.domain.usecase.GetTopicUseCase
import com.eduvark.app.domain.usecase.SubmitCaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentCaseUiState(
    val isLoading: Boolean = true,
    val caseStudy: CaseStudy? = null,
    val topicTitle: String? = null,
    val materials: List<Material> = emptyList(),
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitError: String? = null,
)

@HiltViewModel
class StudentCaseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getStudentCaseUseCase: GetStudentCaseUseCase,
    private val getTopicUseCase: GetTopicUseCase,
    private val getMaterialsUseCase: GetMaterialsUseCase,
    private val submitCaseUseCase: SubmitCaseUseCase,
) : ViewModel() {

    private val topicId: String = savedStateHandle.get<String>("topicId").orEmpty()

    private val _state = MutableStateFlow(StudentCaseUiState())
    val state: StateFlow<StudentCaseUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (topicId.isBlank()) {
            _state.value = StudentCaseUiState(isLoading = false, error = "Topik tidak valid.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, submitError = null) }
            try {
                val caseStudy = getStudentCaseUseCase(topicId)
                val topicTitle = runCatching { getTopicUseCase(topicId).title }.getOrNull()
                // Supporting Kinesthetic materials are optional. A missing or
                // expired material must not hide the case study itself.
                val materials = runCatching { getMaterialsUseCase(topicId) }.getOrDefault(emptyList())
                _state.value = StudentCaseUiState(
                    isLoading = false,
                    caseStudy = caseStudy,
                    topicTitle = topicTitle,
                    materials = materials,
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

    fun submit(answerText: String) {
        val trimmed = answerText.trim()
        if (trimmed.isEmpty() || trimmed.length > 10000) {
            _state.update {
                it.copy(submitError = "Jawaban harus 1–10.000 karakter.")
            }
            return
        }
        if (_state.value.isSubmitting) return

        viewModelScope.launch {
            _state.update { it.copy(isSubmitting = true, submitError = null) }
            try {
                val result = submitCaseUseCase(topicId, trimmed)
                val current = _state.value.caseStudy
                _state.update {
                    it.copy(
                        isSubmitting = false,
                        caseStudy = current?.copy(
                            status = result.status,
                            submittedAt = result.submittedAt,
                        ),
                    )
                }
            } catch (error: AppFailure) {
                _state.update {
                    it.copy(isSubmitting = false, submitError = error.message)
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isSubmitting = false, submitError = "Gagal mengirim jawaban.")
                }
            }
        }
    }
}
