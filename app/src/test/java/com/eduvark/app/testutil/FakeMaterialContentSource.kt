package com.eduvark.app.testutil

import com.eduvark.app.domain.repository.MaterialContentSource
import java.io.OutputStream

/**
 * Sumber isi materi palsu. Bila diberi [repository], ia membaca/merekam state
 * unduhan yang sama dengan fake repository agar test lama tetap valid.
 */
class FakeMaterialContentSource(
    private val repository: FakeTopicRepository? = null,
) : MaterialContentSource {

    var bytes: ByteArray = ByteArray(0)
    var failure: Throwable? = null
    val streamedMaterialIds: MutableList<String> = mutableListOf()

    override suspend fun stream(materialId: String, target: OutputStream) {
        streamedMaterialIds += materialId
        if (repository != null) {
            repository.downloadedMaterialIds += materialId
            repository.downloadFailure?.let { throw it }
            target.write(repository.downloadedContent)
            return
        }
        failure?.let { throw it }
        target.write(bytes)
    }
}
