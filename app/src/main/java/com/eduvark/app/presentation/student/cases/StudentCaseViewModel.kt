package com.eduvark.app.presentation.student.cases

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.core.text.P5TextRules
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.CaseStudy
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.MaterialKind
import com.eduvark.app.domain.model.MaterialOpenEvent
import com.eduvark.app.domain.model.MaterialOpenFailure
import com.eduvark.app.domain.repository.MaterialFileStore
import com.eduvark.app.domain.usecase.DownloadMaterialContentUseCase
import com.eduvark.app.domain.usecase.GetMaterialLinkTargetUseCase
import com.eduvark.app.domain.usecase.GetMaterialsUseCase
import com.eduvark.app.domain.usecase.GetStudentCaseUseCase
import com.eduvark.app.domain.usecase.GetTopicUseCase
import com.eduvark.app.domain.usecase.SubmitCaseUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class StudentCaseUiState(
    val isLoading: Boolean = true,
    val caseStudy: CaseStudy? = null,
    val topicTitle: String? = null,
    val topicOrder: Int? = null,
    val materials: List<Material> = emptyList(),
    /**
     * C1-03: kegagalan memuat materi pendukung harus bisa dibedakan dari
     * kondisi "materi memang belum tersedia". Jangan digabung jadi empty list.
     */
    val materialsFailed: Boolean = false,
    val isSubmitting: Boolean = false,
    val error: String? = null,
    val submitError: String? = null,
    /** ID materi pendukung yang sedang diunduh/dibuka. */
    val openingMaterialId: String? = null,
    /** Aksi buka materi yang menunggu dieksekusi UI. */
    val openEvent: MaterialOpenEvent? = null,
    /** Kegagalan membuka materi pendukung (403/404 take-down, jaringan, server). */
    val openFailure: MaterialOpenFailure? = null,
    /** Sesi berakhir saat membuka materi: UI harus kembali ke Login. */
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class StudentCaseViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getStudentCaseUseCase: GetStudentCaseUseCase,
    private val getTopicUseCase: GetTopicUseCase,
    private val getMaterialsUseCase: GetMaterialsUseCase,
    private val submitCaseUseCase: SubmitCaseUseCase,
    private val downloadMaterialContentUseCase: DownloadMaterialContentUseCase,
    private val getMaterialLinkTargetUseCase: GetMaterialLinkTargetUseCase,
    private val materialFileStore: MaterialFileStore,
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
                val topic = runCatching { getTopicUseCase(topicId) }.getOrNull()
                val topicTitle = topic?.title
                // Supporting Kinesthetic materials are optional. A missing or
                // expired material must not hide the case study itself, but a
                // failed fetch must stay distinguishable from "empty".
                var materials: List<Material> = emptyList()
                var materialsFailed = false
                try {
                    materials = getMaterialsUseCase(topicId)
                } catch (error: AppFailure.Api) {
                    // Kontrak 404 materi belum tersedia = empty state, bukan error.
                    materialsFailed = error.code != "MATERIAL_NOT_AVAILABLE"
                } catch (error: Exception) {
                    materialsFailed = true
                }
                _state.value = StudentCaseUiState(
                    isLoading = false,
                    caseStudy = caseStudy,
                    topicTitle = topicTitle,
                    topicOrder = topic?.sortOrder,
                    materials = materials,
                    materialsFailed = materialsFailed,
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

    /** Coba lagi memuat materi pendukung tanpa menyembunyikan studi kasus. */
    fun reloadMaterials() {
        val current = _state.value
        if (current.isLoading || current.caseStudy == null) return
        viewModelScope.launch {
            _state.update { it.copy(materialsFailed = false) }
            try {
                val materials = getMaterialsUseCase(topicId)
                _state.update {
                    it.copy(materials = materials, materialsFailed = false)
                }
            } catch (error: AppFailure.Api) {
                _state.update {
                    it.copy(
                        materials = emptyList(),
                        materialsFailed = error.code != "MATERIAL_NOT_AVAILABLE",
                    )
                }
            } catch (error: Exception) {
                _state.update { it.copy(materials = emptyList(), materialsFailed = true) }
            }
        }
    }

    /**
     * Buka materi pendukung Kinesthetic dengan alur yang sama seperti layar materi
     * V/A/R: FILE diunduh via alamat tetap terautentikasi lalu dibuka dari berkas
     * sementara; LINK memakai tujuan yang baru diperiksa izinnya.
     */
    fun openMaterial(material: Material) {
        if (_state.value.openingMaterialId != null) return
        viewModelScope.launch {
            _state.update {
                it.copy(openingMaterialId = material.id, openFailure = null)
            }
            try {
                when (material.kind) {
                    MaterialKind.FILE -> {
                        val mime = material.mime ?: DEFAULT_MIME
                        val contentUri = materialFileStore.saveTempFile(
                            materialId = material.id,
                            fileName = tempFileName(material, mime),
                            mime = mime,
                        ) { output ->
                            downloadMaterialContentUseCase(material.id, output)
                        }
                        _state.update {
                            it.copy(
                                openingMaterialId = null,
                                openEvent = MaterialOpenEvent.OpenFile(contentUri, mime),
                            )
                        }
                    }

                    MaterialKind.LINK -> {
                        val url = getMaterialLinkTargetUseCase(material.id)
                        _state.update {
                            if (url.startsWith("https://", ignoreCase = true)) {
                                it.copy(
                                    openingMaterialId = null,
                                    openEvent = MaterialOpenEvent.OpenExternal(url),
                                )
                            } else {
                                it.copy(
                                    openingMaterialId = null,
                                    openFailure = MaterialOpenFailure.FORBIDDEN,
                                )
                            }
                        }
                    }
                }
            } catch (cancel: CancellationException) {
                _state.update { it.copy(openingMaterialId = null) }
                throw cancel
            } catch (error: AppFailure.Api) {
                _state.update {
                    it.copy(
                        openingMaterialId = null,
                        sessionExpired = error.statusCode == 401 || it.sessionExpired,
                        openFailure = if (error.statusCode == 401) {
                            null
                        } else if (error.statusCode == 403 || error.statusCode == 404) {
                            MaterialOpenFailure.FORBIDDEN
                        } else {
                            MaterialOpenFailure.GENERIC
                        },
                    )
                }
            } catch (error: AppFailure.Network) {
                _state.update {
                    it.copy(
                        openingMaterialId = null,
                        openFailure = MaterialOpenFailure.NETWORK,
                    )
                }
            } catch (error: Exception) {
                _state.update {
                    it.copy(
                        openingMaterialId = null,
                        openFailure = MaterialOpenFailure.GENERIC,
                    )
                }
            }
        }
    }

    fun onOpenEventConsumed() {
        _state.update { it.copy(openEvent = null) }
    }

    fun onOpenDispatchFailed() {
        _state.update { it.copy(openFailure = MaterialOpenFailure.GENERIC) }
    }

    fun onSessionExpiredHandled() {
        _state.update { it.copy(sessionExpired = false) }
    }

    fun submit(answerText: String) {
        // C1-04: pembersihan & satuan code point sama dengan backend & PostgreSQL.
        val trimmed = P5TextRules.normalizeAnswerText(answerText)
        if (!P5TextRules.isAnswerLengthValid(trimmed)) {
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

    private fun tempFileName(material: Material, mime: String): String {
        val base = material.title
            .trim()
            .take(MAX_FILE_NAME_BASE)
            .ifBlank { "materi" }
            .trimEnd('.')
        return base + "." + extensionFor(mime)
    }

    private fun extensionFor(mime: String): String = when (mime) {
        "application/pdf" -> "pdf"
        "image/png" -> "png"
        "image/jpeg" -> "jpg"
        "image/gif" -> "gif"
        "audio/mpeg" -> "mp3"
        "audio/mp4" -> "m4a"
        "audio/wav" -> "wav"
        "audio/ogg" -> "ogg"
        "video/mp4" -> "mp4"
        "video/webm" -> "webm"
        else -> "bin"
    }

    private companion object {
        const val DEFAULT_MIME = "application/octet-stream"
        const val MAX_FILE_NAME_BASE = 60
    }
}
