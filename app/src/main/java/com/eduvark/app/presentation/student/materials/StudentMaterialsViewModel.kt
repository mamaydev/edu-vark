package com.eduvark.app.presentation.student.materials

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.MaterialKind
import com.eduvark.app.domain.model.MaterialOpenEvent
import com.eduvark.app.domain.model.MaterialOpenFailure
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.MaterialFileStore
import com.eduvark.app.domain.usecase.DownloadMaterialContentUseCase
import com.eduvark.app.domain.usecase.GetFeedbackUseCase
import com.eduvark.app.domain.usecase.GetMaterialLinkTargetUseCase
import com.eduvark.app.domain.usecase.GetMaterialsUseCase
import com.eduvark.app.domain.usecase.GetMeUseCase
import com.eduvark.app.domain.usecase.GetTopicUseCase
import com.eduvark.app.domain.usecase.SaveFeedbackUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.CancellationException
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
    /** ID materi yang sedang diunduh/dibuka; null berarti tidak ada proses. */
    val openingMaterialId: String? = null,
    /** Aksi buka materi yang menunggu dieksekusi UI. */
    val openEvent: MaterialOpenEvent? = null,
    /** Kegagalan membuka materi yang perlu ditampilkan ke pengguna. */
    val openFailure: MaterialOpenFailure? = null,
    /** Sesi berakhir saat membuka materi: UI harus kembali ke Login. */
    val sessionExpired: Boolean = false,
)

@HiltViewModel
class StudentMaterialsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTopicUseCase: GetTopicUseCase,
    private val getMaterialsUseCase: GetMaterialsUseCase,
    private val getMeUseCase: GetMeUseCase,
    private val getFeedbackUseCase: GetFeedbackUseCase,
    private val saveFeedbackUseCase: SaveFeedbackUseCase,
    private val downloadMaterialContentUseCase: DownloadMaterialContentUseCase,
    private val getMaterialLinkTargetUseCase: GetMaterialLinkTargetUseCase,
    private val materialFileStore: MaterialFileStore,
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
                val materials = try {
                    getMaterialsUseCase(topicId)
                } catch (error: AppFailure.Api) {
                    // A published topic without uploads is a designed empty state, not a failure.
                    if (error.code == "MATERIAL_NOT_AVAILABLE") {
                        emptyList()
                    } else {
                        throw error
                    }
                }
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

    /**
     * Buka materi: berkas diunduh ulang lewat alamat tetap terautentikasi lalu
     * dibuka dari berkas sementara; tautan eksternal memakai tujuan yang baru
     * diperiksa izinnya. Tidak ada isi materi yang dipakai ulang antar-percobaan.
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
                        // Streaming: byte ditulis langsung ke berkas sementara, tidak
                        // ditampung seluruhnya di memori (berkas besar tetap aman).
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
                // Layar ditutup/permintaan dibatalkan: bukan kegagalan yang ditampilkan.
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

    /** Aplikasi penampil tidak tersedia/gagal dibuka setelah unduhan berhasil. */
    fun onOpenDispatchFailed() {
        _state.update {
            it.copy(openFailure = MaterialOpenFailure.GENERIC)
        }
    }

    fun onOpenFailureDismissed() {
        _state.update { it.copy(openFailure = null) }
    }

    fun onSessionExpiredHandled() {
        _state.update { it.copy(sessionExpired = false) }
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

    private fun tempFileName(material: Material, mime: String): String {
        val base = material.title
            .trim()
            .take(MAX_FILE_NAME_BASE)
            .ifBlank { "materi" }
            .trimEnd('.')
        val extension = extensionFor(mime)
        return base + "." + extension
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
