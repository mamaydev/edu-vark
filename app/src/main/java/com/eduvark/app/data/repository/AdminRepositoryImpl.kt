package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.AdminUserDto
import com.eduvark.app.data.remote.dto.CreateAdminUserRequest
import com.eduvark.app.data.remote.dto.EnrollmentStatusRequest
import com.eduvark.app.data.remote.dto.ResetPasswordRequest
import com.eduvark.app.data.remote.dto.UpdateAdminUserRequest
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.AdminUserPage
import com.eduvark.app.domain.model.EnrollmentResult
import com.eduvark.app.domain.model.EnrollmentStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.repository.AdminRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AdminRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
    private val executor: ApiExecutor,
) : AdminRepository {

    override suspend fun listUsers(
        query: String?,
        role: UserRole?,
        status: AdminAccountStatus?,
        page: Int,
        pageSize: Int,
    ): AdminUserPage {
        val response = executor.execute {
            api.listAdminUsers(
                query = query,
                role = role?.wireValue,
                status = status?.wireValue,
                page = page,
                pageSize = pageSize,
            )
        }
        return AdminUserPage(
            items = response.data.orEmpty().map { it.toDomain() },
            total = response.meta?.total ?: 0,
            page = response.meta?.page ?: page,
            pageSize = response.meta?.pageSize ?: pageSize,
        )
    }

    override suspend fun getUser(userId: String): AdminUser {
        val response = executor.execute { api.getAdminUser(userId) }
        return response.data?.toDomain() ?: throw AppFailure.Unknown
    }

    override suspend fun createUser(
        loginId: String,
        fullName: String,
        role: UserRole,
        temporaryPassword: String,
    ): AdminUser {
        val response = executor.execute {
            api.createAdminUser(
                CreateAdminUserRequest(
                    loginId = loginId,
                    fullName = fullName,
                    role = role.wireValue,
                    temporaryPassword = temporaryPassword,
                ),
            )
        }
        return response.data?.toDomain() ?: throw AppFailure.Unknown
    }

    override suspend fun updateUser(
        userId: String,
        fullName: String?,
        role: UserRole?,
    ): AdminUser {
        val response = executor.execute {
            api.updateAdminUser(userId, UpdateAdminUserRequest(fullName, role?.wireValue))
        }
        return response.data?.toDomain() ?: throw AppFailure.Unknown
    }

    override suspend fun resetPassword(userId: String, temporaryPassword: String) {
        executor.execute { api.resetAdminPassword(userId, ResetPasswordRequest(temporaryPassword)) }
    }

    override suspend fun deactivateUser(userId: String) {
        executor.execute { api.deactivateAdminUser(userId) }
    }

    override suspend fun updateEnrollment(
        enrollmentId: String,
        active: Boolean,
    ): EnrollmentResult {
        val response = executor.execute {
            api.updateEnrollment(
                enrollmentId,
                EnrollmentStatusRequest(if (active) "ACTIVE" else "LEFT"),
            )
        }
        val data = response.data ?: throw AppFailure.Unknown
        return EnrollmentResult(
            id = data.id,
            status = EnrollmentStatus.fromWire(data.status) ?: throw AppFailure.Unknown,
            leftAt = data.leftAt,
        )
    }

    private fun AdminUserDto.toDomain(): AdminUser {
        val role = UserRole.fromWire(role) ?: throw AppFailure.Unknown
        val status = AdminAccountStatus.fromWire(status) ?: throw AppFailure.Unknown
        return AdminUser(
            id = id,
            loginId = loginId,
            fullName = fullName,
            role = role,
            status = status,
            mustChangePassword = mustChangePassword,
            createdAt = createdAt,
            enrollmentId = enrollmentId,
            enrollmentStatus = enrollmentStatus?.let { EnrollmentStatus.fromWire(it) },
            classCode = classCode,
            className = className,
            teachingAssignmentId = teachingAssignmentId,
            teachingClassCode = teachingClassCode,
            teachingClassName = teachingClassName,
        )
    }
}
