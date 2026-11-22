package com.eduvark.app.presentation.lecturer.assessments

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.CaseSubmissionSummary
import com.eduvark.app.domain.usecase.ListCaseSubmissionsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LecturerSubmissionsUiState(
    val isLoading: Boolean = true,
    val submissions: List<CaseSubmissionSummary> = emptyList(),
    val error: String? = null,
)

@HiltViewModel
class LecturerSubmissionsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val listCaseSubmissionsUseCase: ListCaseSubmissionsUseCase,
) : ViewModel() {

    private val caseId: String = savedStateHandle.get<String>("caseId").orEmpty()

    private val _state = MutableStateFlow(LecturerSubmissionsUiState())
    val state: StateFlow<LecturerSubmissionsUiState> = _state.asStateFlow()

    fun load() {
        if (caseId.isBlank()) {
            _state.value = LecturerSubmissionsUiState(
                isLoading = false,
                error = "Studi kasus tidak valid.",
            )
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val rows = listCaseSubmissionsUseCase(caseId)
                _state.value = LecturerSubmissionsUiState(
                    isLoading = false,
                    submissions = rows,
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
