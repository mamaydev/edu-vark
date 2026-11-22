package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.SaveFeedbackRequest
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Feedback
import com.eduvark.app.domain.repository.FeedbackRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FeedbackRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
    private val executor: ApiExecutor,
) : FeedbackRepository {

    override suspend fun getFeedback(topicId: String): Feedback {
        val response = executor.execute { api.getFeedback(topicId) }
        val data = response.data ?: throw AppFailure.Unknown
        return Feedback(value = data.value, updatedAt = data.updatedAt)
    }

    override suspend fun saveFeedback(topicId: String, value: Int): Feedback {
        val response = executor.execute {
            api.saveFeedback(topicId, SaveFeedbackRequest(value = value))
        }
        val data = response.data ?: throw AppFailure.Unknown
        return Feedback(value = data.value, updatedAt = data.updatedAt)
    }
}
