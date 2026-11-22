package com.eduvark.app.domain.repository

import com.eduvark.app.domain.model.TopicAnalytics
import com.eduvark.app.domain.model.VarkType

interface AnalyticsRepository {
    suspend fun getTopicAnalytics(
        topicId: String,
        category: VarkType?,
        studentId: String?,
    ): TopicAnalytics
}
