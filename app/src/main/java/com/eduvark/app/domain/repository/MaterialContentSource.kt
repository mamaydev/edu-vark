package com.eduvark.app.domain.repository

import java.io.OutputStream

/**
 * Sumber isi materi privat yang selalu meminta ulang ke backend EduVARK dengan
 * otorisasi per permintaan (FR-007/FR-015). Tidak pernah memakai URL storage langsung.
 */
interface MaterialContentSource {
    /** Streaming isi materi ke [target]; dibatalkan lewat pembatalan coroutine. */
    suspend fun stream(materialId: String, target: OutputStream)
}
