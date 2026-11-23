package com.eduvark.app.domain.repository

import com.eduvark.app.domain.model.AuthSession
import com.eduvark.app.domain.model.UserProfile
import com.eduvark.app.domain.model.VarkType

interface AuthRepository {
    suspend fun login(loginId: String, password: String): AuthSession
    suspend fun logout()
    suspend fun me(): UserProfile
    suspend fun changePassword(
        currentPassword: String,
        newPassword: String,
        confirmation: String,
    )
    suspend fun selectVark(vark: VarkType): VarkType
}
