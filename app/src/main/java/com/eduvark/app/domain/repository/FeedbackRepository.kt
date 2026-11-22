package com.eduvark.app.domain.repository

import com.eduvark.app.domain.model.Feedback

interface FeedbackRepository {
    suspend fun getFeedback(topicId: String): Feedback
    suspend fun saveFeedback(topicId: String, value: Int): Feedback
}
