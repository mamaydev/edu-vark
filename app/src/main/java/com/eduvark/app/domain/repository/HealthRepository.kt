package com.eduvark.app.domain.repository

import com.eduvark.app.domain.model.HealthStatus

interface HealthRepository {
    suspend fun getHealth(): HealthStatus
}
