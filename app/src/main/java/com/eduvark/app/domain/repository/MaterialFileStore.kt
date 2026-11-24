package com.eduvark.app.domain.repository

import java.io.OutputStream

/**
 * Penyimpanan sementara untuk membuka berkas materi lewat aplikasi eksternal.
 * Isi materi privat tidak boleh dipakai ulang tanpa permintaan/otorisasi baru,
 * sehingga implementasi wajib membersihkan berkas sementara dan tidak mencampur
 * dua unduhan meskipun judul materinya sama.
 */
interface MaterialFileStore {
    /**
     * Buat berkas sementara lalu tulis isi materi secara STREAMING lewat [writeContent]
     * (tanpa menampung seluruh byte di memori). Kembalikan content URI yang bisa dibuka.
     * Implementasi wajib menghapus berkas parsial bila penulisan gagal/dibatalkan.
     */
    suspend fun saveTempFile(
        materialId: String,
        fileName: String,
        mime: String,
        writeContent: suspend (OutputStream) -> Unit,
    ): String
}
