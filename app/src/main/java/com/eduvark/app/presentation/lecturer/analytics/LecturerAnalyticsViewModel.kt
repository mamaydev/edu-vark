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

/** Kategori feedback subjektif yang ditampilkan pada analitik dosen. */
val LECTURER_FEEDBACK_CATEGORIES = listOf(
    VarkType.VISUAL,
    VarkType.AURAL,
    VarkType.READ_WRITE,
)

/**
 * Statistik per kategori asal feedback. Nilai dihitung dari nilai feedback
 * tersimpan per kategori asal, bukan dari satu label VARK mahasiswa.
 *
 * `respondents` hanya menghitung nilai non-null: nilai 0% tetap dihitung sebagai
 * respons, sedangkan mahasiswa tanpa feedback (`null`) tidak dihitung.
 */
data class LecturerCategoryStat(
    val category: VarkType,
    val respondents: Int,
    val mean: Double?,
)

data class LecturerAnalyticsUiState(
    val isLoading: Boolean = true,
    val analytics: TopicAnalytics? = null,
    val allStudents: List<StudentFeedback> = emptyList(),
    val selectedCategory: VarkType? = null,
    val selectedStudentId: String? = null,
    val error: String? = null,
) {
    /** Statistik V/A/R dari nilai tersimpan per kategori asal. */
    val categoryStats: List<LecturerCategoryStat> = LECTURER_FEEDBACK_CATEGORIES.map { category ->
        val values = allStudents.mapNotNull { it.valueFor(category) }
        LecturerCategoryStat(
            category = category,
            respondents = values.size,
            mean = if (values.isEmpty()) null else values.average(),
        )
    }

    val hasFeedback: Boolean = categoryStats.any { it.respondents > 0 }

    val totalStudentCount: Int = allStudents.size

    /** Jumlah responden resmi dari backend untuk cakupan filter aktif. */
    val respondentCount: Int = analytics?.respondentCount ?: 0

    /**
     * CATATAN: `TopicAnalytics.notRespondedCount` dan
     * `KinestheticAnalytics.notSubmittedCount` tetap ada di model domain/DTO
     * (kontrak API dipertahankan), tetapi SENGAJA tidak diekspos sebagai
     * properti UI. UI dosen tidak boleh merender total "belum merespons" /
     * "belum mengumpulkan"; semantik 0% vs belum merespons cukup diwakili
     * nilai `null` per mahasiswa di [visibleStudents].
     */

    /** Definisi agregasi yang dikirim backend; wajib ditampilkan ke dosen. */
    val aggregationDefinition: String? = analytics?.aggregation

    val kinestheticAssessedCount: Int = analytics?.kinesthetic?.assessedCount ?: 0

    val kinestheticMeanScore: Double? = analytics?.kinesthetic?.meanScore

    /**
     * Filter daftar memakai KATEGORI ASAL nilai tersimpan (FR-030), bukan satu
     * label VARK mahasiswa. Tanpa filter semua mahasiswa ditampilkan agar
     * "belum merespons" (null) tetap terlihat dan tidak dianggap 0%.
     */
    val visibleStudents: List<StudentFeedback> = run {
        val category = selectedCategory
        if (category == null) {
            allStudents
        } else {
            allStudents.filter { it.respondedIn(category) }
        }
    }
}

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
