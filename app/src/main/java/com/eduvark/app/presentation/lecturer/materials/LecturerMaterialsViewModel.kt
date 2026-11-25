package com.eduvark.app.presentation.lecturer.materials

import androidx.annotation.StringRes
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
import com.eduvark.app.R
import com.eduvark.app.domain.usecase.GetLecturerMaterialsUseCase
import com.eduvark.app.domain.model.FileDeletionStatus
import com.eduvark.app.domain.usecase.GetLecturerTopicUseCase
import com.eduvark.app.domain.usecase.ReplaceMaterialFileUseCase
import com.eduvark.app.domain.usecase.UploadMaterialUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Pemberitahuan status fisik berkas setelah aksi materi berhasil. Teks berasal
 * dari resource agar setiap status dijelaskan sesuai janjinya: retry otomatis
 * HANYA untuk [FileDeletionStatus.PENDING].
 */
data class FileDeletionNotice(
    val status: FileDeletionStatus,
    @StringRes val titleRes: Int,
    @StringRes val messageRes: Int,
)

data class LecturerMaterialsUiState(
    val isLoading: Boolean = true,
    val isSaving: Boolean = false,
    val topic: Topic? = null,
    val materials: List<Material> = emptyList(),
    val error: String? = null,
    val actionError: String? = null,
    /** Status fisik berkas terakhir; tidak pernah berarti aksi materi gagal. */
    val fileDeletionNotice: FileDeletionNotice? = null,
)

private const val MAX_TITLE_LENGTH = 200

/**
 * Perubahan materi sudah commit; kegagalan hanya pada pemuatan ulang daftar.
 * Pesan ini tidak boleh menyiratkan bahwa hapus/ganti gagal.
 */
private const val COMMITTED_REFRESH_ERROR =
    "Perubahan sudah tersimpan, tetapi daftar materi gagal dimuat ulang. " +
        "Muat ulang halaman untuk melihat data terbaru."

private enum class FileDeletionAction { DELETE, REPLACE }

private fun fileDeletionNotice(
    status: FileDeletionStatus,
    action: FileDeletionAction,
): FileDeletionNotice? {
    val delete = action == FileDeletionAction.DELETE
    return when (status) {
        FileDeletionStatus.NONE,
        FileDeletionStatus.COMPLETED,
        -> null

        FileDeletionStatus.PENDING -> if (delete) {
            FileDeletionNotice(
                status = status,
                titleRes = R.string.lec_material_file_pending_title,
                messageRes = R.string.lec_material_file_pending_body,
            )
        } else {
            FileDeletionNotice(
                status = status,
                titleRes = R.string.lec_material_file_replace_pending_title,
                messageRes = R.string.lec_material_file_replace_pending_body,
            )
        }

        FileDeletionStatus.SKIPPED -> if (delete) {
            FileDeletionNotice(
                status = status,
                titleRes = R.string.lec_material_file_skipped_title,
                messageRes = R.string.lec_material_file_skipped_body,
            )
        } else {
            FileDeletionNotice(
                status = status,
                titleRes = R.string.lec_material_file_replace_skipped_title,
                messageRes = R.string.lec_material_file_replace_skipped_body,
            )
        }

        FileDeletionStatus.FAILED -> if (delete) {
            FileDeletionNotice(
                status = status,
                titleRes = R.string.lec_material_file_failed_title,
                messageRes = R.string.lec_material_file_failed_body,
            )
        } else {
            FileDeletionNotice(
                status = status,
                titleRes = R.string.lec_material_file_replace_failed_title,
                messageRes = R.string.lec_material_file_replace_failed_body,
            )
        }

        FileDeletionStatus.UNKNOWN -> if (delete) {
            FileDeletionNotice(
                status = status,
                titleRes = R.string.lec_material_file_unknown_title,
                messageRes = R.string.lec_material_file_unknown_body,
            )
        } else {
            FileDeletionNotice(
                status = status,
                titleRes = R.string.lec_material_file_replace_unknown_title,
                messageRes = R.string.lec_material_file_replace_unknown_body,
            )
        }
    }
}

