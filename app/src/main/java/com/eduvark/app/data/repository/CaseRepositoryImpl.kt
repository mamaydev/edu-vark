package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.CaseDto
import com.eduvark.app.data.remote.dto.SubmitCaseRequest
import com.eduvark.app.data.remote.dto.UpsertCaseRequest
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.CaseStudy
import com.eduvark.app.domain.model.SubmissionResult
import com.eduvark.app.domain.model.SubmissionStatus
import com.eduvark.app.domain.repository.CaseRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CaseRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
    private val executor: ApiExecutor,
) : CaseRepository {

    override suspend fun getStudentCase(topicId: String): CaseStudy {
        val response = executor.execute { api.getStudentCase(topicId) }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun submitCase(topicId: String, answerText: String): SubmissionResult {
        val response = executor.execute {
            api.submitCase(topicId, SubmitCaseRequest(answerText))
        }
        val data = response.data ?: throw AppFailure.Unknown
        val status = SubmissionStatus.fromWire(data.status) ?: throw AppFailure.Unknown
        return SubmissionResult(
            submissionId = data.submissionId,
            submittedAt = data.submittedAt,
            status = status,
        )
    }

    override suspend fun getLecturerCase(topicId: String): CaseStudy? {
        val response = executor.execute { api.getLecturerCase(topicId) }
        return response.data?.toDomain()
    }

    override suspend fun upsertCase(
        topicId: String,
        theory: String?,
        instructions: String,
        deadlineAt: String,
    ): CaseStudy {
        val response = executor.execute {
            api.upsertCase(topicId, UpsertCaseRequest(theory, instructions, deadlineAt))
        }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    private fun CaseDto.toDomain(): CaseStudy {
        val status = SubmissionStatus.fromWire(status) ?: SubmissionStatus.BELUM_MENGUMPULKAN
        return CaseStudy(
            id = id,
            topicId = topicId,
            theory = theory,
            instructions = instructions,
            deadlineAt = deadlineAt,
            deadlineWib = deadlineWib,
            status = status,
            submittedAt = submittedAt,
            score = score,
            comment = comment,
            submissionCount = submissionCount,
        )
    }
}
