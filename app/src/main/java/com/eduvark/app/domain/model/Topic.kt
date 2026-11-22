package com.eduvark.app.domain.model

enum class TopicStatus(val wireValue: String, val label: String) {
    DRAFT("DRAFT", "Draft"),
    PUBLISHED("PUBLISHED", "Terbit"),
    ;

    companion object {
        fun fromWire(value: String): TopicStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class Topic(
    val id: String,
    val title: String,
    val description: String?,
    val sortOrder: Int,
    val status: TopicStatus,
    val materialAvailable: Boolean,
)

enum class MaterialKind(val wireValue: String, val label: String) {
    FILE("FILE", "File"),
    LINK("LINK", "Tautan"),
    ;

    companion object {
        fun fromWire(value: String): MaterialKind? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class Material(
    val id: String,
    val topicId: String,
    val vark: VarkType,
    val title: String,
    val kind: MaterialKind,
    val mime: String?,
    val sizeBytes: Long?,
    val url: String?,
    val signedUrl: String?,
    val expiresAt: String?,
    val uploadedAt: String?,
)

data class UploadPayload(
    val fileName: String,
    val mime: String,
    val bytes: ByteArray,
)
