package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.TopicAnalyticsDto
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.HistogramBucket
import com.eduvark.app.domain.model.KinestheticAnalytics
import com.eduvark.app.domain.model.StudentFeedback
import com.eduvark.app.domain.model.TopicAnalytics
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.AnalyticsRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AnalyticsRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
    private val executor: ApiExecutor,
) : AnalyticsRepository {

    override suspend fun getTopicAnalytics(
        topicId: String,
        category: VarkType?,
        studentId: String?,
    ): TopicAnalytics {
        val response = executor.execute {
            api.getTopicAnalytics(
                topicId = topicId,
                category = category?.wireValue,
                studentId = studentId,
            )
        }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    private fun TopicAnalyticsDto.toDomain(): TopicAnalytics {
        val parsedCategory = category?.let { VarkType.fromWire(it) ?: throw AppFailure.Unknown }
        return TopicAnalytics(
            topicId = topicId,
            category = parsedCategory,
            studentId = studentId,
            respondentCount = respondentCount,
            mean = mean,
            histogram = histogram.map { HistogramBucket(it.bucket, it.count) },
            notRespondedCount = notRespondedCount,
            aggregation = aggregation,
            kinesthetic = KinestheticAnalytics(
                assessedCount = kinesthetic.assessedCount,
                meanScore = kinesthetic.meanScore,
                notSubmittedCount = kinesthetic.notSubmittedCount,
            ),
            students = students.map { row ->
                // `vark` adalah kategori asal nilai tersimpan yang relevan;
                // null berarti belum memberi feedback (bukan nilai 0%).
                val vark = row.vark?.let { wire ->
                    VarkType.fromWire(wire) ?: throw AppFailure.Unknown
                }
                val originCategories = row.originCategories.map { wire ->
                    VarkType.fromWire(wire) ?: throw AppFailure.Unknown
                }
                StudentFeedback(
                    studentId = row.studentId,
                    fullName = row.fullName,
                    loginId = row.loginId,
                    vark = vark,
                    originCategories = originCategories,
                    feedback = mapOf(
                        VarkType.VISUAL to row.feedback.visual,
                        VarkType.AURAL to row.feedback.aural,
                        VarkType.READ_WRITE to row.feedback.readWrite,
                    ),
                )
            },
        )
    }
}
