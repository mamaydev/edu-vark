package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.CreateAssessmentRequest
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AssessmentResult
import com.eduvark.app.domain.model.CaseSubmissionSummary
import com.eduvark.app.domain.model.SubmissionDetail
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.domain.repository.AssessmentRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AssessmentRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
    private val executor: ApiExecutor,
) : AssessmentRepository {

    override suspend fun listCaseSubmissions(caseId: String): List<CaseSubmissionSummary> {
        val response = executor.execute { api.listCaseSubmissions(caseId) }
        return response.data.orEmpty().map { row ->
            CaseSubmissionSummary(
                studentId = row.studentId,
                fullName = row.fullName,
                loginId = row.loginId,
                submissionId = row.submissionId,
                submittedAt = row.submittedAt,
                status = SubmissionStatus.fromWire(row.status)
                    ?: SubmissionStatus.BELUM_MENGUMPULKAN,
                score = row.score,
            )
        }
    }

    override suspend fun getSubmission(submissionId: String): SubmissionDetail {
        val response = executor.execute { api.getSubmission(submissionId) }
        val data = response.data ?: throw AppFailure.Unknown
        return SubmissionDetail(
            id = data.id,
            caseStudyId = data.caseStudyId,
            topicId = data.topicId,
            studentId = data.studentId,
            studentName = data.studentName,
            answerText = data.answerText,
            submittedAt = data.submittedAt,
            status = SubmissionStatus.fromWire(data.status)
                ?: SubmissionStatus.MENUNGGU_PENILAIAN,
            score = data.score,
            comment = data.comment,
            assessedAt = data.assessedAt,
        )
    }

    override suspend fun createAssessment(
        submissionId: String,
        score: Int,
        comment: String?,
    ): AssessmentResult {
        val response = executor.execute {
            api.createAssessment(
                submissionId,
                CreateAssessmentRequest(score = score, comment = comment),
            )
        }
        val data = response.data ?: throw AppFailure.Unknown
        return AssessmentResult(
            id = data.id,
            submissionId = data.submissionId,
            score = data.score,
            comment = data.comment,
            assessedAt = data.assessedAt,
        )
    }
}
