package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.MaterialKind
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.TopicStatus
import com.eduvark.app.domain.model.UploadPayload
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.TopicRepository

fun sampleTopic(
    id: String = "topic-1",
    title: String = "Topik Basis Data",
    materialAvailable: Boolean = true,
): Topic = Topic(
    id = id,
    title = title,
    description = "Deskripsi topik",
    sortOrder = 0,
    status = TopicStatus.PUBLISHED,
    materialAvailable = materialAvailable,
)

fun sampleMaterial(
    id: String = "material-1",
    topicId: String = "topic-1",
): Material = Material(
    id = id,
    topicId = topicId,
    vark = VarkType.VISUAL,
    title = "Materi Visual",
    kind = MaterialKind.LINK,
    mime = null,
    sizeBytes = null,
    url = "https://example.com/material",
    signedUrl = null,
    expiresAt = null,
    uploadedAt = "2026-11-20T10:00:00Z",
)

class FakeTopicRepository(
    var topics: List<Topic> = listOf(sampleTopic()),
    var materials: List<Material> = listOf(sampleMaterial()),
    var failure: AppFailure? = null,
) : TopicRepository {

    var lastReorder: List<String>? = null

    override suspend fun listTopics(): List<Topic> {
        failure?.let { throw it }
        return topics
    }

    override suspend fun getTopic(topicId: String): Topic {
        failure?.let { throw it }
        return topics.firstOrNull { it.id == topicId } ?: throw AppFailure.Unknown
    }

    override suspend fun listMaterials(topicId: String): List<Material> {
        failure?.let { throw it }
        return materials
    }

    override suspend fun createTopic(title: String, description: String?): Topic {
        val topic = sampleTopic(id = "topic-" + (topics.size + 1), title = title)
        topics = topics + topic
        return topic
    }

    override suspend fun publishTopic(topicId: String): Topic {
        topics = topics.map {
            if (it.id == topicId) it.copy(status = TopicStatus.PUBLISHED) else it
        }
        return topics.first { it.id == topicId }
    }

    override suspend fun unpublishTopic(topicId: String): Topic {
        topics = topics.map {
            if (it.id == topicId) it.copy(status = TopicStatus.DRAFT) else it
        }
        return topics.first { it.id == topicId }
    }

    override suspend fun deleteTopic(topicId: String) {
        topics = topics.filterNot { it.id == topicId }
    }

    override suspend fun reorderTopics(topicIds: List<String>) {
        lastReorder = topicIds
        val byId = topics.associateBy { it.id }
        topics = topicIds.mapNotNull { byId[it] }
    }

    override suspend fun createLinkMaterial(
        topicId: String,
        title: String,
        vark: VarkType,
        url: String,
    ): Material {
        val material = sampleMaterial(id = "material-" + (materials.size + 1), topicId = topicId)
            .copy(title = title, vark = vark, url = url, kind = MaterialKind.LINK)
        materials = materials + material
        return material
    }

    override suspend fun uploadFileMaterial(
        topicId: String,
        title: String,
        vark: VarkType,
        payload: UploadPayload,
    ): Material {
        val material = sampleMaterial(id = "material-" + (materials.size + 1), topicId = topicId)
            .copy(title = title, vark = vark, kind = MaterialKind.FILE, mime = payload.mime)
        materials = materials + material
        return material
    }

    override suspend fun deleteMaterial(materialId: String) {
        materials = materials.filterNot { it.id == materialId }
    }
}
