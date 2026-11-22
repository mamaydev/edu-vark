package com.eduvark.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class HealthResponse(
    val data: HealthDataDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class HealthDataDto(
    val status: String = "unknown",
    val service: String? = null,
    val timestamp: String? = null,
)
