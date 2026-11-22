package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.AdminUserPage
import com.eduvark.app.domain.model.EnrollmentResult
import com.eduvark.app.domain.model.EnrollmentStatus
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.repository.AdminRepository

fun sampleAdminUser(
    id: String = "user-1",
    loginId: String = "stu1",
    fullName: String = "Mahasiswa Satu",
    role: UserRole = UserRole.STUDENT,
    status: AdminAccountStatus = AdminAccountStatus.ACTIVE,
    enrollmentStatus: EnrollmentStatus? = EnrollmentStatus.ACTIVE,
): AdminUser = AdminUser(
    id = id,
    loginId = loginId,
    fullName = fullName,
    role = role,
    status = status,
    mustChangePassword = false,
    createdAt = "2026-11-20T10:00:00Z",
    enrollmentId = if (role == UserRole.STUDENT) "enrollment-1" else null,
    enrollmentStatus = if (role == UserRole.STUDENT) enrollmentStatus else null,
    classCode = if (role == UserRole.STUDENT) "IBD-01" else null,
    className = if (role == UserRole.STUDENT) "Implementasi Basis Data" else null,
    teachingAssignmentId = if (role == UserRole.LECTURER) "teaching-1" else null,
    teachingClassCode = if (role == UserRole.LECTURER) "IBD-01" else null,
    teachingClassName = if (role == UserRole.LECTURER) "Implementasi Basis Data" else null,
)

class FakeAdminRepository(
    var page: AdminUserPage = AdminUserPage(listOf(sampleAdminUser()), 1, 1, 20),
    var user: AdminUser = sampleAdminUser(),
    var failure: AppFailure? = null,
) : AdminRepository {

    var lastQuery: String? = null
    var lastRole: UserRole? = null
    var lastStatus: AdminAccountStatus? = null
    var lastResetPassword: String? = null
    var lastDeactivated: String? = null

    override suspend fun listUsers(
        query: String?,
        role: UserRole?,
        status: AdminAccountStatus?,
        page: Int,
        pageSize: Int,
    ): AdminUserPage {
        failure?.let { throw it }
        lastQuery = query
        lastRole = role
        lastStatus = status
        return this.page.copy(page = page, pageSize = pageSize)
    }

    override suspend fun getUser(userId: String): AdminUser {
        failure?.let { throw it }
        return user
    }

    override suspend fun createUser(
        loginId: String,
        fullName: String,
        role: UserRole,
        temporaryPassword: String,
    ): AdminUser {
        failure?.let { throw it }
        user = sampleAdminUser(loginId = loginId, fullName = fullName, role = role)
        return user
    }

    override suspend fun updateUser(
        userId: String,
        fullName: String?,
        role: UserRole?,
    ): AdminUser {
        failure?.let { throw it }
        user = user.copy(
            fullName = fullName ?: user.fullName,
            role = role ?: user.role,
        )
        return user
    }

    override suspend fun resetPassword(userId: String, temporaryPassword: String) {
        failure?.let { throw it }
        lastResetPassword = temporaryPassword
        user = user.copy(mustChangePassword = true)
    }

    override suspend fun deactivateUser(userId: String) {
        failure?.let { throw it }
        lastDeactivated = userId
        user = user.copy(status = AdminAccountStatus.INACTIVE)
    }

    override suspend fun updateEnrollment(
        enrollmentId: String,
        active: Boolean,
    ): EnrollmentResult {
        failure?.let { throw it }
        val status = if (active) EnrollmentStatus.ACTIVE else EnrollmentStatus.LEFT
        user = user.copy(enrollmentStatus = status)
        return EnrollmentResult(enrollmentId, status, if (active) null else "2026-11-20T12:00:00Z")
    }
}
