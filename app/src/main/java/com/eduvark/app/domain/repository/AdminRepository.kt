package com.eduvark.app.domain.repository

import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.AdminUserPage
import com.eduvark.app.domain.model.EnrollmentResult
import com.eduvark.app.domain.model.UserRole

interface AdminRepository {
    suspend fun listUsers(
        query: String?,
        role: UserRole?,
        status: AdminAccountStatus?,
        page: Int,
        pageSize: Int,
    ): AdminUserPage

    suspend fun getUser(userId: String): AdminUser

    suspend fun createUser(
        loginId: String?,
        fullName: String,
        role: UserRole,
        temporaryPassword: String,
    ): AdminUser

    suspend fun updateUser(
        userId: String,
        fullName: String?,
        role: UserRole?,
        loginId: String? = null,
    ): AdminUser

    suspend fun resetPassword(userId: String, temporaryPassword: String)

    suspend fun deactivateUser(userId: String)

    suspend fun updateEnrollment(enrollmentId: String, active: Boolean): EnrollmentResult
}
