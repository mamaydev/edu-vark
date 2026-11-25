package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.CreateTopicRequest
import com.eduvark.app.data.remote.dto.LinkMaterialRequest
import com.eduvark.app.data.remote.dto.MaterialDto
import com.eduvark.app.data.remote.dto.ReorderTopicsRequest
import com.eduvark.app.data.remote.dto.TopicDto
import com.eduvark.app.data.remote.dto.UpdateTopicRequest
import com.eduvark.app.data.remote.toAppFailureOrNull
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.FileDeletionStatus
import com.eduvark.app.domain.model.FileReplacementResult
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class TopicRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
    private val executor: ApiExecutor,
    private val json: Json,
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

    /**
     * FR-017: backend memakai pagination (default 20, maksimum 100 per halaman).
     * Dosen wajib melihat seluruh topik agar urutan yang dikirim saat reorder
     * memuat semua topik aktif (`TOPIC_ORDER_INCOMPLETE` bila tidak), dan
     * mahasiswa harus melihat urutan yang sama. Jadi ambil semua halaman
     * berurutan memakai `meta.total` sebagai batas.
     */
    override suspend fun listTopics(): List<Topic> {
        val topics = mutableListOf<Topic>()
        var page = 1
        var total: Int? = null
        while (page <= MAX_TOPIC_PAGES && (total == null || topics.size < total)) {
            val response = executor.execute {
                api.listTopics(page = page, pageSize = TOPIC_PAGE_SIZE)
            }
            val data = response.data.orEmpty()
            if (data.isEmpty()) break
            topics.addAll(data.map { it.toDomain() })
            val reportedTotal = response.meta?.total
            if (reportedTotal == null) {
                // Server tanpa metadata: halaman pertama dianggap satu-satunya.
                break
            }
            total = reportedTotal
            if (topics.size >= reportedTotal) break
            page += 1
        }
        return topics
    }

    override suspend fun getTopic(topicId: String): Topic {
        val response = executor.execute { api.getTopic(topicId) }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun getLecturerTopic(topicId: String): Topic {
        val response = executor.execute { api.getLecturerTopic(topicId) }
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

    override suspend fun downloadMaterialContent(materialId: String): ByteArray {
        return executor.execute { api.downloadMaterialContent(materialId).bytes() }
    }

    override suspend fun getMaterialLinkTarget(materialId: String): String {
        val response = executor.execute { api.getMaterialLinkTarget(materialId) }
        return response.data?.url?.takeIf { it.isNotBlank() } ?: throw AppFailure.Unknown
    }

    override suspend fun createTopic(title: String, description: String?): Topic {
        val response = executor.execute {
            api.createTopic(CreateTopicRequest(title = title, description = description))
        }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun updateTopic(
        topicId: String,
        title: String?,
        description: String?,
    ): Topic {
        val response = executor.execute {
            api.updateTopic(
                topicId = topicId,
                request = UpdateTopicRequest(
                    title = title,
                    // JsonNull (bukan null Kotlin) agar JSON memuat "description": null
                    // dan dosen dapat mengosongkan deskripsi secara eksplisit.
                    description = description?.let { JsonPrimitive(it) } ?: JsonNull,
                ),
            )
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
        // Retrofit tidak melempar HttpException untuk Response<Unit>; non-2xx harus
        // diperiksa eksplisit agar penolakan (mis. TOPIC_HAS_ACADEMIC_DATA) tidak
        // tampak sukses di UI (FR-009 / perlindungan arsip).
        val response = executor.execute { api.deleteTopic(topicId) }
        response.toAppFailureOrNull(json)?.let { throw it }
    }

    override suspend fun reorderTopics(topicIds: List<String>) {
        // Sama seperti deleteTopic: kegagalan reorder tidak boleh ditelan diam-diam,
        // karena UI melakukan reorder optimistis dan harus memulihkan urutan server.
        val response = executor.execute {
            api.reorderTopics(ReorderTopicsRequest(topicIds))
        }
        response.toAppFailureOrNull(json)?.let { throw it }
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
        val filePart = payload.toFilePart()
        val titleBody = title.toRequestBody("text/plain".toMediaType())
        val varkBody = vark.wireValue.toRequestBody("text/plain".toMediaType())

        val response = executor.execute {
            api.uploadFileMaterial(topicId, filePart, titleBody, varkBody)
        }
        val data = response.data ?: throw AppFailure.Unknown
        return data.toDomain()
    }

    override suspend fun deleteMaterial(materialId: String): FileDeletionStatus {
        val response = executor.execute { api.deleteMaterial(materialId) }
        response.toAppFailureOrNull(json)?.let { throw it }
        // Backend lama membalas 204 tanpa body: aksi hapus berhasil, tetapi status
        // fisik objek tidak dikirim sehingga diperlakukan UNKNOWN (bukan PENDING,
        // agar klien tidak menjanjikan percobaan ulang yang belum tentu ada).
        val wire = response.body()?.data?.fileDeletion
        return FileDeletionStatus.fromWire(wire)
    }

    override suspend fun replaceMaterialFile(
        materialId: String,
        payload: UploadPayload,
    ): FileReplacementResult {
        val response = executor.execute {
            api.replaceMaterialFile(materialId, payload.toFilePart())
        }
        val data = response.data ?: throw AppFailure.Unknown
        return FileReplacementResult(
            material = data.material.toDomain(),
            previousFileDeletion = FileDeletionStatus.fromWire(data.previousFileDeletion),
        )
    }

    private fun UploadPayload.toFilePart(): MultipartBody.Part {
        val mediaType = mime.toMediaTypeOrNull()
            ?: throw AppFailure.Api(
                code = "INVALID_FILE_TYPE",
                message = "Tipe file tidak dikenali.",
                statusCode = 422,
            )
        return MultipartBody.Part.createFormData(
            name = "file",
            filename = fileName,
            body = bytes.toRequestBody(mediaType),
        )
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
            contentUrl = contentUrl,
            linkTargetUrl = linkTargetUrl,
        )
    }

    private companion object {
        const val TOPIC_PAGE_SIZE = 100
        const val MAX_TOPIC_PAGES = 100
    }
}
