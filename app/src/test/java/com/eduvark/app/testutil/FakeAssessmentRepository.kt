package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AssessmentResult
import com.eduvark.app.domain.model.CaseSubmissionSummary
import com.eduvark.app.domain.model.SubmissionDetail
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.domain.repository.AssessmentRepository

fun sampleSummary(
    studentId: String = "student-1",
    status: SubmissionStatus = SubmissionStatus.MENUNGGU_PENILAIAN,
    score: Int? = null,
): CaseSubmissionSummary = CaseSubmissionSummary(
    studentId = studentId,
    fullName = "Mahasiswa Satu",
    loginId = "stu1",
    submissionId = "submission-1",
    submittedAt = "2026-11-20T10:00:00Z",
    status = status,
    score = score,
)

fun sampleSubmissionDetail(
    status: SubmissionStatus = SubmissionStatus.MENUNGGU_PENILAIAN,
    score: Int? = null,
    comment: String? = null,
): SubmissionDetail = SubmissionDetail(
    id = "submission-1",
    caseStudyId = "case-1",
    topicId = "topic-1",
    studentId = "student-1",
    studentName = "Mahasiswa Satu",
    answerText = "Jawaban studi kasus.",
    submittedAt = "2026-11-20T10:00:00Z",
    status = status,
    score = score,
    comment = comment,
    assessedAt = if (status == SubmissionStatus.SUDAH_DINILAI) {
        "2026-11-20T11:00:00Z"
    } else {
        null
    },
)

class FakeAssessmentRepository(
    var summaries: List<CaseSubmissionSummary> = listOf(sampleSummary()),
    var detail: SubmissionDetail = sampleSubmissionDetail(),
    var failure: AppFailure? = null,
) : AssessmentRepository {

    override suspend fun listCaseSubmissions(caseId: String): List<CaseSubmissionSummary> {
        failure?.let { throw it }
        return summaries
    }

    override suspend fun getSubmission(submissionId: String): SubmissionDetail {
        failure?.let { throw it }
        return detail
    }

    override suspend fun createAssessment(
        submissionId: String,
        score: Int,
        comment: String?,
    ): AssessmentResult {
        failure?.let { throw it }
        detail = detail.copy(
            status = SubmissionStatus.SUDAH_DINILAI,
            score = score,
            comment = comment,
            assessedAt = "2026-11-20T11:00:00Z",
        )
        return AssessmentResult(
            id = "assessment-1",
            submissionId = submissionId,
            score = score,
            comment = comment,
            assessedAt = "2026-11-20T11:00:00Z",
        )
    }
}
