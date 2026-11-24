package com.eduvark.app.data.remote

import com.eduvark.app.domain.error.AppFailure
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CancellationException
import kotlinx.serialization.json.Json

@Singleton
class ApiExecutor @Inject constructor(
    private val json: Json,
) {
    suspend fun <T> execute(block: suspend () -> T): T {
        return try {
            safeApiCall(json, block)
        } catch (error: CancellationException) {
            // Pembatalan coroutine (mis. pengguna menutup layar) bukan kegagalan API.
            throw error
        } catch (error: ApiException) {
            throw AppFailure.Api(error.code, error.message, error.statusCode)
        } catch (error: AppFailure) {
            throw error
        } catch (error: Exception) {
            throw AppFailure.Unknown
        }
    }
}
