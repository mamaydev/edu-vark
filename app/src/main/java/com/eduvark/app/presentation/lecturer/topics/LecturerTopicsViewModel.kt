package com.eduvark.app.presentation.lecturer.topics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.usecase.CreateTopicUseCase
import com.eduvark.app.domain.usecase.DeleteTopicUseCase
import com.eduvark.app.domain.usecase.GetTopicsUseCase
import com.eduvark.app.domain.usecase.PublishTopicUseCase
import com.eduvark.app.domain.usecase.ReorderTopicsUseCase
import com.eduvark.app.domain.usecase.UnpublishTopicUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LecturerTopicsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val topics: List<Topic> = emptyList(),
    val error: String? = null,
    val actionError: String? = null,
)

@HiltViewModel
class LecturerTopicsViewModel @Inject constructor(
    private val getTopicsUseCase: GetTopicsUseCase,
    private val createTopicUseCase: CreateTopicUseCase,
    private val publishTopicUseCase: PublishTopicUseCase,
    private val unpublishTopicUseCase: UnpublishTopicUseCase,
    private val deleteTopicUseCase: DeleteTopicUseCase,
    private val reorderTopicsUseCase: ReorderTopicsUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(LecturerTopicsUiState())
    val state: StateFlow<LecturerTopicsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load() {
        viewModelScope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            try {
                val topics = getTopicsUseCase()
                _state.value = LecturerTopicsUiState(isLoading = false, topics = topics)
            } catch (error: AppFailure) {
                _state.update { it.copy(isLoading = false, error = error.message) }
            } catch (error: Exception) {
                _state.update {
                    it.copy(isLoading = false, error = "Terjadi kesalahan. Coba lagi.")
                }
            }
        }
    }

    fun create(title: String, description: String) {
        if (title.trim().isEmpty()) {
            _state.update { it.copy(actionError = "Judul topik wajib diisi.") }
            return
        }
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            try {
                createTopicUseCase(title.trim(), description.trim().ifBlank { null })
                val topics = getTopicsUseCase()
                _state.update { it.copy(isSaving = false, topics = topics) }
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal menyimpan topik.") }
            }
        }
    }

    fun setPublished(topicId: String, published: Boolean) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            try {
                if (published) publishTopicUseCase(topicId) else unpublishTopicUseCase(topicId)
                val topics = getTopicsUseCase()
                _state.update { it.copy(isSaving = false, topics = topics) }
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal mengubah status.") }
            }
        }
    }

    fun delete(topicId: String) {
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            try {
                deleteTopicUseCase(topicId)
                val topics = getTopicsUseCase()
                _state.update { it.copy(isSaving = false, topics = topics) }
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal menghapus topik.") }
            }
        }
    }

    fun move(index: Int, delta: Int) {
        val topics = _state.value.topics.toMutableList()
        val target = index + delta
        if (index !in topics.indices || target !in topics.indices) return
        val moved = topics.removeAt(index)
        topics.add(target, moved)
        _state.update { it.copy(topics = topics, isSaving = true, actionError = null) }

        viewModelScope.launch {
            try {
                reorderTopicsUseCase(topics.map { it.id })
                _state.update { it.copy(isSaving = false) }
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
                load()
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal mengubah urutan.") }
                load()
            }
        }
    }

    fun clearActionError() {
        _state.update { it.copy(actionError = null) }
    }
}
