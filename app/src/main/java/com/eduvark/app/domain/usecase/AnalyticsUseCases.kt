package com.eduvark.app.domain.usecase

import com.eduvark.app.domain.model.TopicAnalytics
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.AnalyticsRepository
import javax.inject.Inject

class GetTopicAnalyticsUseCase @Inject constructor(
    private val repository: AnalyticsRepository,
) {
    suspend operator fun invoke(
        topicId: String,
        category: VarkType? = null,
        studentId: String? = null,
    ): TopicAnalytics = repository.getTopicAnalytics(topicId, category, studentId)
}
