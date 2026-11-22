package com.eduvark.app.domain.error

sealed class AppFailure(message: String) : Exception(message) {
    data object Network : AppFailure("Tidak dapat terhubung ke server. Periksa koneksi.")
    data class Api(
        val code: String,
        override val message: String,
        val statusCode: Int,
    ) : AppFailure(message)
    data object Unknown : AppFailure("Terjadi kesalahan. Coba lagi.")
}
