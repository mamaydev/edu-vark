package com.eduvark.app.domain.usecase

import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.UploadPayload
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.TopicRepository
import javax.inject.Inject

class GetStudentDashboardUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke() = repository.getStudentDashboard()
}

class GetTopicsUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(): List<Topic> = repository.listTopics()
}

class GetTopicUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicId: String): Topic = repository.getTopic(topicId)
}

class GetMaterialsUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicId: String): List<Material> =
        repository.listMaterials(topicId)
}

class GetLecturerMaterialsUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicId: String): List<Material> =
        repository.listLecturerMaterials(topicId)
}

class CreateTopicUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(title: String, description: String?): Topic =
        repository.createTopic(title, description)
}

class PublishTopicUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicId: String): Topic = repository.publishTopic(topicId)
}

class UnpublishTopicUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicId: String): Topic = repository.unpublishTopic(topicId)
}

class DeleteTopicUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicId: String) = repository.deleteTopic(topicId)
}

class ReorderTopicsUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(topicIds: List<String>) = repository.reorderTopics(topicIds)
}

class CreateLinkMaterialUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(
        topicId: String,
        title: String,
        vark: VarkType,
        url: String,
    ): Material = repository.createLinkMaterial(topicId, title, vark, url)
}

class UploadMaterialUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(
        topicId: String,
        title: String,
        vark: VarkType,
        payload: UploadPayload,
    ): Material = repository.uploadFileMaterial(topicId, title, vark, payload)
}

class DeleteMaterialUseCase @Inject constructor(
    private val repository: TopicRepository,
) {
    suspend operator fun invoke(materialId: String) = repository.deleteMaterial(materialId)
}
