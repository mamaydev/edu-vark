package com.eduvark.app.data.repository

import com.eduvark.app.core.network.EduVarkApi
import com.eduvark.app.data.remote.ApiExecutor
import com.eduvark.app.data.remote.dto.MetaDto
import com.eduvark.app.data.remote.dto.TopicDto
import com.eduvark.app.data.remote.dto.TopicListResponse
import java.lang.reflect.Proxy
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * FR-017 / AT-11: klien Android harus mengambil SELURUH halaman topik, bukan
 * hanya halaman pertama, dan mempertahankan urutan yang diberikan backend.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class TopicRepositoryPaginationTest {

    private fun topicDto(index: Int) = TopicDto(
        id = "topic-" + index,
        title = "Topik " + index,
        description = "Deskripsi " + index,
        sortOrder = index,
        status = "PUBLISHED",
        materialAvailable = true,
    )

    /**
     * @param serverPageSize bila diisi, mensimulasikan server yang mengembalikan
     *   lebih sedikit baris per halaman daripada `pageSize` yang diminta klien
     *   (klien tetap harus meminta halaman berikutnya sampai total tercapai).
     * @param total bila null, respons tidak menyertakan `meta` sama sekali.
     */
    private fun repository(
        requestedPages: MutableList<Pair<Int, Int>>,
        total: Int?,
        serverPageSize: Int? = null,
    ): TopicRepositoryImpl {
        val api = Proxy.newProxyInstance(
            EduVarkApi::class.java.classLoader,
            arrayOf(EduVarkApi::class.java),
        ) { _, method, args ->
            if (method.name == "listTopics") {
                val page = args!![0] as Int
                val pageSize = args[1] as Int
                requestedPages += page to pageSize
                val effectivePageSize =
                    if (serverPageSize != null && serverPageSize < pageSize) serverPageSize else pageSize
                val data = if (total == null) {
                    (1..5).map { topicDto(it) }
                } else {
                    val first = (page - 1) * effectivePageSize + 1
                    val last = minOf(page * effectivePageSize, total)
                    (first..last).map { topicDto(it) }
                }
                TopicListResponse(
                    data = data,
                    meta = total?.let { MetaDto(page = page, pageSize = pageSize, total = it) },
                    error = null,
                )
            } else {
                error("Unexpected API call: " + method.name)
            }
        } as EduVarkApi
        val json = Json { ignoreUnknownKeys = true; explicitNulls = false }
        return TopicRepositoryImpl(api = api, executor = ApiExecutor(json), json = json)
    }

    @Test
    fun `fetches every page until meta total is reached`() = runTest {
        val requested = mutableListOf<Pair<Int, Int>>()
        // Server hanya mengembalikan 20 baris per halaman walau klien meminta 100.
        val repository = repository(requestedPages = requested, total = 55, serverPageSize = 20)

        val topics = repository.listTopics()

        assertEquals(55, topics.size)
        assertEquals("topic-1", topics.first().id)
        assertEquals("topic-55", topics.last().id)
        // Urutan backend (sortOrder) tidak boleh diubah klien.
        assertEquals(2, topics[1].sortOrder)
        assertEquals(listOf(1, 2, 3), requested.map { it.first })
        assertEquals(listOf(100, 100, 100), requested.map { it.second })
    }

    @Test
    fun `single page response without meta returns that page only`() = runTest {
        val requested = mutableListOf<Pair<Int, Int>>()
        val repository = repository(requestedPages = requested, total = null)

        val topics = repository.listTopics()

        assertEquals(5, topics.size)
        assertEquals(listOf(1), requested.map { it.first })
    }

    @Test
    fun `deep pagination still accumulates all topics in order`() = runTest {
        val requested = mutableListOf<Pair<Int, Int>>()
        // 250 topik dengan pageSize 100 harus memerlukan 3 halaman.
        val repository = repository(requestedPages = requested, total = 250)

        val topics = repository.listTopics()

        assertEquals(250, topics.size)
        assertEquals(1, topics.first().sortOrder)
        assertEquals(250, topics.last().sortOrder)
        assertEquals(listOf(1, 2, 3), requested.map { it.first })
        assertEquals(3, requested.size)
    }
}
