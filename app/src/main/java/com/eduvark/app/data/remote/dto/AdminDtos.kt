package com.eduvark.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class AdminUserDto(
    val id: String,
    val loginId: String,
    val fullName: String,
    val role: String,
    val status: String,
    val mustChangePassword: Boolean = false,
    val createdAt: String? = null,
    val enrollmentId: String? = null,
    val enrollmentStatus: String? = null,
    val classCode: String? = null,
    val className: String? = null,
    val teachingAssignmentId: String? = null,
    val teachingClassCode: String? = null,
    val teachingClassName: String? = null,
)

@Serializable
data class AdminUserListResponse(
    val data: List<AdminUserDto>? = null,
    val meta: MetaDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class AdminUserResponse(
    val data: AdminUserDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class CreateAdminUserRequest(
    val loginId: String,
    val fullName: String,
    val role: String,
    val temporaryPassword: String,
)

@Serializable
data class UpdateAdminUserRequest(
    val fullName: String? = null,
    val role: String? = null,
)

@Serializable
data class ResetPasswordRequest(
    val temporaryPassword: String,
)

@Serializable
data class EnrollmentStatusRequest(
    val status: String,
)

@Serializable
data class EnrollmentDto(
    val id: String,
    val status: String,
    val leftAt: String? = null,
)

@Serializable
data class EnrollmentResponse(
    val data: EnrollmentDto? = null,
    val error: ApiErrorDto? = null,
)
