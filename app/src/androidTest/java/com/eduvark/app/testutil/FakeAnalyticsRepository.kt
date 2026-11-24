package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.HistogramBucket
import com.eduvark.app.domain.model.KinestheticAnalytics
import com.eduvark.app.domain.model.StudentFeedback
import com.eduvark.app.domain.model.TopicAnalytics
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.AnalyticsRepository

/**
 * Fake repository untuk instrumentasi Compose layar Analytics (regresi C1-05).
 * Dataset sama dengan unit test: 2 responden (Visual 75/Audio 60 dan Visual 0%),
 * 1 mahasiswa tanpa feedback, serta total kompatibilitas API
 * `notRespondedCount = 1` dan `kinesthetic.notSubmittedCount = 1` yang TIDAK
 * boleh dirender UI.
 */
fun sampleAnalyticsForUi(
    category: VarkType? = null,
    studentId: String? = null,
) = TopicAnalytics(
    topicId = "topic-1",
    category = category,
    studentId = studentId,
    respondentCount = 2,
    mean = 37.5,
    histogram = listOf(
        HistogramBucket("0-20", 1),
        HistogramBucket("21-40", 0),
        HistogramBucket("41-60", 0),
        HistogramBucket("61-80", 1),
        HistogramBucket("81-100", 0),
    ),
    notRespondedCount = 1,
    aggregation = "Rerata aritmetika feedback terbaru per mahasiswa-topik-kategori.",
    kinesthetic = KinestheticAnalytics(
        assessedCount = 2,
        meanScore = 90.0,
        notSubmittedCount = 1,
    ),
    students = listOf(
        StudentFeedback(
            studentId = "student-1",
            fullName = "Mahasiswa Satu",
            loginId = "stu1",
            vark = VarkType.VISUAL,
            originCategories = listOf(VarkType.VISUAL, VarkType.AURAL),
            feedback = mapOf(
                VarkType.VISUAL to 75,
                VarkType.AURAL to 60,
                VarkType.READ_WRITE to null,
            ),
        ),
        StudentFeedback(
            studentId = "student-2",
            fullName = "Mahasiswa Dua",
            loginId = "stu2",
            vark = VarkType.VISUAL,
            originCategories = listOf(VarkType.VISUAL),
            feedback = mapOf(
                VarkType.VISUAL to 0,
                VarkType.AURAL to null,
                VarkType.READ_WRITE to null,
            ),
        ),
        StudentFeedback(
            studentId = "student-3",
            fullName = "Mahasiswa Tiga",
            loginId = "stu3",
            vark = null,
            originCategories = emptyList(),
            feedback = mapOf(
                VarkType.VISUAL to null,
                VarkType.AURAL to null,
                VarkType.READ_WRITE to null,
            ),
        ),
    ),
)

class FakeAnalyticsRepository(
    var analytics: TopicAnalytics = sampleAnalyticsForUi(),
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
