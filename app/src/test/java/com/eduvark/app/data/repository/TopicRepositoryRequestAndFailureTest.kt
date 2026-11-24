package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.ReorderTopicsRequest
import com.eduvark.app.data.remote.dto.TopicDto
import com.eduvark.app.data.remote.dto.TopicResponse
import com.eduvark.app.data.remote.dto.UpdateTopicRequest
import com.eduvark.app.domain.error.AppFailure
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.Response

/**
 * Remediasi cross-check P5:
 *
 * (1) `PATCH /lecturer/topics/{id}` harus benar-benar mengirim `"description": null`
 *     saat dosen mengosongkan deskripsi. Aplikasi memakai `Json { explicitNulls = false }`,
 *     jadi DTO memakai `JsonElement` + `JsonNull` agar null tidak dihilangkan dari body.
 * (2) `Response<Unit>` (delete/reorder) tidak melempar `HttpException`, sehingga
 *     non-2xx harus dipropagasikan sebagai [AppFailure.Api] agar UI menampilkan gagal
 *     dan memulihkan urutan server.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TopicRepositoryRequestAndFailureTest {

    private val json = Json {
        ignoreUnknownKeys = true
        explicitNulls = false
    }

    private fun topicDto(id: String = "topic-1") = TopicDto(
        id = id,
        title = "Topik",
        description = null,
        sortOrder = 1,
        status = "PUBLISHED",
        materialAvailable = true,
    )

    private fun errorBody(code: String, message: String): String =
        """{"error":{"code":"$code","message":"$message"}}"""

    private fun repository(handler: (String, Array<Any?>?) -> Any?): TopicRepositoryImpl {
        val api = Proxy.newProxyInstance(
            EduVarkApi::class.java.classLoader,
            arrayOf(EduVarkApi::class.java),
        ) { _, method, args -> handler(method.name, args) } as EduVarkApi
        return TopicRepositoryImpl(
            api = api,
            executor = ApiExecutor(json),
            json = json,
        )
    }

    @Test
    fun `clearing description sends explicit description null in JSON`() = runTest {
        var capturedId: String? = null
        var capturedRequest: UpdateTopicRequest? = null
        val repository = repository { name, args ->
            assertEquals("updateTopic", name)
            capturedId = args!![0] as String
            capturedRequest = args[1] as UpdateTopicRequest
            TopicResponse(data = topicDto(), error = null)
        }

        repository.updateTopic(
            topicId = "topic-1",
            title = "Judul Baru",
            description = null,
        )

        assertEquals("topic-1", capturedId)
        val encoded = json.encodeToString(UpdateTopicRequest.serializer(), capturedRequest!!)
        assertTrue(
            "JSON harus memuat description null eksplisit, aktual: $encoded",
            encoded.contains("\"description\":null"),
        )
        assertFalse(
            "JSON tidak boleh mengirim description sebagai string kosong",
            encoded.contains("\"\""),
        )
        assertTrue(encoded.contains("\"title\":\"Judul Baru\""))
    }

    @Test
    fun `filled description is sent as JSON string not null`() = runTest {
        var capturedRequest: UpdateTopicRequest? = null
        val repository = repository { _, args ->
            capturedRequest = args!![1] as UpdateTopicRequest
            TopicResponse(data = topicDto(), error = null)
        }

        repository.updateTopic(
            topicId = "topic-1",
            title = "Judul",
            description = "Deskripsi baru",
        )

        val encoded = json.encodeToString(UpdateTopicRequest.serializer(), capturedRequest!!)
        assertTrue(encoded.contains("\"description\":\"Deskripsi baru\""))
        assertFalse(encoded.contains("\"description\":null"))
    }

    @Test
    fun `delete failure with academic data is propagated as AppFailure`() = runTest {
        val repository = repository { name, _ ->
            assertEquals("deleteTopic", name)
            Response.error<Unit>(
                409,
                errorBody(
                    "TOPIC_HAS_ACADEMIC_DATA",
                    "Topik sudah memiliki submission/nilai. Gunakan unpublish/arsip.",
                ).toResponseBody("application/json".toMediaType()),
            )
        }

        val thrown = runCatching { repository.deleteTopic("topic-1") }.exceptionOrNull()

        val failure = thrown as? AppFailure.Api
        assertEquals("TOPIC_HAS_ACADEMIC_DATA", failure?.code)
        assertEquals(409, failure?.statusCode)
        assertEquals(
            "Topik sudah memiliki submission/nilai. Gunakan unpublish/arsip.",
            failure?.message,
        )
    }

    @Test
    fun `successful delete does not throw`() = runTest {
        val repository = repository { name, args ->
            assertEquals("deleteTopic", name)
            assertEquals("topic-1", args!![0])
            Response.success<Unit>(Unit)
        }

        assertNull(runCatching { repository.deleteTopic("topic-1") }.exceptionOrNull())
    }

    @Test
    fun `reorder failure is propagated and restores opportunity for reload`() = runTest {
        val repository = repository { name, _ ->
            assertEquals("reorderTopics", name)
            Response.error<Unit>(
                422,
                errorBody(
                    "TOPIC_ORDER_INCOMPLETE",
                    "Daftar urutan harus memuat seluruh topik aktif.",
                ).toResponseBody("application/json".toMediaType()),
            )
        }

        val thrown = runCatching { repository.reorderTopics(listOf("topic-1")) }.exceptionOrNull()

        val failure = thrown as? AppFailure.Api
        assertEquals("TOPIC_ORDER_INCOMPLETE", failure?.code)
        assertEquals(422, failure?.statusCode)
    }

    @Test
    fun `reorder sends the complete ordered list for more than 50 topics`() = runTest {
        var capturedRequest: ReorderTopicsRequest? = null
        val repository = repository { name, args ->
            assertEquals("reorderTopics", name)
            capturedRequest = args!![0] as ReorderTopicsRequest
            Response.success<Unit>(Unit)
        }
        val ids = (1..55).map { "topic-$it" }

        repository.reorderTopics(ids)

        assertEquals(55, capturedRequest?.topicIds?.size)
        assertEquals(ids, capturedRequest?.topicIds)
    }

    @Test
    fun `unknown body on non-2xx yields generic HTTP failure code`() = runTest {
        val repository = repository { _, _ ->
            Response.error<Unit>(
                500,
                "bukan json".toResponseBody("text/plain".toMediaType()),
            )
        }

        val thrown = runCatching { repository.deleteTopic("topic-1") }.exceptionOrNull()

        val failure = thrown as? AppFailure.Api
        assertEquals("HTTP_500", failure?.code)
        assertEquals(500, failure?.statusCode)
    }
}
