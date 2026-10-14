package ro.cosminmihu.ktor.monitor.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import java.io.File
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class LibraryDaoTest {

    private lateinit var file: File
    private lateinit var database: LibraryDatabase
    private lateinit var dao: LibraryDao

    @BeforeTest
    fun setUp() {
        file = File.createTempFile("ktor_monitor_test", ".db").also { it.delete() }
        database = Room.databaseBuilder<LibraryDatabase>(name = file.absolutePath)
            .setDriver(BundledSQLiteDriver())
            .build()
        dao = LibraryDao(database)
    }

    @AfterTest
    fun tearDown() {
        database.close()
        file.delete()
    }

    private suspend fun saveRequest(id: String, timestamp: Long = 1L) = dao.saveRequest(
        id = id,
        method = "GET",
        url = "https://example.com/$id",
        requestTimestamp = timestamp,
        requestHeaders = mapOf("Accept" to listOf("a", "b")),
        requestContentType = null,
        requestContentLength = 0,
        requestBody = byteArrayOf(1, 2),
        isRequestBodyTruncated = false,
    )

    @Test
    fun saveRequestAndResponseRoundTrip() = runTest {
        saveRequest("1")
        dao.saveResponse("1", "HTTP/1.1", 2L, 200, 3L, "text/plain", mapOf("X" to listOf("y")))
        dao.saveResponseBody("1", 3L, byteArrayOf(9), false)

        val call = dao.getCall("1").first()!!
        assertEquals(mapOf("Accept" to listOf("a", "b")), call.requestHeaders)
        assertEquals(mapOf("X" to listOf("y")), call.responseHeaders)
        assertEquals(200L, call.responseCode)
        assertEquals(false, call.isResponseBodyTruncated)
        assertContentEquals(byteArrayOf(9), call.responseBody)
    }

    @Test
    fun appendResponseBodyConcatenatesChunks() = runTest {
        saveRequest("1")
        dao.appendResponseBody("1", byteArrayOf(1, 2), 2, false)
        dao.appendResponseBody("1", byteArrayOf(3), 1, true)

        val call = dao.getCall("1").first()!!
        assertContentEquals(byteArrayOf(1, 2, 3), call.responseBody)
        assertEquals(3L, call.responseContentLength)
        assertEquals(true, call.isResponseBodyTruncated)
    }

    @Test
    fun listingLimitAndDeletion() = runTest {
        saveRequest("1", 1)
        saveRequest("2", 2)
        saveRequest("3", 3)

        assertEquals(listOf("3", "2", "1"), dao.getCalls().first().map { it.id })
        assertEquals(listOf("3", "2"), dao.getCalls(2).first().map { it.id })
        assertEquals(setOf("1", "3"), dao.getCalls(listOf("1", "3")).map { it.id }.toSet())

        dao.deleteCalls(listOf("1"))
        dao.deleteCallsBefore(3)
        assertEquals(listOf("3"), dao.getCalls().first().map { it.id })
        assertNull(dao.getCall("1").first())
    }

    @Test
    fun errorIsStored() = runTest {
        saveRequest("1")
        dao.saveRequest("1", IllegalStateException("boom"))
        assertTrue(dao.getCall("1").first()!!.error!!.contains("boom"))
    }

    @Test
    fun responseWrittenBeforeRequestIsKept() = runTest {
        dao.saveResponse("1", "HTTP/1.1", 5L, 200, 9L, "text/plain", null)
        dao.saveResponseBody("1", 1L, byteArrayOf(7), false)
        saveRequest("1", timestamp = 2L)

        val call = dao.getCall("1").first()!!
        assertEquals("GET", call.method)
        assertEquals(2L, call.requestTimestamp)
        assertEquals(200L, call.responseCode)
        assertContentEquals(byteArrayOf(7), call.responseBody)
        assertEquals(1, dao.getCalls().first().size)
    }

    @Test
    fun responseDoesNotOverwriteRequestTimestamp() = runTest {
        saveRequest("1", timestamp = 2L)
        dao.saveResponse("1", "HTTP/1.1", 5L, 200, 9L, null, null)
        assertEquals(2L, dao.getCall("1").first()!!.requestTimestamp)
    }

    @Test
    fun errorWrittenBeforeRequestIsKept() = runTest {
        dao.saveRequest("1", IllegalStateException("boom"))
        saveRequest("1")
        assertTrue(dao.getCall("1").first()!!.error!!.contains("boom"))
    }
}
