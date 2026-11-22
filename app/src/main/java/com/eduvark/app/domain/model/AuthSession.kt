package com.eduvark.app.domain.model

data class AuthSession(
    val accessToken: String,
    val refreshToken: String,
    val user: UserProfile,
    val varkRequired: Boolean,
    val activeVark: VarkType?,
)
