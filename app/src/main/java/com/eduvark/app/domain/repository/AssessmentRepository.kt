package com.eduvark.app.domain.repository

import com.eduvark.app.domain.model.AssessmentResult
import com.eduvark.app.domain.model.CaseSubmissionSummary
import com.eduvark.app.domain.model.SubmissionDetail

interface AssessmentRepository {
    suspend fun listCaseSubmissions(caseId: String): List<CaseSubmissionSummary>
    suspend fun getSubmission(submissionId: String): SubmissionDetail
    suspend fun createAssessment(
        submissionId: String,
        score: Int,
        comment: String?,
    ): AssessmentResult
}
