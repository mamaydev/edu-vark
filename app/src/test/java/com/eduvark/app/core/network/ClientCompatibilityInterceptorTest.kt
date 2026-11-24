package com.eduvark.app.core.network

import okhttp3.Request
import org.junit.Assert.assertEquals
import org.junit.Test

class ClientCompatibilityInterceptorTest {
    @Test
    fun addsClientVersionHeaderWithoutChangingUrlOrMethod() {
        val request = Request.Builder().url("https://example.test/api/v1/auth/login").build()
        val decorated = ClientCompatibilityInterceptor().decorate(request)

        assertEquals("2", decorated.header(ClientCompatibilityInterceptor.HEADER_NAME))
        assertEquals(request.method, decorated.method)
        assertEquals(request.url, decorated.url)
    }

    @Test
    fun headerValueMatchesBackendMinimumContract() {
        assertEquals("X-EduVARK-Client-Version", ClientCompatibilityInterceptor.HEADER_NAME)
        assertEquals(2, ClientCompatibilityInterceptor.CLIENT_VERSION)
    }
}
