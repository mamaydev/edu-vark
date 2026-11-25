package com.eduvark.app.data.remote

import com.eduvark.app.data.remote.dto.ApiErrorResponse
import com.eduvark.app.domain.error.AppFailure
import kotlinx.serialization.json.Json
import retrofit2.Response

/**
 * Retrofit suspend functions that return [Response] do NOT throw
 * [retrofit2.HttpException]; the explicit HTTP result must be validated before
 * the caller may treat the operation as successful (FR-003/DEC-007).
 *
 * Returns `null` only for a successful response. For an HTTP error it returns
 * an [AppFailure.Api] carrying the server error envelope when the body can be
 * parsed, or a generic `HTTP_<code>` failure otherwise.
 */
internal fun <T> Response<T>.toAppFailureOrNull(json: Json): AppFailure? {
    if (isSuccessful) return null
    val parsed = errorBody()?.string()?.let { raw ->
        runCatching { json.decodeFromString<ApiErrorResponse>(raw) }.getOrNull()
    }
    return AppFailure.Api(
        code = parsed?.error?.code ?: "HTTP_" + code(),
        message = parsed?.error?.message ?: "Permintaan gagal.",
        statusCode = code(),
    )
}
