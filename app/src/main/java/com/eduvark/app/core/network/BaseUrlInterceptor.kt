package com.eduvark.app.core.network

import com.eduvark.app.BuildConfig
import com.eduvark.app.core.datastore.SettingsStore
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

@Singleton
class BaseUrlInterceptor @Inject constructor(
    private val settingsStore: SettingsStore,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        // Release builds must always talk to the public API host.
        if (!BuildConfig.ALLOW_API_BASE_URL_OVERRIDE) return chain.proceed(chain.request())

        val override = runBlocking { settingsStore.baseUrl.first() }
        val base = override?.toHttpUrlOrNull()
        if (base == null) return chain.proceed(chain.request())

        val original = chain.request().url
        val rewritten = original.newBuilder()
            .scheme(base.scheme)
            .host(base.host)
            .port(base.port)
            .build()
        return chain.proceed(chain.request().newBuilder().url(rewritten).build())
    }
}
