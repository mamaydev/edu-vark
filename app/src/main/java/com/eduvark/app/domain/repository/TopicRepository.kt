package com.eduvark.app.domain.repository

import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.StudentDashboard
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.UploadPayload
import com.eduvark.app.domain.model.VarkType

interface TopicRepository {
    suspend fun getStudentDashboard(): StudentDashboard
    suspend fun listTopics(): List<Topic>
    suspend fun getTopic(topicId: String): Topic
    suspend fun listMaterials(topicId: String): List<Material>
    suspend fun listLecturerMaterials(topicId: String): List<Material>

    suspend fun createTopic(title: String, description: String?): Topic
    suspend fun publishTopic(topicId: String): Topic
    suspend fun unpublishTopic(topicId: String): Topic
    suspend fun deleteTopic(topicId: String)
    suspend fun reorderTopics(topicIds: List<String>)

    suspend fun createLinkMaterial(
        topicId: String,
        title: String,
        vark: VarkType,
        url: String,
    ): Material

    suspend fun uploadFileMaterial(
        topicId: String,
        title: String,
        vark: VarkType,
        payload: UploadPayload,
    ): Material

    suspend fun deleteMaterial(materialId: String)
}
