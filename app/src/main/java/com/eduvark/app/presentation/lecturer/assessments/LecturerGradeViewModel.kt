package com.eduvark.app.presentation.lecturer.assessments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.core.text.P5TextRules
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.SubmissionDetail
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.domain.usecase.CreateAssessmentUseCase
import com.eduvark.app.domain.usecase.GetSubmissionUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LecturerGradeUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val detail: SubmissionDetail? = null,
    val error: String? = null,
    val saveError: String? = null,
)

@HiltViewModel
class LecturerGradeViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getSubmissionUseCase: GetSubmissionUseCase,
    private val createAssessmentUseCase: CreateAssessmentUseCase,
) : ViewModel() {

    private val submissionId: String = savedStateHandle.get<String>("submissionId").orEmpty()

    private val _state = MutableStateFlow(LecturerGradeUiState())
    val state: StateFlow<LecturerGradeUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (submissionId.isBlank()) {
            _state.value = LecturerGradeUiState(
                isLoading = false,
                error = "Submission tidak valid.",
            )
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, saveError = null) }
            try {
                val detail = getSubmissionUseCase(submissionId)
                _state.value = LecturerGradeUiState(isLoading = false, detail = detail)
            } catch (error: AppFailure) {
                _state.update { it.copy(isLoading = false, error = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Terjadi kesalahan. Coba lagi.")
                }
            }
        }
    }

    fun save(score: Int, comment: String?) {
        // FR-043: nilai integer 0–100, komentar opsional maksimal 1.000
        // karakter (code point setelah trim). API tetap menjadi otoritas akhir.
        if (score < 0 || score > 100) {
            _state.update { it.copy(saveError = "Nilai harus 0–100.") }
            return
        }
        if (!P5TextRules.isCommentLengthValid(comment.orEmpty())) {
            _state.update { it.copy(saveError = "Komentar maksimal 1.000 karakter.") }
            return
        }
        if (_state.value.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            try {
                val result = createAssessmentUseCase(
                    submissionId,
                    score,
                    comment?.trim()?.ifBlank { null },
                )
                val current = _state.value.detail
                _state.update {
                    it.copy(
                        isSaving = false,
                        detail = current?.copy(
                            status = SubmissionStatus.SUDAH_DINILAI,
                            score = result.score,
                            comment = result.comment,
                            assessedAt = result.assessedAt,
                        ),
                    )
                }
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, saveError = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isSaving = false, saveError = "Gagal menyimpan nilai.")
                }
            }
        }
    }
}
