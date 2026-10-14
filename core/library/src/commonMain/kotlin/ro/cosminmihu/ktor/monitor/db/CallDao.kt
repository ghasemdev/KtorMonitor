package ro.cosminmihu.ktor.monitor.db

import androidx.room3.Dao
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

private const val CALL_COLUMNS =
    "id, method, url, protocol, requestTimestamp, requestHeaders, requestContentType, requestContentLength, " +
        "requestBody, isRequestBodyTruncated, responseCode, responseTimestamp, responseContentType, " +
        "responseHeaders, responseContentLength, responseBody, isResponseBodyTruncated, error, " +
        "streamKind, streamStatus, streamCloseCode, streamCloseReason, streamError, streamClosedAt"

@Dao
internal interface CallDao {

    @Query(
        "INSERT INTO call (id, method, url, requestTimestamp, requestHeaders, requestContentType, " +
            "requestContentLength, requestBody, isRequestBodyTruncated) " +
            "VALUES (:id, :method, :url, :requestTimestamp, :requestHeaders, :requestContentType, " +
            ":requestContentLength, :requestBody, :isRequestBodyTruncated) " +
            "ON CONFLICT(id) DO UPDATE SET method = excluded.method, url = excluded.url, " +
            "requestTimestamp = excluded.requestTimestamp, requestHeaders = excluded.requestHeaders, " +
            "requestContentType = excluded.requestContentType, " +
            "requestContentLength = excluded.requestContentLength, requestBody = excluded.requestBody, " +
            "isRequestBodyTruncated = excluded.isRequestBodyTruncated"
    )
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
    )

    // Creates an empty row when a response/error is written before its request. `saveRequest` later
    // fills the request columns in, so the order of the writes does not matter.
    @Query(
        "INSERT OR IGNORE INTO call (id, method, url, requestTimestamp, requestHeaders, " +
            "requestContentLength, isRequestBodyTruncated) " +
            "VALUES (:id, '', '', :requestTimestamp, '{}', 0, 0)"
    )
    suspend fun ensureCall(id: String, requestTimestamp: Long)

    @Query(
        "UPDATE call SET protocol = :protocol, " +
            "responseCode = :responseCode, responseTimestamp = :responseTimestamp, " +
            "responseContentType = :responseContentType, responseHeaders = :responseHeaders WHERE id = :id"
    )
    suspend fun saveResponse(
        id: String,
        protocol: String?,
        responseCode: Long,
        responseTimestamp: Long,
        responseContentType: String?,
        responseHeaders: Map<String, List<String>>?,
    )

    @Query(
        "UPDATE call SET responseContentLength = :responseContentLength, responseBody = :responseBody, " +
            "isResponseBodyTruncated = :isResponseBodyTruncated WHERE id = :id"
    )
    suspend fun saveResponseBody(
        id: String,
        responseContentLength: Long?,
        responseBody: ByteArray?,
        isResponseBodyTruncated: Boolean,
    )

    // Append a chunk of bytes to the (possibly null) responseBody and bump the running content-length.
    // Used by SSE / streamed responses where the body grows over time while the connection is still
    // open. The UI observes the call row reactively, so every append re-renders the body viewer.
    // `||` yields TEXT, so the result is cast back to BLOB.
    @Query(
        "UPDATE call SET responseBody = CAST(COALESCE(responseBody, zeroblob(0)) || :chunk AS BLOB), " +
            "responseContentLength = COALESCE(responseContentLength, 0) + :deltaSize, " +
            "isResponseBodyTruncated = :isResponseBodyTruncated WHERE id = :id"
    )
    suspend fun appendResponseBody(
        id: String,
        chunk: ByteArray,
        deltaSize: Long,
        isResponseBodyTruncated: Boolean,
    )

    @Query("UPDATE call SET streamKind = :kind, streamStatus = :status WHERE id = :id")
    suspend fun openStream(id: String, kind: String, status: String)

    @Query(
        "UPDATE call SET streamStatus = :status, streamCloseCode = :closeCode, " +
            "streamCloseReason = :closeReason, streamError = :error, streamClosedAt = :closedAt WHERE id = :id"
    )
    suspend fun closeStream(
        id: String,
        status: String,
        closeCode: Int?,
        closeReason: String?,
        error: String?,
        closedAt: Long,
    )

    @Query("UPDATE call SET error = :error WHERE id = :id")
    suspend fun saveError(id: String, error: String)

    @Query(
        "SELECT id, method, url, requestTimestamp, responseCode, responseTimestamp, responseContentType, " +
            "responseContentLength, error, streamKind FROM call ORDER BY requestTimestamp DESC"
    )
    fun selectCalls(): Flow<List<SelectCalls>>

    @Query(
        "SELECT id, method, url, requestTimestamp, responseCode, responseTimestamp, responseContentType, " +
            "responseContentLength, error, streamKind FROM call ORDER BY requestTimestamp DESC LIMIT :limit"
    )
    fun selectCallsWithLimit(limit: Long): Flow<List<SelectCallsWithLimit>>

    @Query("SELECT $CALL_COLUMNS FROM call WHERE id = :id")
    fun selectCall(id: String): Flow<Call?>

    @Query("SELECT $CALL_COLUMNS FROM call WHERE id IN (:ids)")
    suspend fun selectCallsByIds(ids: Collection<String>): List<Call>

    @Query("DELETE FROM call WHERE id IN (:ids)")
    suspend fun deleteCallsByIds(ids: Collection<String>)

    @Query("DELETE FROM call WHERE requestTimestamp < :threshold")
    suspend fun deleteCallsBefore(threshold: Long)
}
