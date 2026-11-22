package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.CaseStudy
import com.eduvark.app.domain.model.SubmissionResult
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.domain.repository.CaseRepository

fun sampleCase(
    status: SubmissionStatus = SubmissionStatus.BELUM_MENGUMPULKAN,
): CaseStudy = CaseStudy(
    id = "case-1",
    topicId = "topic-1",
    theory = "Teori IBD",
    instructions = "Kerjakan studi kasus",
    deadlineAt = "2026-12-01T16:59:59Z",
    deadlineWib = "1 Des 2026, 23.59 WIB",
    status = status,
    submittedAt = null,
    score = null,
    comment = null,
    submissionCount = 0,
)

class FakeCaseRepository(
    var caseStudy: CaseStudy? = sampleCase(),
    var failure: AppFailure? = null,
) : CaseRepository {

    override suspend fun getStudentCase(topicId: String): CaseStudy {
        failure?.let { throw it }
        return caseStudy ?: throw AppFailure.Unknown
    }

    override suspend fun submitCase(topicId: String, answerText: String): SubmissionResult {
        failure?.let { throw it }
        val current = caseStudy ?: throw AppFailure.Unknown
        caseStudy = current.copy(
            status = SubmissionStatus.MENUNGGU_PENILAIAN,
            submittedAt = "2026-11-20T10:00:00Z",
        )
        return SubmissionResult(
            submissionId = "submission-1",
            submittedAt = "2026-11-20T10:00:00Z",
            status = SubmissionStatus.MENUNGGU_PENILAIAN,
        )
    }

    override suspend fun getLecturerCase(topicId: String): CaseStudy? {
        failure?.let { throw it }
        return caseStudy
    }

    override suspend fun upsertCase(
        topicId: String,
        theory: String?,
        instructions: String,
        deadlineAt: String,
    ): CaseStudy {
        failure?.let { throw it }
        val current = caseStudy ?: sampleCase()
        val updated = current.copy(
            theory = theory,
            instructions = instructions,
            deadlineAt = deadlineAt,
        )
        caseStudy = updated
        return updated
    }
}
