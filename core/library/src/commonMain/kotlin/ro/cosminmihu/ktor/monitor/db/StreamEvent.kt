package ro.cosminmihu.ktor.monitor.db

import androidx.room3.Entity
import androidx.room3.ForeignKey
import androidx.room3.Index
import androidx.room3.PrimaryKey

/** A single WebSocket frame or Server-Sent Event of a [Call]. */
@Entity(
    tableName = "stream_event",
    foreignKeys = [
        ForeignKey(
            entity = Call::class,
            parentColumns = ["id"],
            childColumns = ["callId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["callId"])],
)
internal data class StreamEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val callId: String,
    // Sent by the app (true) or received from the server (false).
    val isSent: Boolean,
    // See StreamEventType.
    val type: String,
    // SSE `event:` field.
    val name: String? = null,
    // SSE `id:` field.
    val eventId: String? = null,
    // SSE `retry:` field.
    val retryMs: Long? = null,
    val payload: ByteArray? = null,
    // Real size on the wire, before truncation.
    val size: Long,
    val isTruncated: Boolean = false,
    val timestamp: Long,
)

internal object StreamEventType {
    const val TEXT = "TEXT"
    const val BINARY = "BINARY"
    const val PING = "PING"
    const val PONG = "PONG"
    const val CLOSE = "CLOSE"
    const val ERROR = "ERROR"
    const val SSE = "SSE"
}

internal object StreamKind {
    const val WEB_SOCKET = "WS"
    const val SSE = "SSE"
}

internal object StreamStatus {
    const val OPEN = "OPEN"
    const val CLOSED = "CLOSED"
    const val FAILED = "FAILED"
}
