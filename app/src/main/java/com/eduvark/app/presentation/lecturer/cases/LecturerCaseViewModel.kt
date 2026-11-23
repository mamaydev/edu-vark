package com.eduvark.app.presentation.lecturer.cases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.CaseStudy
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.usecase.GetLecturerCaseUseCase
import com.eduvark.app.domain.usecase.GetLecturerMaterialsUseCase
import com.eduvark.app.domain.usecase.GetLecturerTopicUseCase
import com.eduvark.app.domain.usecase.UpsertCaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LecturerCaseUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val caseStudy: CaseStudy? = null,
    val topicId: String = "",
    val topicTitle: String? = null,
    val materials: List<Material> = emptyList(),
    val error: String? = null,
    val saveError: String? = null,
)

@HiltViewModel
class LecturerCaseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getLecturerCaseUseCase: GetLecturerCaseUseCase,
    private val getTopicUseCase: GetLecturerTopicUseCase,
    private val getMaterialsUseCase: GetLecturerMaterialsUseCase,
    private val upsertCaseUseCase: UpsertCaseUseCase,
) : ViewModel() {

    private val topicId: String = savedStateHandle.get<String>("topicId").orEmpty()

    private val _state = MutableStateFlow(LecturerCaseUiState())
    val state: StateFlow<LecturerCaseUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (topicId.isBlank()) {
            _state.value = LecturerCaseUiState(isLoading = false, error = "Topik tidak valid.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null, saveError = null) }
            try {
                // Context is optional; a missing case or material list must not
                // block the editor from creating the first case study.
                val caseStudy = runCatching { getLecturerCaseUseCase(topicId) }.getOrNull()
                val topicTitle = runCatching { getTopicUseCase(topicId).title }.getOrNull()
                val materials = runCatching { getMaterialsUseCase(topicId) }.getOrDefault(emptyList())
                _state.update {
                    it.copy(
                        isLoading = false,
                        caseStudy = caseStudy,
                        topicId = topicId,
                        topicTitle = topicTitle,
                        materials = materials,
                        error = null,
                    )
                }
            } catch (error: AppFailure) {
                _state.update { it.copy(isLoading = false, error = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Terjadi kesalahan. Coba lagi.")
                }
            }
        }
    }

    fun save(theory: String, instructions: String, deadlineAt: String) {
        if (instructions.trim().isEmpty()) {
            _state.update { it.copy(saveError = "Instruksi wajib diisi.") }
            return
        }
        if (deadlineAt.trim().isEmpty()) {
            _state.update { it.copy(saveError = "Deadline wajib diisi (ISO UTC).") }
            return
        }
        if (_state.value.isSaving) return

        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, saveError = null) }
            try {
                val caseStudy = upsertCaseUseCase(
                    topicId,
                    theory.trim().ifBlank { null },
                    instructions.trim(),
                    deadlineAt.trim(),
                )
                _state.update {
                    it.copy(isSaving = false, caseStudy = caseStudy, saveError = null)
                }
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, saveError = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isSaving = false, saveError = "Gagal menyimpan studi kasus.")
                }
            }
        }
    }
}
