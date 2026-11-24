package com.eduvark.app.data.remote

import com.eduvark.app.data.remote.dto.MaterialDto
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Matriks kompatibilitas C1-02 untuk kontrak materi (Android lama x backend baru,
 * Android baru x backend lama). `newBackend*Payload` adalah payload NYATA yang
 * ditangkap dari backend baru pada DB terisolasi `eduvark_test_p2`
 * (`GET /api/v1/topics/:id/materials`, 2026-11-24). `legacyBackend*Payload`
 * disalin dari kontrak b7b6cd6 (`git show b7b6cd6:.../TopicDtos.kt` + respons
 * signedUrl saat itu). Klien memakai konfigurasi JSON produksi: ignoreUnknownKeys.
 */
class MaterialDtoCompatibilityTest {

    private val json = Json { ignoreUnknownKeys = true }

    @Serializable
    private data class LegacyMaterialDto(
        val id: String,
        val topicId: String,
        val vark: String,
        val title: String,
        val kind: String,
        val mime: String? = null,
        val sizeBytes: Long? = null,
        val url: String? = null,
        val signedUrl: String? = null,
        val expiresAt: String? = null,
        val uploadedAt: String? = null,
    )

    private val newBackendFilePayload = """
        {
          "id": "18a0050e-b4d0-425a-9f4b-f94586d3f8f7",
          "topicId": "f17299ae-dc1d-444c-bb0b-efe85b3292e0",
          "vark": "KINESTHETIC",
          "title": "Dataset Kasus",
          "kind": "FILE",
          "mime": "application/pdf",
          "sizeBytes": 51,
          "url": null,
          "contentUrl": "/api/v1/materials/18a0050e-b4d0-425a-9f4b-f94586d3f8f7/content",
          "linkTargetUrl": null,
          "signedUrl": null,
          "expiresAt": null,
          "uploadedAt": "2026-11-24T12:21:08.858Z"
        }
    """.trimIndent()

    private val newBackendLinkPayload = """
        {
          "id": "1ec5883e-3613-4f6e-b9a5-e6b8bf9504ca",
          "topicId": "f17299ae-dc1d-444c-bb0b-efe85b3292e0",
          "vark": "KINESTHETIC",
          "title": "Referensi Kasus",
          "kind": "LINK",
          "mime": null,
          "sizeBytes": null,
          "url": "https://example.com/referensi",
          "contentUrl": null,
          "linkTargetUrl": "/api/v1/materials/1ec5883e-3613-4f6e-b9a5-e6b8bf9504ca/link-target",
          "signedUrl": null,
          "expiresAt": null,
          "uploadedAt": "2026-11-24T12:21:08.883Z"
        }
    """.trimIndent()

    private val legacyBackendFilePayload = """
        {
          "id": "material-1",
          "topicId": "topic-1",
          "vark": "KINESTHETIC",
          "title": "Dataset Kasus",
          "kind": "FILE",
          "mime": "application/pdf",
          "sizeBytes": 1024,
          "url": null,
          "signedUrl": "https://storage.example.com/material-1?X-Amz-Signature=abc",
          "expiresAt": "2026-11-20T10:05:00.000Z",
          "uploadedAt": "2026-11-20T10:00:00.000Z"
        }
    """.trimIndent()

    private val legacyBackendLinkPayload = """
        {
          "id": "material-link",
          "topicId": "topic-1",
          "vark": "KINESTHETIC",
          "title": "Referensi Kasus",
          "kind": "LINK",
          "mime": null,
          "sizeBytes": null,
          "url": "https://example.com/referensi",
          "signedUrl": null,
          "expiresAt": null,
          "uploadedAt": "2026-11-20T10:00:00.000Z"
        }
    """.trimIndent()

    @Test
    fun `new client parses new backend file payload without signed url`() {
        val dto = json.decodeFromString<MaterialDto>(newBackendFilePayload)

        assertEquals("18a0050e-b4d0-425a-9f4b-f94586d3f8f7", dto.id)
        assertEquals(
            "/api/v1/materials/18a0050e-b4d0-425a-9f4b-f94586d3f8f7/content",
            dto.contentUrl,
        )
        assertNull(dto.linkTargetUrl)
        assertNull(dto.signedUrl)
        assertNull(dto.expiresAt)
        // Klien baru tidak pernah memakai URL storage langsung untuk FILE.
        assertNull(dto.url)
    }

    @Test
    fun `new client can still parse legacy backend file payload`() {
        val dto = json.decodeFromString<MaterialDto>(legacyBackendFilePayload)

        assertNull(dto.contentUrl)
        assertNull(dto.linkTargetUrl)
        assertEquals(
            "https://storage.example.com/material-1?X-Amz-Signature=abc",
            dto.signedUrl,
        )
        // Klien baru mengabaikan signedUrl; endpoint tetap oleh id materi sehingga
        // pasangan Android baru x backend lama berakhir 404 (controlled failure).
    }

    @Test
    fun `legacy client parses new backend file payload but cannot open the file`() {
        val dto = json.decodeFromString<LegacyMaterialDto>(newBackendFilePayload)

        assertNull(dto.signedUrl)
        assertNull(dto.expiresAt)
        assertNull(dto.url)
        // Controlled failure: klien lama tidak punya target untuk materi FILE.
    }

    @Test
    fun `legacy client still opens link material from new backend`() {
        val dto = json.decodeFromString<LegacyMaterialDto>(newBackendLinkPayload)

        assertEquals("https://example.com/referensi", dto.url)
        assertNull(dto.signedUrl)
    }

    @Test
    fun `legacy client parses legacy backend payload unchanged`() {
        val file = json.decodeFromString<LegacyMaterialDto>(legacyBackendFilePayload)
        val link = json.decodeFromString<LegacyMaterialDto>(legacyBackendLinkPayload)

        assertEquals(
            "https://storage.example.com/material-1?X-Amz-Signature=abc",
            file.signedUrl,
        )
        assertEquals("https://example.com/referensi", link.url)
    }
}
