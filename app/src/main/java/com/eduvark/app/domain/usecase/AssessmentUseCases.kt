package com.eduvark.app.domain.usecase

import com.eduvark.app.domain.model.AssessmentResult
import com.eduvark.app.domain.model.CaseSubmissionSummary
import com.eduvark.app.domain.model.SubmissionDetail
import com.eduvark.app.domain.repository.AssessmentRepository
import javax.inject.Inject

class ListCaseSubmissionsUseCase @Inject constructor(
    private val repository: AssessmentRepository,
) {
    suspend operator fun invoke(caseId: String): List<CaseSubmissionSummary> =
        repository.listCaseSubmissions(caseId)
}

class GetSubmissionUseCase @Inject constructor(
    private val repository: AssessmentRepository,
) {
    suspend operator fun invoke(submissionId: String): SubmissionDetail =
        repository.getSubmission(submissionId)
}

class CreateAssessmentUseCase @Inject constructor(
    private val repository: AssessmentRepository,
) {
    suspend operator fun invoke(
        submissionId: String,
        score: Int,
        comment: String?,
    ): AssessmentResult = repository.createAssessment(submissionId, score, comment)
}
