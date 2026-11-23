package com.eduvark.app.domain.model

enum class SubmissionStatus(val wireValue: String, val label: String) {
    BELUM_MENGUMPULKAN("BELUM_MENGUMPULKAN", "Belum Mengumpulkan"),
    TIDAK_MENGUMPULKAN("TIDAK_MENGUMPULKAN", "Tidak Mengumpulkan"),
    MENUNGGU_PENILAIAN("MENUNGGU_PENILAIAN", "Menunggu Penilaian"),
    SUDAH_DINILAI("SUDAH_DINILAI", "Sudah Dinilai"),
    ;

    companion object {
        fun fromWire(value: String): SubmissionStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class CaseStudy(
    val id: String,
    val topicId: String,
    val theory: String?,
    val instructions: String,
    val deadlineAt: String,
    val deadlineWib: String,
    val status: SubmissionStatus,
    val submittedAt: String?,
    val score: Int?,
    val comment: String?,
    val answer: String? = null,
    val gradedAt: String? = null,
    val gradedBy: String? = null,
    val submissionCount: Int? = null,
)

data class SubmissionResult(
    val submissionId: String,
    val submittedAt: String,
    val status: SubmissionStatus,
)
