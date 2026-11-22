package com.eduvark.app.data.remote.dto

import kotlinx.serialization.Serializable

@Serializable
data class LoginRequest(
    val loginId: String,
    val password: String,
)

@Serializable
data class LoginResponse(
    val data: AuthSessionDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class AuthSessionDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
    val user: UserDto,
    val varkRequired: Boolean,
    val activeVark: String? = null,
)

@Serializable
data class UserDto(
    val id: String,
    val loginId: String,
    val fullName: String,
    val role: String,
    val status: String? = null,
    val mustChangePassword: Boolean,
    val activeVark: String? = null,
)

@Serializable
data class RefreshRequest(
    val refreshToken: String,
)

@Serializable
data class RefreshResponse(
    val data: TokenPairDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class TokenPairDto(
    val accessToken: String,
    val refreshToken: String,
    val expiresIn: Int,
)

@Serializable
data class MeResponse(
    val data: UserDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class ChangePasswordRequest(
    val currentPassword: String,
    val newPassword: String,
)

@Serializable
data class VarkRequest(
    val vark: String,
)

@Serializable
data class VarkResponse(
    val data: VarkDataDto? = null,
    val error: ApiErrorDto? = null,
)

@Serializable
data class VarkDataDto(
    val activeVark: String,
    val locked: Boolean = true,
)
