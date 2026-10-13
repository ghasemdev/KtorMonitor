package ro.cosminmihu.ktor.monitor.db

import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LibraryDaoWebTest {

    @Test
    fun roundTripOverWebWorker() = runTest {
        val dao = LibraryDao(createDatabase())

        dao.saveRequest(
            id = "1",
            method = "GET",
            url = "https://example.com/1",
            requestTimestamp = 1_700_000_000_000L,
            requestHeaders = mapOf("Accept" to listOf("a", "b")),
            requestContentType = null,
            requestContentLength = 2,
            requestBody = byteArrayOf(1, 2),
            isRequestBodyTruncated = false,
        )
        dao.saveResponse("1", "HTTP/1.1", 1_700_000_000_001L, 200, 1_700_000_000_002L, "text/plain", mapOf("X" to listOf("y")))
        dao.appendResponseBody("1", byteArrayOf(1, 2), 2, false)
        dao.appendResponseBody("1", byteArrayOf(3), 1, true)

        val call = dao.getCall("1").first()!!
        assertEquals(mapOf("Accept" to listOf("a", "b")), call.requestHeaders)
        assertEquals(mapOf("X" to listOf("y")), call.responseHeaders)
        assertEquals(200L, call.responseCode)
        assertContentEquals(byteArrayOf(1, 2), call.requestBody)
        assertContentEquals(byteArrayOf(1, 2, 3), call.responseBody)
        assertEquals(3L, call.responseContentLength)
        assertEquals(true, call.isResponseBodyTruncated)
        assertEquals(listOf("1"), dao.getCalls().first().map { it.id })

        dao.deleteCalls(listOf("1"))
        assertNull(dao.getCall("1").first())
    }
}
