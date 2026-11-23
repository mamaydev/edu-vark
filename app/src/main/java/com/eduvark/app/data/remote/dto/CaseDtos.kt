package com.eduvark.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class CaseDto(
    val id: String,
    val topicId: String,
    val theory: String? = null,
    val instructions: String = "",
    val deadlineAt: String = "",
    val deadlineWib: String = "",
    val status: String = "BELUM_MENGUMPULKAN",
    val submittedAt: String? = null,
    val score: Int? = null,
    val comment: String? = null,
    val answer: String? = null,
    val gradedAt: String? = null,
    val gradedBy: String? = null,
    val submissionCount: Int? = null,
)

@Serializable
data class CaseResponse(
    val data: CaseDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class SubmitCaseRequest(
    val answerText: String,
)

@Serializable
data class SubmissionDto(
    val submissionId: String,
    val submittedAt: String,
    val status: String,
)

@Serializable
data class SubmissionResponse(
    val data: SubmissionDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class UpsertCaseRequest(
    val theory: String? = null,
    val instructions: String,
    val deadlineAt: String,
)
