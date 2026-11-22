package com.eduvark.app.domain.model

data class CaseSubmissionSummary(
    val studentId: String,
    val fullName: String,
    val loginId: String,
    val submissionId: String?,
    val submittedAt: String?,
    val status: SubmissionStatus,
    val score: Int?,
)

data class SubmissionDetail(
    val id: String,
    val caseStudyId: String,
    val topicId: String,
    val studentId: String,
    val studentName: String,
    val answerText: String,
    val submittedAt: String,
    val status: SubmissionStatus,
    val score: Int?,
    val comment: String?,
    val assessedAt: String?,
)

data class AssessmentResult(
    val id: String,
    val submissionId: String,
    val score: Int,
    val comment: String?,
    val assessedAt: String,
)
