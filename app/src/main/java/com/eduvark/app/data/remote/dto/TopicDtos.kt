package com.eduvark.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class TopicDto(
    val id: String,
    val title: String,
    val description: String? = null,
    val sortOrder: Int = 0,
    val status: String = "DRAFT",
    val materialAvailable: Boolean = false,
    val createdAt: String? = null,
    val updatedAt: String? = null,
)

@Serializable
data class MetaDto(
    val page: Int = 1,
    val pageSize: Int = 20,
    val total: Int = 0,
)

@Serializable
data class TopicListResponse(
    val data: List<TopicDto>? = null,
    val meta: MetaDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class TopicResponse(
    val data: TopicDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class MaterialDto(
    val id: String,
    val topicId: String,
    val vark: String,
    val title: String,
    val kind: String,
    val mime: String? = null,
    val sizeBytes: Long? = null,
    val url: String? = null,
    val signedUrl: String? = null,
    val expiresAt: String? = null,
    val uploadedAt: String? = null,
)

@Serializable
data class MaterialListResponse(
    val data: List<MaterialDto>? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class MaterialResponse(
    val data: MaterialDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class StudentDashboardDto(
    val vark: String,
    val availableMaterials: Int = 0,
    val notCompletedMaterials: Int = 0,
)

@Serializable
data class StudentDashboardResponse(
    val data: StudentDashboardDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class CreateTopicRequest(
    val title: String,
    val description: String? = null,
)

@Serializable
data class UpdateTopicRequest(
    val title: String? = null,
    val description: String? = null,
)

@Serializable
data class ReorderTopicsRequest(
    val topicIds: List<String>,
)

@Serializable
data class LinkMaterialRequest(
    val title: String,
    val vark: String,
    val url: String,
)

@Serializable
data class UpdateMaterialRequest(
    val title: String? = null,
    val vark: String? = null,
)
