package com.eduvark.app.presentation.lecturer.analytics

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.StudentFeedback
import com.eduvark.app.domain.model.TopicAnalytics
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.usecase.GetTopicAnalyticsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class LecturerAnalyticsUiState(
    val isLoading: Boolean = true,
    val analytics: TopicAnalytics? = null,
    val allStudents: List<StudentFeedback> = emptyList(),
    val selectedCategory: VarkType? = null,
    val selectedStudentId: String? = null,
    val error: String? = null,
)

@HiltViewModel
class LecturerAnalyticsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTopicAnalyticsUseCase: GetTopicAnalyticsUseCase,
) : ViewModel() {

    private val topicId: String = savedStateHandle.get<String>("topicId").orEmpty()

    private val _state = MutableStateFlow(LecturerAnalyticsUiState())
    val state: StateFlow<LecturerAnalyticsUiState> = _state.asStateFlow()

    init {
        load()
    }

    fun load(
        category: VarkType? = _state.value.selectedCategory,
        studentId: String? = _state.value.selectedStudentId,
    ) {
        if (topicId.isBlank()) {
            _state.value = LecturerAnalyticsUiState(
                isLoading = false,
                error = "Topik tidak valid.",
            )
            return
        }
        viewModelScope.launch {
            _state.update {
                it.copy(
                    isLoading = true,
                    error = null,
                    selectedCategory = category,
                    selectedStudentId = studentId,
                )
            }
            try {
                val analytics = getTopicAnalyticsUseCase(topicId, category, studentId)
                val allStudents = if (studentId == null) {
                    analytics.students
                } else {
                    _state.value.allStudents.ifEmpty { analytics.students }
                }
                _state.update {
                    it.copy(
                        isLoading = false,
                        analytics = analytics,
                        allStudents = allStudents,
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

    fun selectCategory(category: VarkType?) {
        load(category, null)
    }

    fun selectStudent(studentId: String?) {
        load(_state.value.selectedCategory, studentId)
    }
}
