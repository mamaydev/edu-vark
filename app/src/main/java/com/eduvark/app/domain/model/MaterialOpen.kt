package com.eduvark.app.domain.model

/**
 * Aksi membuka materi yang dihasilkan ViewModel dan dieksekusi UI.
 * Materi FILE memakai content URI sementara; materi LINK selalu dibuka lewat
 * URL tujuan yang sudah diperiksa izinnya oleh backend.
 */
sealed interface MaterialOpenEvent {
    data class OpenFile(val contentUri: String, val mime: String) : MaterialOpenEvent
    data class OpenExternal(val url: String) : MaterialOpenEvent
}

enum class MaterialOpenFailure {
    SESSION_EXPIRED,
    FORBIDDEN,
    NETWORK,
    GENERIC,
}
