package com.eduvark.app.data.repository

import com.eduvark.app.core.datastore.SessionStore
import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiException
import com.eduvark.app.data.remote.dto.ChangePasswordRequest
import com.eduvark.app.data.remote.dto.LoginRequest
import com.eduvark.app.data.remote.dto.UserDto
import com.eduvark.app.data.remote.dto.VarkRequest
import com.eduvark.app.data.remote.safeApiCall
import com.eduvark.app.data.remote.toAppFailureOrNull
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.AuthSession
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.model.UserRole
import com.eduvark.app.domain.model.VarkType
import com.eduvark.app.domain.repository.AuthRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.serialization.json.Json

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
    private val sessionStore: SessionStore,
    private val json: Json,
) : AuthRepository {

    private suspend fun <T> execute(
        endpoint: String? = null,
        block: suspend () -> T,
    ): T {
        return try {
            safeApiCall(json, block, endpoint)
        } catch (error: ApiException) {
            throw AppFailure.Api(error.code, error.message, error.statusCode)
        } catch (error: AppFailure) {
            throw error
        } catch (error: Exception) {
            throw AppFailure.Unknown
        }
    }

    override suspend fun login(loginId: String, password: String): AuthSession {
        val response = execute("POST /auth/login") {
            api.login(LoginRequest(loginId = loginId, password = password))
        }
        val data = response.data ?: throw AppFailure.Unknown
        val activeVark = data.activeVark?.let {
            VarkType.fromWire(it) ?: throw AppFailure.Unknown
        }
        val session = AuthSession(
            accessToken = data.accessToken,
            refreshToken = data.refreshToken,
            user = data.user.toDomain(),
            varkRequired = data.varkRequired,
            activeVark = activeVark,
        )
        sessionStore.saveSession(session)
        return session
    }

    override suspend fun logout() {
        val response = execute("POST /auth/logout") { api.logout() }
        response.toAppFailureOrNull(json)?.let { throw it }
        // The server confirmed the session revocation; only now may the local
        // session be discarded (FR-001/DEC-005).
        sessionStore.clear()
    }

    override suspend fun me(): UserProfile {
        val response = execute { api.me() }
        val data = response.data ?: throw AppFailure.Unknown
        val user = data.toDomain()
        sessionStore.updateUser(user)
        return user
    }

    override suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmation: String,
    ) {
        val response = execute("POST /auth/change-password") {
            api.changePassword(
                ChangePasswordRequest(
                    currentPassword = currentPassword,
                    newPassword = newPassword,
                    confirmPassword = confirmation,
                ),
            )
        }
        response.toAppFailureOrNull(json)?.let { throw it }
        // All server sessions are revoked on success, so the local session is
        // cleared only after a valid HTTP success (FR-003/DEC-007).
        sessionStore.clear()
    }

    override suspend fun selectVark(vark: VarkType): VarkType {
        val response = execute { api.selectVark(VarkRequest(vark.wireValue)) }
        val data = response.data ?: throw AppFailure.Unknown
        val selected = VarkType.fromWire(data.activeVark) ?: throw AppFailure.Unknown
        sessionStore.updateActiveVark(selected)
        return selected
    }

    private fun UserDto.toDomain(): UserProfile {
        val role = UserRole.fromWire(role) ?: throw AppFailure.Unknown
        val vark = activeVark?.let {
            VarkType.fromWire(it) ?: throw AppFailure.Unknown
        }
        return UserProfile(
            id = id,
            loginId = loginId,
            fullName = fullName,
            role = role,
            mustChangePassword = mustChangePassword,
            activeVark = vark,
        )
    }
}
