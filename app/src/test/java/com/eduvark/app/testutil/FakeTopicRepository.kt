package com.eduvark.app.testutil

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
    var failStudentTopic = false
    var failLecturerTopic = false
    /** Kegagalan yang disimulasikan pada reorder (mis. TOPIC_ORDER_INCOMPLETE). */
    var reorderFailure: AppFailure? = null
    /** Kegagalan yang disimulasikan pada hapus (mis. TOPIC_HAS_ACADEMIC_DATA). */
    var deleteFailure: AppFailure? = null

    /** Isi yang dikembalikan untuk unduhan materi FILE. */
    var downloadedContent: ByteArray = "%PDF-1.7 isi materi".toByteArray()
    /** Kegagalan yang disimulasikan pada unduhan berkas. */
    var downloadFailure: AppFailure? = null
    /** Tujuan tautan eksternal yang lolos pemeriksaan izin backend. */
    var linkTargetUrl: String = "https://example.com/materi-aman"
    /** Kegagalan yang disimulasikan pada permintaan link-target. */
    var linkTargetFailure: AppFailure? = null
    var downloadedMaterialIds: MutableList<String> = mutableListOf()
    var linkTargetMaterialIds: MutableList<String> = mutableListOf()

    override suspend fun getStudentDashboard(): StudentDashboard {
        failure?.let { throw it }
        return StudentDashboard(
            vark = VarkType.VISUAL,
            availableMaterials = topics.count { it.materialAvailable },
            notCompletedMaterials = topics.count { it.materialAvailable },
        )
    }

    override suspend fun listTopics(): List<Topic> {
        failure?.let { throw it }
        return topics
    }

    override suspend fun getTopic(topicId: String): Topic {
        failure?.let { throw it }
        if (failStudentTopic) throw AppFailure.Unknown
        return topics.firstOrNull { it.id == topicId } ?: throw AppFailure.Unknown
    }

    override suspend fun getLecturerTopic(topicId: String): Topic {
        failure?.let { throw it }
        if (failLecturerTopic) throw AppFailure.Unknown
        return topics.firstOrNull { it.id == topicId } ?: throw AppFailure.Unknown
    }

    /** Kegagalan pemuatan ulang daftar setelah aksi materi berhasil (commit). */
    var failMaterialsOnRefresh = false

    override suspend fun listLecturerMaterials(topicId: String): List<Material> {
        failure?.let { throw it }
        if (failMaterialsOnRefresh) throw AppFailure.Unknown
        return materials
    }

    override suspend fun listMaterials(topicId: String): List<Material> {
        failure?.let { throw it }
        return materials
    }

    override suspend fun downloadMaterialContent(materialId: String): ByteArray {
        downloadedMaterialIds += materialId
        downloadFailure?.let { throw it }
        failure?.let { throw it }
        return downloadedContent
    }

    override suspend fun getMaterialLinkTarget(materialId: String): String {
        linkTargetMaterialIds += materialId
        linkTargetFailure?.let { throw it }
        failure?.let { throw it }
        return linkTargetUrl
    }

    override suspend fun createTopic(title: String, description: String?): Topic {
        val topic = sampleTopic(id = "topic-" + (topics.size + 1), title = title)
        topics = topics + topic
        return topic
    }

    override suspend fun updateTopic(
        topicId: String,
        title: String?,
        description: String?,
    ): Topic {
        failure?.let { throw it }
        // Backend sekarang membedakan "field dihilangkan" (tidak berubah) dari
        // "description: null" (dikosongkan). Android selalu mengirim null eksplisit
        // untuk deskripsi kosong, jadi fake ini mengosongkan description.
        topics = topics.map {
            if (it.id == topicId) {
                it.copy(
                    title = title ?: it.title,
                    description = description,
                )
            } else {
                it
            }
        }
        return topics.first { it.id == topicId }
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
        deleteFailure?.let { throw it }
        topics = topics.filterNot { it.id == topicId }
    }

    override suspend fun reorderTopics(topicIds: List<String>) {
        lastReorder = topicIds
        reorderFailure?.let { throw it }
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

    /** Status yang dikembalikan backend saat delete; dapat diatur test. */
    var deletionStatus: FileDeletionStatus = FileDeletionStatus.COMPLETED

    /** Kegagalan yang disimulasikan saat mengganti berkas. */
    var replaceFailure: AppFailure? = null

    /** Status fisik berkas lama yang dilaporkan setelah penggantian berhasil. */
    var replaceDeletionStatus: FileDeletionStatus = FileDeletionStatus.COMPLETED

    var lastReplacedPayload: UploadPayload? = null

    override suspend fun deleteMaterial(materialId: String): FileDeletionStatus {
        materials = materials.filterNot { it.id == materialId }
        return deletionStatus
    }

    override suspend fun replaceMaterialFile(
        materialId: String,
        payload: UploadPayload,
    ): FileReplacementResult {
        replaceFailure?.let { throw it }
        lastReplacedPayload = payload
        val existing = materials.firstOrNull { it.id == materialId }
            ?: throw AppFailure.Api("MATERIAL_NOT_FOUND", "Materi tidak ditemukan.", 404)
        val replaced = existing.copy(mime = payload.mime, sizeBytes = payload.bytes.size.toLong())
        materials = materials.map { if (it.id == materialId) replaced else it }
        return FileReplacementResult(
            material = replaced,
            previousFileDeletion = replaceDeletionStatus,
        )
    }
}
