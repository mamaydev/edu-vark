package com.eduvark.app.presentation.lecturer.materials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.UploadPayload
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.CreateLinkMaterialUseCase
import com.eduvark.app.domain.usecase.DeleteMaterialUseCase
import com.eduvark.app.domain.usecase.GetMaterialsUseCase
import com.eduvark.app.domain.usecase.GetTopicUseCase
import com.eduvark.app.domain.usecase.UploadMaterialUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LecturerMaterialsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val topic: Topic? = null,
    val materials: List<Material> = emptyList(),
    val error: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class LecturerMaterialsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTopicUseCase: GetTopicUseCase,
    private val getMaterialsUseCase: GetMaterialsUseCase,
    private val createLinkMaterialUseCase: CreateLinkMaterialUseCase,
    private val uploadMaterialUseCase: UploadMaterialUseCase,
    private val deleteMaterialUseCase: DeleteMaterialUseCase,
) : ViewModel() {

    private val topicId: String = savedStateHandle.get<String>("topicId").orEmpty()

    private val _state = MutableStateFlow(LecturerMaterialsUiState())
    val state: StateFlow<LecturerMaterialsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        if (topicId.isBlank()) {
            _state.value = LecturerMaterialsUiState(isLoading = false, error = "Topik tidak valid.")
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val topic = getTopicUseCase(topicId)
                val materials = getMaterialsUseCase(topicId)
                _state.value = LecturerMaterialsUiState(
                    isLoading = false,
                    topic = topic,
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

    fun addLink(title: String, vark: VarkType, url: String) {
        if (title.trim().isEmpty() || url.trim().isEmpty()) {
            _state.update { it.copy(actionError = "Judul dan URL wajib diisi.") }
            return
        }
        if (!url.trim().startsWith("https://")) {
            _state.update { it.copy(actionError = "URL harus menggunakan HTTPS.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            try {
                createLinkMaterialUseCase(topicId, title.trim(), vark, url.trim())
                refreshAfterChange()
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal menambah materi.") }
            }
        }
    }

    fun upload(title: String, vark: VarkType, payload: UploadPayload) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            try {
                uploadMaterialUseCase(topicId, title, vark, payload)
                refreshAfterChange()
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal mengunggah materi.") }
            }
        }
    }

    fun delete(materialId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            try {
                deleteMaterialUseCase(materialId)
                refreshAfterChange()
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal menghapus materi.") }
            }
        }
    }

    fun reportError(message: String) {
        _state.update { it.copy(actionError = message, isSaving = false) }
    }

    private suspend fun refreshAfterChange() {
        val materials = getMaterialsUseCase(topicId)
        _state.update { it.copy(isSaving = false, materials = materials) }
    }
}
