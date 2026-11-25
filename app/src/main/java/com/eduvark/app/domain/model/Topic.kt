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
    /** Alamat unduh tetap EduVARK untuk materi FILE (C1-02). */
    val contentUrl: String? = null,
    /** Alamat pembukaan tautan eksternal melalui EduVARK untuk materi LINK. */
    val linkTargetUrl: String? = null,
)

data class UploadPayload(
    val fileName: String,
    val mime: String,
    val bytes: ByteArray,
)

/**
 * Status penghapusan objek berkas di penyimpanan (R2) setelah aksi dosen.
 * PENDING berarti perubahan data sudah tersimpan dan materi/berkas lama sudah
 * tidak dapat diakses, tetapi backend masih mencoba menghapus objek secara
 * otomatis.
 */
/**
 * Status penghapusan fisik objek materi setelah perubahan data berhasil.
 *
 * - [NONE] tidak ada operasi penyimpanan yang diperlukan (materi LINK)
 * - [COMPLETED] objek terhapus, atau dipastikan sudah tidak ada
 * - [PENDING] masih dijadwalkan/diproses dan akan dicoba ulang otomatis
 * - [SKIPPED] objek sengaja dipertahankan karena masih dirujuk materi aktif
 * - [FAILED] percobaan otomatis habis; perlu tindak lanjut operator
 * - [UNKNOWN] status fisik belum dapat dipastikan (server lama, pekerjaan hilang)
 *
 * Nilai tak dikenal/absen diperlakukan [UNKNOWN] agar klien tidak menjanjikan
 * penghapusan maupun percobaan ulang yang tidak terbukti.
 */
enum class FileDeletionStatus(val wireValue: String) {
    NONE("NONE"),
    COMPLETED("COMPLETED"),
    PENDING("PENDING"),
    SKIPPED("SKIPPED"),
    FAILED("FAILED"),
    UNKNOWN("UNKNOWN"),
    ;

    companion object {
        fun fromWire(value: String?): FileDeletionStatus =
            entries.firstOrNull { it.wireValue == value } ?: UNKNOWN
    }
}

data class FileReplacementResult(
    val material: Material,
    val previousFileDeletion: FileDeletionStatus,
)
