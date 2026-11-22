package com.eduvark.app.core.network

import com.eduvark.app.core.datastore.SessionStore
import com.eduvark.app.data.remote.dto.RefreshRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import okhttp3.Authenticator
import okhttp3.Request
import okhttp3.Response
import okhttp3.Route

@Singleton
class TokenAuthenticator @Inject constructor(
    private val sessionStore: SessionStore,
    private val refreshApi: RefreshApi,
) : Authenticator {

    /** Serialises refreshes so parallel 401s cannot consume a rotated refresh token twice. */
    private val refreshMutex = Mutex()

    override fun authenticate(route: Route?, response: Response): Request? {
        val path = response.request.url.encodedPath
        if (path.endsWith("/auth/login") || path.endsWith("/auth/refresh")) {
            return null
        }
        if (responseCount(response) >= 2) {
            return null
        }

        val failedToken = response.request
            .header("Authorization")
            ?.removePrefix("Bearer ")
            ?.trim()

        val newAccessToken = runBlocking {
            refreshMutex.withLock {
                val currentAccessToken = sessionStore.accessToken.first()

                // Another call already refreshed while this one waited for the lock.
                if (!currentAccessToken.isNullOrBlank() && currentAccessToken != failedToken) {
                    return@withLock currentAccessToken
                }

                val refreshToken = sessionStore.refreshToken.first()
                    ?: return@withLock null
                val refreshed = try {
                    refreshApi.refresh(RefreshRequest(refreshToken)).data
                } catch (error: Exception) {
                    null
                } ?: return@withLock null

                sessionStore.saveTokens(refreshed.accessToken, refreshed.refreshToken)
                refreshed.accessToken
            }
        }

        if (newAccessToken == null) {
            runBlocking { sessionStore.clear() }
            return null
        }

        return response.request.newBuilder()
            .header("Authorization", "Bearer " + newAccessToken)
            .build()
    }

    private fun responseCount(response: Response): Int {
        var count = 1
        var prior = response.priorResponse
        while (prior != null) {
            count += 1
            prior = prior.priorResponse
        }
        return count
    }
}
