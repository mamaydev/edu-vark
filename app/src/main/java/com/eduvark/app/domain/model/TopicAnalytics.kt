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
    /**
     * Kategori asal nilai yang relevan untuk baris ini (bukan VARK login
     * terakhir). `null` = belum memberi feedback, berbeda dari nilai 0%.
     */
    val vark: VarkType?,
    /** Semua kategori asal yang memiliki nilai feedback tersimpan pada topik. */
    val originCategories: List<VarkType>,
    val feedback: Map<VarkType, Int?>,
) {
    fun valueFor(category: VarkType): Int? = feedback[category]

    fun respondedIn(category: VarkType): Boolean = originCategories.contains(category)
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
