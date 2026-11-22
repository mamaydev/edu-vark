package com.eduvark.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CaseSubmissionSummaryDto(
    val studentId: String,
    val fullName: String,
    val loginId: String,
    val submissionId: String? = null,
    val submittedAt: String? = null,
    val status: String = "BELUM_MENGUMPULKAN",
    val score: Int? = null,
)

@Serializable
data class CaseSubmissionListResponse(
    val data: List<CaseSubmissionSummaryDto>? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class SubmissionDetailDto(
    val id: String,
    val caseStudyId: String,
    val topicId: String,
    val studentId: String,
    val studentName: String,
    val answerText: String,
    val submittedAt: String,
    val status: String,
    val score: Int? = null,
    val comment: String? = null,
    val assessedAt: String? = null,
)

@Serializable
data class SubmissionDetailResponse(
    val data: SubmissionDetailDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class CreateAssessmentRequest(
    val score: Int,
    val comment: String? = null,
)

@Serializable
data class AssessmentDto(
    val id: String,
    val submissionId: String,
    val score: Int,
    val comment: String? = null,
    val assessedAt: String,
)

@Serializable
data class AssessmentResponse(
    val data: AssessmentDto? = null,
    val error: ApiErrorDto? = null,
)
