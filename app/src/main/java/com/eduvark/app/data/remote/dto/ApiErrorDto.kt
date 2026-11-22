package com.eduvark.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class ApiErrorDto(
    val code: String? = null,
    val message: String? = null,
)

@Serializable
data class ApiErrorResponse(
    val error: ApiErrorDto? = null,
)
