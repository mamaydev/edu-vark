package com.eduvark.app.testutil

import com.eduvark.app.domain.model.AuthSession
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.AuthRepository

class FakeAuthRepository : AuthRepository {
    private val session = AuthSession(
        accessToken = "access",
        refreshToken = "refresh",
        user = UserProfile(
            id = "user-1",
            loginId = "2025000001",
            fullName = "Mahasiswa Satu",
            role = UserRole.STUDENT,
            mustChangePassword = false,
            activeVark = null,
        ),
        varkRequired = true,
        activeVark = null,
    )

    override suspend fun login(loginId: String, password: String): AuthSession = session
    override suspend fun logout() = Unit
    override suspend fun me(): UserProfile = session.user
    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmation: String,
    ) = Unit
    override suspend fun selectVark(vark: VarkType): VarkType = vark
}
