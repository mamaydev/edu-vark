package com.eduvark.app.domain.usecase

import com.eduvark.app.domain.model.Feedback
import com.eduvark.app.domain.repository.FeedbackRepository
import javax.inject.Inject

class GetFeedbackUseCase @Inject constructor(
    private val repository: FeedbackRepository,
) {
    suspend operator fun invoke(topicId: String): Feedback = repository.getFeedback(topicId)
}

class SaveFeedbackUseCase @Inject constructor(
    private val repository: FeedbackRepository,
) {
    suspend operator fun invoke(topicId: String, value: Int): Feedback =
        repository.saveFeedback(topicId, value)
}
