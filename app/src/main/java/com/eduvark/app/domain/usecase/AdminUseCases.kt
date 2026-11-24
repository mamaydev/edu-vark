package com.eduvark.app.domain.usecase

import com.eduvark.app.domain.model.AdminAccountStatus
import com.eduvark.app.domain.model.AdminUser
import com.eduvark.app.domain.model.AdminUserPage
import com.eduvark.app.domain.model.EnrollmentResult
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.repository.AdminRepository
import javax.inject.Inject

class ListAdminUsersUseCase @Inject constructor(
    private val repository: AdminRepository,
) {
    suspend operator fun invoke(
        query: String?,
        role: UserRole?,
        status: AdminAccountStatus?,
        page: Int,
        pageSize: Int,
    ): AdminUserPage = repository.listUsers(query, role, status, page, pageSize)
}

class GetAdminUserUseCase @Inject constructor(
    private val repository: AdminRepository,
) {
    suspend operator fun invoke(userId: String): AdminUser = repository.getUser(userId)
}

class CreateAdminUserUseCase @Inject constructor(
    private val repository: AdminRepository,
) {
    suspend operator fun invoke(
        loginId: String?,
        fullName: String,
        role: UserRole,
        temporaryPassword: String,
    ): AdminUser = repository.createUser(loginId, fullName, role, temporaryPassword)
}

class UpdateAdminUserUseCase @Inject constructor(
    private val repository: AdminRepository,
) {
    suspend operator fun invoke(
        userId: String,
        fullName: String?,
        role: UserRole?,
        loginId: String? = null,
    ): AdminUser = repository.updateUser(userId, fullName, role, loginId)
}

class ResetAdminPasswordUseCase @Inject constructor(
    private val repository: AdminRepository,
) {
    suspend operator fun invoke(userId: String, temporaryPassword: String) =
        repository.resetPassword(userId, temporaryPassword)
}

class DeactivateAdminUserUseCase @Inject constructor(
    private val repository: AdminRepository,
) {
    suspend operator fun invoke(userId: String) = repository.deactivateUser(userId)
}

class UpdateEnrollmentUseCase @Inject constructor(
    private val repository: AdminRepository,
) {
    suspend operator fun invoke(enrollmentId: String, active: Boolean): EnrollmentResult =
        repository.updateEnrollment(enrollmentId, active)
}
