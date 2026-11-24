package com.eduvark.app.core.files

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.domain.repository.MaterialContentSource
import java.io.OutputStream
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/**
 * Mengunduh isi materi lewat alamat tetap terautentikasi dan menyalinnya secara
 * streaming ke [OutputStream]. Cancellation diperiksa di antara potongan data agar
 * pembatalan tidak menunggu seluruh berkas selesai.
 */
@Singleton
class HttpMaterialContentSource @Inject constructor(
    private val api: EduVarkApi,
    private val executor: ApiExecutor,
) : MaterialContentSource {

    override suspend fun stream(materialId: String, target: OutputStream) {
        executor.execute {
            api.downloadMaterialContent(materialId).use { body ->
                body.byteStream().use { input ->
                    val buffer = ByteArray(COPY_BUFFER_BYTES)
                    while (true) {
                        currentCoroutineContext().ensureActive()
                        val read = input.read(buffer)
                        if (read < 0) break
                        target.write(buffer, 0, read)
                    }
                }
            }
        }
    }

    private companion object {
        const val COPY_BUFFER_BYTES = 16 * 1024
    }
}
