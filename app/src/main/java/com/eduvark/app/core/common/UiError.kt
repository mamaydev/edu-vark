package com.eduvark.app.core.common

sealed interface UiError {
    data object Network : UiError
    data class Http(val statusCode: Int, val serverMessage: String?) : UiError
    data class Unexpected(val detail: String?) : UiError
}

fun UiError.toUserMessage(): String = when (this) {
    UiError.Network -> "Tidak dapat terhubung ke server. Periksa koneksi dan coba lagi."
    is UiError.Http -> serverMessage?.takeIf { it.isNotBlank() }
        ?: "Permintaan ditolak server (HTTP $statusCode)."
    is UiError.Unexpected -> "Terjadi kesalahan tak terduga. Coba lagi."
}
