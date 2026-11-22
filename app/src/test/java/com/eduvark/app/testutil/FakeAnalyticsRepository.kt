package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.HistogramBucket
import com.eduvark.app.domain.model.KinestheticAnalytics
import com.eduvark.app.domain.model.StudentFeedback
import com.eduvark.app.domain.model.TopicAnalytics
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.AnalyticsRepository

fun sampleAnalytics(
    category: VarkType? = null,
    studentId: String? = null,
): TopicAnalytics = TopicAnalytics(
    topicId = "topic-1",
    category = category,
    studentId = studentId,
    respondentCount = 2,
    mean = 50.0,
    histogram = listOf(
        HistogramBucket("0-20", 0),
        HistogramBucket("21-40", 1),
        HistogramBucket("41-60", 1),
        HistogramBucket("61-80", 0),
        HistogramBucket("81-100", 0),
    ),
    notRespondedCount = 1,
    aggregation = "mean of latest feedback per student-topic-category",
    kinesthetic = KinestheticAnalytics(
        assessedCount = 1,
        meanScore = 80.0,
        notSubmittedCount = 0,
    ),
    students = listOf(
        StudentFeedback(
            studentId = "student-1",
            fullName = "Mahasiswa Satu",
            loginId = "stu1",
            vark = VarkType.VISUAL,
            feedback = mapOf(
                VarkType.VISUAL to 40,
                VarkType.AURAL to null,
                VarkType.READ_WRITE to null,
            ),
        ),
    ),
)

class FakeAnalyticsRepository(
    var analytics: TopicAnalytics = sampleAnalytics(),
    var failure: AppFailure? = null,
) : AnalyticsRepository {

    var lastCategory: VarkType? = null
    var lastStudentId: String? = null

    override suspend fun getTopicAnalytics(
        topicId: String,
        category: VarkType?,
        studentId: String?,
    ): TopicAnalytics {
        failure?.let { throw it }
        lastCategory = category
        lastStudentId = studentId
        return analytics.copy(category = category, studentId = studentId)
    }
}
