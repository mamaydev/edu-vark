package com.eduvark.app.core.files

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Instrumentation (device/emulator): memastikan berkas sementara ditulis lewat
 * FileProvider, unik per unduhan meski judul sama, dan berkas parsial dihapus
 * ketika unduhan gagal/dibatalkan di tengah jalan.
 */
@RunWith(AndroidJUnit4::class)
class AndroidMaterialFileStoreTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val store = AndroidMaterialFileStore(context)

    @Test
    fun saveTempFileStreamsContentAndReturnsReadableUri() = runBlocking {
        val payload = ByteArray(64 * 1024) { (it % 251).toByte() }
        val contentUri = store.saveTempFile(
            materialId = "material-stream",
            fileName = "Materi Kinesthetic.pdf",
            mime = "application/pdf",
        ) { output -> output.write(payload) }

        val readBack = context.contentResolver.openInputStream(android.net.Uri.parse(contentUri))
            ?.use { it.readBytes() }
        assertTrue(contentUri.startsWith("content://"))
        assertEquals(payload.size, readBack?.size)
        assertEquals(payload.toList(), readBack?.toList())
    }

    @Test
    fun sameTitleDownloadsGetDistinctContentUris() = runBlocking {
        val first = store.saveTempFile("material-a", "Materi.pdf", "application/pdf") {
            it.write("pertama".toByteArray())
        }
        val second = store.saveTempFile("material-b", "Materi.pdf", "application/pdf") {
            it.write("kedua".toByteArray())
        }

        assertNotEquals(first, second)
        assertEquals(
            "pertama",
            context.contentResolver.openInputStream(android.net.Uri.parse(first))
                ?.use { it.readBytes() }?.toString(Charsets.UTF_8),
        )
        assertEquals(
            "kedua",
            context.contentResolver.openInputStream(android.net.Uri.parse(second))
                ?.use { it.readBytes() }?.toString(Charsets.UTF_8),
        )
    }

    @Test
    fun failedOrCancelledDownloadLeavesNoPartialFile() = runBlocking {
        val root = File(context.cacheDir, "materials")
        val before = root.listFiles()?.size ?: 0
        try {
            store.saveTempFile("material-fail", "Materi.pdf", "application/pdf") { output ->
                output.write("%PDF-1.7 partial".toByteArray())
                throw IOException("koneksi terputus")
            }
            fail("Harus melempar IOException")
        } catch (expected: IOException) {
            // sesuai kontrak: kegagalan diteruskan, berkas parsial dibersihkan
        }

        val after = root.listFiles()?.size ?: 0
        assertEquals(before, after)
    }
}
