package com.eduvark.app.domain.usecase

import com.eduvark.app.domain.model.HealthStatus
import com.eduvark.app.domain.repository.HealthRepository
import javax.inject.Inject

class GetHealthUseCase @Inject constructor(
    private val healthRepository: HealthRepository,
) {
    suspend operator fun invoke(): HealthStatus = healthRepository.getHealth()
}
