package com.eduvark.app.core.files

import android.content.Context
import androidx.core.content.FileProvider
import com.eduvark.app.domain.repository.MaterialFileStore
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@Singleton
class AndroidMaterialFileStore @Inject constructor(
    @ApplicationContext private val context: Context,
) : MaterialFileStore {

    override suspend fun saveTempFile(
        materialId: String,
        fileName: String,
        mime: String,
        writeContent: suspend (OutputStream) -> Unit,
    ): String = withContext(Dispatchers.IO) {
        val root = File(context.cacheDir, CACHE_DIRECTORY)
        root.mkdirs()
        pruneOldEntries(root)

        // Direktori unik per unduhan: dua materi dengan judul sama tidak berbagi URI,
        // dan unduhan baru tidak menimpa berkas yang mungkin masih dibuka viewer.
        val downloadDirectory = File(root, materialId + "-" + UUID.randomUUID())
        downloadDirectory.mkdirs()
        val file = File(downloadDirectory, safeFileName(fileName))
        try {
            FileOutputStream(file).use { output ->
                writeContent(output)
                output.flush()
            }
            if (file.length() <= 0L) {
                throw IOException("Isi materi kosong")
            }
            FileProvider.getUriForFile(
                context,
                context.packageName + FILE_PROVIDER_SUFFIX,
                file,
            ).toString()
        } catch (error: Throwable) {
            // Unduhan gagal/dibatalkan: jangan tinggalkan berkas parsial.
            runCatching { file.delete() }
            runCatching { downloadDirectory.delete() }
            throw error
        }
    }

    /**
     * Batasi cache dengan mempertimbangkan viewer yang mungkin masih memakai berkas:
     * hanya entri yang lebih tua dari [MAX_AGE_MILLIS] atau di luar [MAX_ENTRIES]
     * terbaru yang dibuang, sehingga unduhan yang baru dibuka tetap aman.
     */
    private fun pruneOldEntries(root: File) {
        val entries = root.listFiles()?.filter { it.isDirectory } ?: return
        val now = System.currentTimeMillis()
        entries
            .sortedByDescending { it.lastModified() }
            .forEachIndexed { index, directory ->
                val tooOld = now - directory.lastModified() > MAX_AGE_MILLIS
                if (tooOld || index >= MAX_ENTRIES) {
                    directory.deleteRecursively()
                }
            }
    }

    private fun safeFileName(fileName: String): String = fileName
        .replace(Regex("[^A-Za-z0-9._ -]"), "_")
        .trim()
        .ifBlank { "materi" }
        .take(MAX_FILE_NAME)

    private companion object {
        const val CACHE_DIRECTORY = "materials"
        const val FILE_PROVIDER_SUFFIX = ".fileprovider"
        const val MAX_FILE_NAME = 64
        const val MAX_ENTRIES = 10
        const val MAX_AGE_MILLIS = 24L * 60L * 60L * 1000L
    }
}
