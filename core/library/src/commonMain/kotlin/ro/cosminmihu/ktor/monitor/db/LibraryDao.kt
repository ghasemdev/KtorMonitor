package ro.cosminmihu.ktor.monitor.db

import io.ktor.utils.io.ioDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

internal class LibraryDao(database: LibraryDatabase) {

    private val callDao = database.callDao()
    private val streamEventDao = database.streamEventDao()

    suspend fun saveRequest(
        id: String,
        method: String,
        url: String,
        requestTimestamp: Long,
        requestHeaders: Map<String, List<String>>,
        requestContentType: String?,
        requestContentLength: Long,
        requestBody: ByteArray?,
        isRequestBodyTruncated: Boolean,
    ) = withContext(ioDispatcher()) {
        callDao.saveRequest(
            id = id,
            method = method,
            url = url,
            requestTimestamp = requestTimestamp,
            requestHeaders = requestHeaders,
            requestContentType = requestContentType,
            requestContentLength = requestContentLength,
            requestBody = requestBody,
            isRequestBodyTruncated = isRequestBodyTruncated,
        )
    }

    suspend fun saveRequest(
        id: String,
        error: Throwable,
    ) = withContext(ioDispatcher()) {
        callDao.saveError(id = id, error = error.stackTraceToString())
    }

    suspend fun saveResponse(
        id: String,
        protocol: String?,
        requestTimestamp: Long,
        responseCode: Int,
        responseTimestamp: Long,
        responseContentType: String?,
        responseHeaders: Map<String, List<String>>?,
    ) = withContext(ioDispatcher()) {
        callDao.saveResponse(
            id = id,
            protocol = protocol,
            requestTimestamp = requestTimestamp,
            responseCode = responseCode.toLong(),
            responseTimestamp = responseTimestamp,
            responseContentType = responseContentType,
            responseHeaders = responseHeaders,
        )
    }

    suspend fun saveResponseBody(
        id: String,
        responseContentLength: Long?,
        responseBody: ByteArray?,
        isResponseBodyTruncated: Boolean,
    ) = withContext(ioDispatcher()) {
        callDao.saveResponseBody(
            id = id,
            responseContentLength = responseContentLength,
            responseBody = responseBody,
            isResponseBodyTruncated = isResponseBodyTruncated,
        )
    }

    suspend fun appendResponseBody(
        id: String,
        chunk: ByteArray,
        deltaSize: Long,
        isResponseBodyTruncated: Boolean,
    ) = withContext(ioDispatcher()) {
        callDao.appendResponseBody(
            id = id,
            chunk = chunk,
            deltaSize = deltaSize,
            isResponseBodyTruncated = isResponseBodyTruncated,
        )
    }

    suspend fun saveResponse(
        id: String,
        error: Throwable,
    ) = withContext(ioDispatcher()) {
        callDao.saveError(id = id, error = error.stackTraceToString())
    }

    fun getCalls(): Flow<List<SelectCalls>> =
        callDao.selectCalls()

    fun getCall(id: String): Flow<Call?> =
        callDao.selectCall(id)

    suspend fun getCalls(ids: Collection<String>): List<Call> = withContext(ioDispatcher()) {
        if (ids.isEmpty()) return@withContext emptyList()
        callDao.selectCallsByIds(ids)
    }

    fun getCalls(limit: Long): Flow<List<SelectCallsWithLimit>> =
        callDao.selectCallsWithLimit(limit)


    suspend fun deleteCalls(ids: Collection<String>) = withContext(ioDispatcher()) {
        if (ids.isEmpty()) return@withContext
        callDao.deleteCallsByIds(ids)
    }

    suspend fun deleteCallsBefore(threshold: Long) = withContext(ioDispatcher()) {
        callDao.deleteCallsBefore(threshold)
    }

    suspend fun openStream(id: String, kind: String) = withContext(ioDispatcher()) {
        callDao.openStream(id = id, kind = kind, status = StreamStatus.OPEN)
    }

    suspend fun closeStream(
        id: String,
        closeCode: Int?,
        closeReason: String?,
        error: String?,
        closedAt: Long,
    ) = withContext(ioDispatcher()) {
        callDao.closeStream(
            id = id,
            status = if (error != null) StreamStatus.FAILED else StreamStatus.CLOSED,
            closeCode = closeCode,
            closeReason = closeReason,
            error = error,
            closedAt = closedAt,
        )
    }

    suspend fun saveStreamEvent(event: StreamEvent) = withContext(ioDispatcher()) {
        streamEventDao.insert(event)
    }

    fun getStreamEvents(callId: String): Flow<List<StreamEvent>> =
        streamEventDao.selectEvents(callId)

    suspend fun getStreamEvents(callIds: Collection<String>): List<StreamEvent> =
        withContext(ioDispatcher()) {
            if (callIds.isEmpty()) return@withContext emptyList()
            streamEventDao.selectEventsByCallIds(callIds)
        }
}
