package com.eduvark.app.testutil

import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AuthSession
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.AuthRepository

fun sampleSession(
    varkRequired: Boolean = true,
    activeVark: VarkType? = null,
): AuthSession = AuthSession(
    accessToken = "access-token",
    refreshToken = "refresh-token",
    user = UserProfile(
        id = "user-1",
        loginId = "stu1",
        fullName = "Mahasiswa Satu",
        role = UserRole.STUDENT,
        mustChangePassword = false,
        activeVark = activeVark,
    ),
    varkRequired = varkRequired,
    activeVark = activeVark,
)

class FakeAuthRepository(
    var loginResult: AuthSession = sampleSession(),
    var loginFailure: AppFailure? = null,
    var selectVarkResult: VarkType = VarkType.VISUAL,
    var selectVarkFailure: AppFailure? = null,
    var changePasswordFailure: AppFailure? = null,
) : AuthRepository {

    override suspend fun login(loginId: String, password: String): AuthSession {
        loginFailure?.let { throw it }
        return loginResult
    }

    override suspend fun logout() = Unit

    override suspend fun me(): UserProfile = loginResult.user

    override suspend fun changePassword(currentPassword: String, newPassword: String) {
        changePasswordFailure?.let { throw it }
    }

    override suspend fun selectVark(vark: VarkType): VarkType {
        selectVarkFailure?.let { throw it }
        return selectVarkResult
    }
}
