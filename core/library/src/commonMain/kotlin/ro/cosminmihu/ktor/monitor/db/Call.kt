package ro.cosminmihu.ktor.monitor.db

import androidx.room3.ColumnInfo
import androidx.room3.Entity
import androidx.room3.Index
import androidx.room3.PrimaryKey

@Entity(
    tableName = "call",
    indices = [Index(value = ["requestTimestamp"])],
)
internal data class Call(
    @PrimaryKey val id: String,
    val method: String,
    val url: String,
    val protocol: String? = null,
    val requestTimestamp: Long,
    val requestHeaders: Map<String, List<String>>,
    val requestContentType: String? = null,
    val requestContentLength: Long,
    val requestBody: ByteArray? = null,
    @ColumnInfo(defaultValue = "0") val isRequestBodyTruncated: Boolean? = false,
    val responseCode: Long? = null,
    val responseTimestamp: Long? = null,
    val responseContentType: String? = null,
    val responseHeaders: Map<String, List<String>>? = null,
    val responseContentLength: Long? = null,
    val responseBody: ByteArray? = null,
    @ColumnInfo(defaultValue = "0") val isResponseBodyTruncated: Boolean? = false,
    val error: String? = null,
)

internal data class SelectCalls(
    val id: String,
    val method: String,
    val url: String,
    val requestTimestamp: Long,
    val responseCode: Long?,
    val responseTimestamp: Long?,
    val responseContentType: String?,
    val responseContentLength: Long?,
    val error: String?,
)

internal data class SelectCallsWithLimit(
    val id: String,
    val method: String,
    val url: String,
    val requestTimestamp: Long,
    val responseCode: Long?,
    val responseTimestamp: Long?,
    val responseContentType: String?,
    val responseContentLength: Long?,
    val error: String?,
)
