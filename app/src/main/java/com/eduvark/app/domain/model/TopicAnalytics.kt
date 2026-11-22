package com.eduvark.app.domain.model

data class HistogramBucket(
    val bucket: String,
    val count: Int,
)

data class KinestheticAnalytics(
    val assessedCount: Int,
    val meanScore: Double?,
    val notSubmittedCount: Int,
)

data class StudentFeedback(
    val studentId: String,
    val fullName: String,
    val loginId: String,
    val vark: VarkType,
    val feedback: Map<VarkType, Int?>,
) {
    fun valueFor(category: VarkType): Int? = feedback[category]
}

data class TopicAnalytics(
    val topicId: String,
    val category: VarkType?,
    val studentId: String?,
    val respondentCount: Int,
    val mean: Double?,
    val histogram: List<HistogramBucket>,
    val notRespondedCount: Int,
    val aggregation: String,
    val kinesthetic: KinestheticAnalytics,
    val students: List<StudentFeedback>,
)
