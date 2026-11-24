package com.eduvark.app.core.network

import javax.inject.Inject
import javax.inject.Singleton
import okhttp3.Interceptor
import okhttp3.Request
import okhttp3.Response

/**
 * Menandai setiap request API dengan versi kontrak klien. Backend kandidat
 * menolak request tanpa penanda ini (426), sehingga APK baseline yang sudah
 * terpasang tidak dapat memakai kontrak backend baru.
 *
 * Guard ini tidak menyentuh autentikasi/otorisasi: header hanya menyatakan
 * versi aplikasi dan tidak membawa identitas pengguna.
 */
@Singleton
class ClientCompatibilityInterceptor @Inject constructor() : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response =
        chain.proceed(decorate(chain.request()))

    fun decorate(request: Request): Request =
        request.newBuilder()
            .header(HEADER_NAME, CLIENT_VERSION.toString())
            .build()

    companion object {
        const val HEADER_NAME = "X-EduVARK-Client-Version"
        const val CLIENT_VERSION = 2
    }
}
