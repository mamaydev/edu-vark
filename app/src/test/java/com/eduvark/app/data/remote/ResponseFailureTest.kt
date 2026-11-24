package com.eduvark.app.data.remote

import com.eduvark.app.domain.error.AppFailure
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

class ResponseFailureTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Test
    fun `successful response reports no failure`() {
        assertNull(Response.success(Unit).toAppFailureOrNull(json))
    }

    @Test
    fun `failed response exposes the server error envelope`() {
        val body = """
            {"error":{"code":"PASSWORD_CHANGE_REQUIRED","message":"Ubah password terlebih dahulu.","details":[],"requestId":"r-1"}}
        """.trimIndent().toResponseBody("application/json".toMediaType())

        val failure = Response.error<Unit>(403, body).toAppFailureOrNull(json)

        assertTrue(failure is AppFailure.Api)
        failure as AppFailure.Api
        assertEquals("PASSWORD_CHANGE_REQUIRED", failure.code)
        assertEquals("Ubah password terlebih dahulu.", failure.message)
        assertEquals(403, failure.statusCode)
    }

    @Test
    fun `failed response without a parseable body still reports an API failure`() {
        val body = "<html>bad gateway</html>".toResponseBody("text/html".toMediaType())

        val failure = Response.error<Unit>(502, body).toAppFailureOrNull(json)

        assertTrue(failure is AppFailure.Api)
        failure as AppFailure.Api
        assertEquals("HTTP_502", failure.code)
        assertEquals("Permintaan gagal.", failure.message)
        assertEquals(502, failure.statusCode)
    }
}
