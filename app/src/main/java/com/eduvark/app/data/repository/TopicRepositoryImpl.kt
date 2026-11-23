package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.CreateTopicRequest
import com.eduvark.app.data.remote.dto.LinkMaterialRequest
import com.eduvark.app.data.remote.dto.MaterialDto
import com.eduvark.app.data.remote.dto.ReorderTopicsRequest
import com.eduvark.app.data.remote.dto.TopicDto
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.Material
import com.eduvark.app.domain.model.MaterialKind
import com.eduvark.app.domain.model.StudentDashboard
import com.eduvark.app.domain.model.Topic
import com.eduvark.app.domain.model.TopicStatus
import com.eduvark.app.domain.model.UploadPayload
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.TopicRepository
import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
    private val executor: ApiExecutor,
) : TopicRepository {

    override suspend fun getStudentDashboard(): StudentDashboard {
        val response = executor.execute { api.getStudentDashboard() }
        val data = response.data ?: throw AppFailure.Unknown
        val vark = VarkType.fromWire(data.vark) ?: throw AppFailure.Unknown
        return StudentDashboard(
            vark = vark,
            availableMaterials = data.availableMaterials,
            notCompletedMaterials = data.notCompletedMaterials,
        )
    }

    override suspend fun listTopics(): List<Topic> {
        val response = executor.execute { api.listTopics() }
        return response.data.orEmpty().map { it.toDomain() }
    }

    override suspend fun getTopic(topicId: String): Topic {
        val response = executor.execute { api.getTopic(topicId) }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun listMaterials(topicId: String): List<Material> {
        val response = executor.execute { api.listMaterials(topicId) }
        return response.data.orEmpty().map { it.toDomain() }
    }

    override suspend fun listLecturerMaterials(topicId: String): List<Material> {
        val response = executor.execute { api.listLecturerMaterials(topicId) }
        return response.data.orEmpty().map { it.toDomain() }
    }

    override suspend fun createTopic(title: String, description: String?): Topic {
        val response = executor.execute {
            api.createTopic(CreateTopicRequest(title = title, description = description))
        }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun publishTopic(topicId: String): Topic {
        val response = executor.execute { api.publishTopic(topicId) }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun unpublishTopic(topicId: String): Topic {
        val response = executor.execute { api.unpublishTopic(topicId) }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun deleteTopic(topicId: String) {
        executor.execute { api.deleteTopic(topicId) }
    }

    override suspend fun reorderTopics(topicIds: List<String>) {
        executor.execute { api.reorderTopics(ReorderTopicsRequest(topicIds)) }
    }

    override suspend fun createLinkMaterial(
        topicId: String,
        title: String,
        vark: VarkType,
        url: String,
    ): Material {
        val response = executor.execute {
            api.createLinkMaterial(
                topicId,
                LinkMaterialRequest(title = title, vark = vark.wireValue, url = url),
            )
        }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun uploadFileMaterial(
        topicId: String,
        title: String,
        vark: VarkType,
        payload: UploadPayload,
    ): Material {
        val mediaType = payload.mime.toMediaTypeOrNull()
            ?: throw AppFailure.Api(
                code = "INVALID_FILE_TYPE",
                message = "Tipe file tidak dikenali.",
                statusCode = 422,
            )
        val fileBody = payload.bytes.toRequestBody(mediaType)
        val filePart = MultipartBody.Part.createFormData(
            name = "file",
            filename = payload.fileName,
            body = fileBody,
        )
        val titleBody = title.toRequestBody("text/plain".toMediaType())
        val varkBody = vark.wireValue.toRequestBody("text/plain".toMediaType())

        val response = executor.execute {
            api.uploadFileMaterial(topicId, filePart, titleBody, varkBody)
        }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun deleteMaterial(materialId: String) {
        executor.execute { api.deleteMaterial(materialId) }
    }

    private fun TopicDto.toDomain(): Topic {
        val status = TopicStatus.fromWire(status) ?: TopicStatus.DRAFT
        return Topic(
            id = id,
            title = title,
            description = description,
            sortOrder = sortOrder,
            status = status,
            materialAvailable = materialAvailable,
        )
    }

    private fun MaterialDto.toDomain(): Material {
        val parsedVark = VarkType.fromWire(vark) ?: throw AppFailure.Unknown
        val parsedKind = MaterialKind.fromWire(kind) ?: throw AppFailure.Unknown
        return Material(
            id = id,
            topicId = topicId,
            vark = parsedVark,
            title = title,
            kind = parsedKind,
            mime = mime,
            sizeBytes = sizeBytes,
            url = url,
            signedUrl = signedUrl,
            expiresAt = expiresAt,
            uploadedAt = uploadedAt,
        )
    }
}
