package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.repository.MaterialFileStore
import java.io.ByteArrayOutputStream
import java.io.OutputStream

class FakeMaterialFileStore : MaterialFileStore {

    data class SavedFile(
        val materialId: String,
        val fileName: String,
        val mime: String,
        val bytes: ByteArray,
    )

    val savedFiles: MutableList<SavedFile> = mutableListOf()
    var failure: AppFailure? = null
    /** Jumlah unduhan yang dimulai, termasuk yang gagal/dibatalkan. */
    var saveAttempts: Int = 0

    override suspend fun saveTempFile(
        materialId: String,
        fileName: String,
        mime: String,
        writeContent: suspend (OutputStream) -> Unit,
    ): String {
        saveAttempts += 1
        failure?.let { throw it }
        val buffer = ByteArrayOutputStream()
        writeContent(buffer)
        savedFiles += SavedFile(materialId, fileName, mime, buffer.toByteArray())
        return "content://com.eduvark.app.fileprovider/materials/$materialId/$fileName"
    }
}