@HiltViewModel
class LecturerMaterialsViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val getTopicUseCase: GetLecturerTopicUseCase,
    private val getMaterialsUseCase: GetLecturerMaterialsUseCase,
    private val createLinkMaterialUseCase: CreateLinkMaterialUseCase,
    private val uploadMaterialUseCase: UploadMaterialUseCase,
    private val deleteMaterialUseCase: DeleteMaterialUseCase,
    private val replaceMaterialFileUseCase: ReplaceMaterialFileUseCase,
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
        val cleanTitle = title.trim()
        validateTitle(cleanTitle)?.let { message ->
            _state.update { it.copy(actionError = message) }
            return
        }
        if (url.trim().isEmpty()) {
            _state.update { it.copy(actionError = "Judul dan URL wajib diisi.") }
            return
        }
        if (!url.trim().startsWith("https://")) {
            _state.update { it.copy(actionError = "URL harus menggunakan HTTPS.") }
            return
        }
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            try {
                createLinkMaterialUseCase(topicId, cleanTitle, vark, url.trim())
                refreshAfterChange()
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal menambah materi.") }
            }
        }
    }

    fun upload(title: String, vark: VarkType, payload: UploadPayload) {
        val cleanTitle = title.trim()
        validateTitle(cleanTitle)?.let { message ->
            _state.update { it.copy(actionError = message) }
            return
        }
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            try {
                uploadMaterialUseCase(topicId, cleanTitle, vark, payload)
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
            val deletion = try {
                deleteMaterialUseCase(materialId)
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
                return@launch
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal menghapus materi.") }
                return@launch
            }
            // Aksi materi sudah commit; hasil ini tidak boleh dibatalkan oleh
            // kegagalan pemuatan ulang daftar di bawah.
            _state.update {
                it.copy(
                    isSaving = false,
                    fileDeletionNotice = fileDeletionNotice(deletion, FileDeletionAction.DELETE),
                )
            }
            refreshAfterCommittedChange()
        }
    }

    /**
     * Ganti berkas pada materi FILE yang sama. Bila penggantian berhasil tetapi
     * objek lama belum terhapus, berkas baru tetap aktif dan UI menjelaskan
     * status fisiknya sesuai hasil backend.
     */
    fun replaceFile(materialId: String, payload: UploadPayload) {
        if (_state.value.isSaving) return
        viewModelScope.launch {
            _state.update { it.copy(isSaving = true, actionError = null) }
            val result = try {
                replaceMaterialFileUseCase(materialId, payload)
            } catch (error: AppFailure) {
                _state.update { it.copy(isSaving = false, actionError = error.message) }
                return@launch
            } catch (error: Exception) {
                _state.update { it.copy(isSaving = false, actionError = "Gagal mengganti berkas.") }
                return@launch
            }
            _state.update {
                it.copy(
                    isSaving = false,
                    fileDeletionNotice = fileDeletionNotice(
                        result.previousFileDeletion,
                        FileDeletionAction.REPLACE,
                    ),
                )
            }
            refreshAfterCommittedChange()
        }
    }

    fun clearFileDeletionNotice() {
        _state.update { it.copy(fileDeletionNotice = null) }
    }

    fun reportError(message: String) {
        _state.update { it.copy(actionError = message, isSaving = false) }
    }

    private fun validateTitle(title: String): String? = when {
        title.isEmpty() -> "Judul materi wajib diisi."
        title.length > MAX_TITLE_LENGTH ->
            "Judul materi maksimal $MAX_TITLE_LENGTH karakter."

        else -> null
    }

    private suspend fun refreshAfterChange() {
        val materials = getMaterialsUseCase(topicId)
        _state.update { it.copy(isSaving = false, materials = materials) }
    }

    /** Pemuatan ulang setelah commit; kegagalannya tidak membatalkan aksi materi. */
    private suspend fun refreshAfterCommittedChange() {
        try {
            val materials = getMaterialsUseCase(topicId)
            _state.update { it.copy(materials = materials) }
        } catch (error: AppFailure) {
            _state.update { it.copy(actionError = COMMITTED_REFRESH_ERROR) }
        } catch (error: Exception) {
            _state.update { it.copy(actionError = COMMITTED_REFRESH_ERROR) }
        }
    }
}
