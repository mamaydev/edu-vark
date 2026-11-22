package com.eduvark.app.domain.model

data class UserProfile(
    val id: String,
    val loginId: String,
    val fullName: String,
    val role: UserRole,
    val mustChangePassword: Boolean,
    val activeVark: VarkType?,
)
