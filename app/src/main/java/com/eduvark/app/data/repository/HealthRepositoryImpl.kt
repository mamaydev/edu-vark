package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.domain.model.HealthStatus
import com.eduvark.app.domain.repository.HealthRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class HealthRepositoryImpl @Inject constructor(
    private val api: EduVarkApi,
) : HealthRepository {
    override suspend fun getHealth(): HealthStatus {
        val response = api.health()
        val data = response.data
            ?: error(response.error?.message ?: "Respons health tidak berisi data.")
        return HealthStatus(
            status = data.status,
            service = data.service,
            timestamp = data.timestamp,
        )
    }
}
