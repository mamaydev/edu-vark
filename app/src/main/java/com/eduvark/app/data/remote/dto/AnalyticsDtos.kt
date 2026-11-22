package com.eduvark.app.data.remote.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class HistogramBucketDto(
    val bucket: String,
    val count: Int,
)

@Serializable
data class KinestheticAnalyticsDto(
    val assessedCount: Int,
    val meanScore: Double? = null,
    val notSubmittedCount: Int,
)

@Serializable
data class StudentFeedbackValuesDto(
    @SerialName("VISUAL") val visual: Int? = null,
    @SerialName("AURAL") val aural: Int? = null,
    @SerialName("READ_WRITE") val readWrite: Int? = null,
)

@Serializable
data class StudentFeedbackDto(
    val studentId: String,
    val fullName: String,
    val loginId: String,
    val vark: String,
    val feedback: StudentFeedbackValuesDto,
)

@Serializable
data class TopicAnalyticsDto(
    val topicId: String,
    val category: String? = null,
    val studentId: String? = null,
    val respondentCount: Int,
    val mean: Double? = null,
    val histogram: List<HistogramBucketDto> = emptyList(),
    val notRespondedCount: Int,
    val aggregation: String,
    val kinesthetic: KinestheticAnalyticsDto,
    val students: List<StudentFeedbackDto> = emptyList(),
)

@Serializable
data class TopicAnalyticsResponse(
    val data: TopicAnalyticsDto? = null,
    val error: ApiErrorDto? = null,
)
