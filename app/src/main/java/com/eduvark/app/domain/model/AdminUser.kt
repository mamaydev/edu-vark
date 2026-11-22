package com.eduvark.app.domain.model

enum class AdminAccountStatus(val wireValue: String, val label: String) {
    ACTIVE("ACTIVE", "Aktif"),
    INACTIVE("INACTIVE", "Nonaktif"),
    ;

    companion object {
        fun fromWire(value: String): AdminAccountStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

enum class EnrollmentStatus(val wireValue: String, val label: String) {
    ACTIVE("ACTIVE", "Aktif"),
    LEFT("LEFT", "Keluar"),
    ;

    companion object {
        fun fromWire(value: String): EnrollmentStatus? =
            entries.firstOrNull { it.wireValue == value }
    }
}

data class AdminUser(
    val id: String,
    val loginId: String,
    val fullName: String,
    val role: UserRole,
    val status: AdminAccountStatus,
    val mustChangePassword: Boolean,
    val createdAt: String?,
    val enrollmentId: String?,
    val enrollmentStatus: EnrollmentStatus?,
    val classCode: String?,
    val className: String?,
    val teachingAssignmentId: String?,
    val teachingClassCode: String?,
    val teachingClassName: String?,
)

data class AdminUserPage(
    val items: List<AdminUser>,
    val total: Int,
    val page: Int,
    val pageSize: Int,
)

data class EnrollmentResult(
    val id: String,
    val status: EnrollmentStatus,
    val leftAt: String?,
)
