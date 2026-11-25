package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.MaterialDeletionDto
import com.eduvark.app.data.remote.dto.MaterialDeletionResponse
import com.eduvark.app.data.remote.dto.MaterialDto
import com.eduvark.app.data.remote.dto.MaterialFileReplacementDto
import com.eduvark.app.data.remote.dto.MaterialFileReplacementResponse
import com.eduvark.app.data.remote.dto.ReorderTopicsRequest
import com.eduvark.app.data.remote.dto.TopicDto
import com.eduvark.app.data.remote.dto.TopicResponse
import com.eduvark.app.data.remote.dto.UpdateTopicRequest
import com.eduvark.app.domain.error.AppFailure
import com.eduvark.app.domain.model.FileDeletionStatus
import com.eduvark.app.domain.model.UploadPayload
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
import retrofit2.HttpException
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

    private fun materialDto(id: String = "material-1") = MaterialDto(
        id = id,
        topicId = "topic-1",
        vark = "VISUAL",
        title = "Materi",
        kind = "FILE",
        mime = "application/pdf",
        sizeBytes = 10,
    )

    @Test
    fun `delete maps every physical status from both endpoint contracts`() = runTest {
        val expected = listOf(
            "NONE" to FileDeletionStatus.NONE,
            "COMPLETED" to FileDeletionStatus.COMPLETED,
            "PENDING" to FileDeletionStatus.PENDING,
            "SKIPPED" to FileDeletionStatus.SKIPPED,
            "FAILED" to FileDeletionStatus.FAILED,
            "UNKNOWN" to FileDeletionStatus.UNKNOWN,
            null to FileDeletionStatus.UNKNOWN,
            "STATUS_BARU" to FileDeletionStatus.UNKNOWN,
        )

        for ((wire, status) in expected) {
            val repository = repository { name, args ->
                assertEquals("deleteMaterial", name)
                assertEquals("material-1", args!![0])
                Response.success(
                    MaterialDeletionResponse(
                        data = MaterialDeletionDto(id = "material-1", fileDeletion = wire),
                        error = null,
                    ),
                )
            }

            assertEquals(wire, status, repository.deleteMaterial("material-1"))
        }
    }

    @Test
    fun `legacy 204 delete without body is success with unknown physical status`() = runTest {
        val repository = repository { name, _ ->
            assertEquals("deleteMaterial", name)
            Response.success<MaterialDeletionResponse>(null)
        }

        assertEquals(FileDeletionStatus.UNKNOWN, repository.deleteMaterial("material-1"))
    }

    @Test
    fun `delete failure is propagated as AppFailure when the material is gone`() = runTest {
        val repository = repository { name, _ ->
            assertEquals("deleteMaterial", name)
            throw HttpException(
                Response.error<Any>(
                    404,
                    errorBody("MATERIAL_NOT_FOUND", "Materi tidak ditemukan.")
                        .toResponseBody("application/json".toMediaType()),
                ),
            )
        }

        val thrown = runCatching { repository.deleteMaterial("material-1") }.exceptionOrNull()

        val failure = thrown as? AppFailure.Api
        assertEquals("MATERIAL_NOT_FOUND", failure?.code)
        assertEquals(404, failure?.statusCode)
    }

    @Test
    fun `replace file keeps the material and maps every previous deletion status`() = runTest {
        val payload = UploadPayload("pengganti.pdf", "application/pdf", byteArrayOf(1, 2))
        val expected = listOf(
            "NONE" to FileDeletionStatus.NONE,
            "COMPLETED" to FileDeletionStatus.COMPLETED,
            "PENDING" to FileDeletionStatus.PENDING,
            "SKIPPED" to FileDeletionStatus.SKIPPED,
            "FAILED" to FileDeletionStatus.FAILED,
            "UNKNOWN" to FileDeletionStatus.UNKNOWN,
            null to FileDeletionStatus.UNKNOWN,
        )

        for ((wire, status) in expected) {
            val repository = repository { name, args ->
                assertEquals("replaceMaterialFile", name)
                assertEquals("material-1", args!![0])
                assertTrue(args[1] is okhttp3.MultipartBody.Part)
                MaterialFileReplacementResponse(
                    data = MaterialFileReplacementDto(
                        material = materialDto(),
                        previousFileDeletion = wire,
                    ),
                    error = null,
                )
            }

            val result = repository.replaceMaterialFile("material-1", payload)

            assertEquals("material-1", result.material.id)
            assertEquals("Materi", result.material.title)
            assertEquals(wire, status, result.previousFileDeletion)
        }
    }

    @Test
    fun `replace file maps an unknown wire status to unknown`() = runTest {
        val repository = repository { _, _ ->
            MaterialFileReplacementResponse(
                data = MaterialFileReplacementDto(
                    material = materialDto(),
                    previousFileDeletion = "STATUS_BARU",
                ),
                error = null,
            )
        }

        val result = repository.replaceMaterialFile(
            "material-1",
            UploadPayload("pengganti.pdf", "application/pdf", byteArrayOf(1)),
        )

        assertEquals(FileDeletionStatus.UNKNOWN, result.previousFileDeletion)
    }
}
