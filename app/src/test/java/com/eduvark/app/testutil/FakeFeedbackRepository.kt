package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Feedback
import com.eduvark.app.domain.repository.FeedbackRepository

class FakeFeedbackRepository(
    var value: Int? = null,
    var failure: AppFailure? = null,
) : FeedbackRepository {

    override suspend fun getFeedback(topicId: String): Feedback {
        failure?.let { throw it }
        return Feedback(value = value, updatedAt = null)
    }

    override suspend fun saveFeedback(topicId: String, value: Int): Feedback {
        failure?.let { throw it }
        this.value = value
        return Feedback(value = value, updatedAt = "2026-11-20T10:00:00Z")
    }
}
