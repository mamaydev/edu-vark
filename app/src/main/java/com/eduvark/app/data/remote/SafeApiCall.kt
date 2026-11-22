package com.eduvark.app.data.remote

import android.util.Log
import java.io.IOException
import kotlinx.serialization.json.Json
import retrofit2.HttpException

suspend fun <T> safeApiCall(
    json: Json,
    block: suspend () -> T,
    endpoint: String? = null,
): T {
    try {
        return block()
    } catch (error: HttpException) {
        val rawBody = error.response()?.errorBody()?.string()
        val parsed = rawBody?.let {
            runCatching {
                json.decodeFromString<com.eduvark.app.data.remote.dto.ApiErrorResponse>(it)
            }.getOrNull()
        }
        throw ApiException(
            code = parsed?.error?.code ?: "HTTP_" + error.code(),
            message = parsed?.error?.message ?: "Permintaan gagal.",
            statusCode = error.code(),
        )
    } catch (error: IOException) {
        // Safe diagnostic: exception type and endpoint only; never credentials/tokens.
        Log.e(
            "EduVARK-NET",
            "IOException ${error.javaClass.simpleName} at ${endpoint ?: "request"}: ${error.message}",
        )
        throw ApiException(
            code = "NETWORK_ERROR",
            message = "Tidak dapat terhubung ke server. Periksa koneksi.",
            statusCode = 0,
        )
    }
}
