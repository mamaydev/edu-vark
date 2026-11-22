package com.eduvark.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class FeedbackDto(
    val value: Int? = null,
    val updatedAt: String? = null,
)

@Serializable
data class FeedbackResponse(
    val data: FeedbackDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class SaveFeedbackRequest(
    val value: Int,
)
