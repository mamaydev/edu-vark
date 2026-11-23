package com.eduvark.app.domain.usecase

import com.eduvark.app.domain.model.AuthSession
import com.eduvark.app.domain.repository.AuthRepository
import javax.inject.Inject

class LoginUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(loginId: String, password: String): AuthSession =
        authRepository.login(loginId, password)
}

class LogoutUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.logout()
}

class GetMeUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke() = authRepository.me()
}

class ChangePasswordUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(
        currentPassword: String,
        newPassword: String,
        confirmation: String,
    ) = authRepository.changePassword(currentPassword, newPassword, confirmation)
}

class SelectVarkUseCase @Inject constructor(
    private val authRepository: AuthRepository,
) {
    suspend operator fun invoke(vark: com.eduvark.app.domain.model.VarkType) =
        authRepository.selectVark(vark)
}
