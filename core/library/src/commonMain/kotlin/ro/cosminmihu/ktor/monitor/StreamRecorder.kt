package ro.cosminmihu.ktor.monitor

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import ro.cosminmihu.ktor.monitor.db.LibraryDao
import ro.cosminmihu.ktor.monitor.db.StreamEvent
import ro.cosminmihu.ktor.monitor.db.StreamEventType
import ro.cosminmihu.ktor.monitor.db.StreamKind
import ro.cosminmihu.ktor.monitor.di.LibraryKoinContext
import kotlin.time.Clock
import kotlin.time.Duration.Companion.minutes
import kotlin.time.ExperimentalTime

private const val MAX_EVENTS = 5_000

/**
 * Records the frames of a WebSocket or the events of a Server-Sent Events stream for one call.
 *
 * Events are queued without suspending and written by a single coroutine, so the stored order is
 * the order of the `record*` calls. The recorder starts persisting once [bind] is called with the
 * call id; until then events are buffered. Not thread-safe for concurrent writers of the same
 * direction, except for [record], which can be called from any thread.
 *
 * Internal API, do not use directly.
 */
@InternalKtorMonitorApi
@OptIn(ExperimentalTime::class)
public class StreamRecorder(isWebSocket: Boolean) {

    private val kind = if (isWebSocket) StreamKind.WEB_SOCKET else StreamKind.SSE
    private val target = CompletableDeferred<Target?>()
    private val items = Channel<Item>(Channel.UNLIMITED)
    private var sseBuffer = ByteArray(0)
    private val sseEvent = SseEventBuilder()

    private class Target(val id: String, val maxContentLength: Int)

    private sealed interface Item {
        class Event(
            val isSent: Boolean,
            val type: String,
            val name: String?,
            val eventId: String?,
            val retryMs: Long?,
            val payload: ByteArray?,
            val size: Long,
            val timestamp: Long,
        ) : Item

        class Finish(
            val closeCode: Int?,
            val closeReason: String?,
            val error: String?,
            val timestamp: Long,
        ) : Item
    }

    init {
        InternalLibraryBridge.coroutineScope().launch {
            // Give up when the call is never logged (monitor not installed on this client).
            val target = withTimeoutOrNull(BIND_TIMEOUT) { target.await() }
            if (target == null) {
                items.cancel()
                return@launch
            }
            val dao = LibraryKoinContext.koin.get<LibraryDao>()
            try {
                dao.openStream(target.id, kind)
                var stored = 0
                for (item in items) {
                    when (item) {
                        is Item.Event -> {
                            if (stored >= MAX_EVENTS) continue
                            stored++
                            val max = target.maxContentLength
                            val payload = item.payload
                            val isTruncated = payload != null &&
                                max != ContentLength.Full && payload.size > max
                            dao.saveStreamEvent(
                                StreamEvent(
                                    callId = target.id,
                                    isSent = item.isSent,
                                    type = item.type,
                                    name = item.name,
                                    eventId = item.eventId,
                                    retryMs = item.retryMs,
                                    payload = if (isTruncated) payload.copyOf(max) else payload,
                                    size = item.size,
                                    isTruncated = isTruncated,
                                    timestamp = item.timestamp,
                                )
                            )
                        }

                        is Item.Finish -> dao.closeStream(
                            id = target.id,
                            closeCode = item.closeCode,
                            closeReason = item.closeReason,
                            error = item.error,
                            closedAt = item.timestamp,
                        )
                    }
                }
            } catch (cause: CancellationException) {
                throw cause
            } catch (_: Throwable) {
            }
        }
    }

    /** Starts persisting into the call [id]. Payloads are truncated to [maxContentLength]. */
    public fun bind(id: String, maxContentLength: Int) {
        target.complete(Target(id, maxContentLength))
    }

    /** The call is not logged; drops everything recorded. */
    public fun discard() {
        target.complete(null)
    }

    /**
     * Records a WebSocket frame or an SSE event.
     *
     * @param type One of the `TYPE_*` constants.
     * @param name WebSocket close code or SSE event name.
     */
    public fun record(
        isSent: Boolean,
        type: String,
        payload: ByteArray? = null,
        size: Long = payload?.size?.toLong() ?: 0L,
        name: String? = null,
        eventId: String? = null,
        retryMs: Long? = null,
    ) {
        items.trySend(
            Item.Event(
                isSent = isSent,
                type = type,
                name = name,
                eventId = eventId,
                retryMs = retryMs,
                payload = payload,
                size = size,
                timestamp = Clock.System.now().toEpochMilliseconds(),
            )
        )
    }

    /**
     * Feeds raw `text/event-stream` bytes; every complete event is recorded. Bytes may be split
     * anywhere, including inside a UTF-8 sequence.
     */
    public fun appendSseBytes(bytes: ByteArray) {
        sseBuffer += bytes
        var start = 0
        while (true) {
            val end = sseBuffer.indexOf('\n'.code.toByte(), start)
            if (end < 0) break
            var lineEnd = end
            if (lineEnd > start && sseBuffer[lineEnd - 1] == '\r'.code.toByte()) lineEnd--
            val line = sseBuffer.decodeToString(start, lineEnd)
            sseEvent.addRawSize(end - start + 1L)
            start = end + 1
            sseEvent.line(line)?.let { event ->
                record(
                    isSent = false,
                    type = StreamEventType.SSE,
                    payload = event.data?.encodeToByteArray(),
                    size = event.size,
                    name = event.name,
                    eventId = event.id,
                    retryMs = event.retry,
                )
            }
        }
        sseBuffer = sseBuffer.copyOfRange(start, sseBuffer.size)
    }

    /**
     * Marks the stream as ended. A non-null [error] marks it as failed. Calling it more than once
     * is harmless; later calls are ignored.
     */
    public fun finish(closeCode: Int? = null, closeReason: String? = null, error: String? = null) {
        if (items.trySend(
                Item.Finish(
                    closeCode = closeCode,
                    closeReason = closeReason,
                    error = error,
                    timestamp = Clock.System.now().toEpochMilliseconds(),
                )
            ).isSuccess
        ) {
            items.close()
        }
    }

    public companion object {
        public const val TYPE_TEXT: String = StreamEventType.TEXT
        public const val TYPE_BINARY: String = StreamEventType.BINARY
        public const val TYPE_PING: String = StreamEventType.PING
        public const val TYPE_PONG: String = StreamEventType.PONG
        public const val TYPE_CLOSE: String = StreamEventType.CLOSE
        public const val TYPE_ERROR: String = StreamEventType.ERROR
        public const val TYPE_SSE: String = StreamEventType.SSE

        private val BIND_TIMEOUT = 1.minutes
    }
}

private fun ByteArray.indexOf(byte: Byte, from: Int): Int {
    for (index in from until size) if (this[index] == byte) return index
    return -1
}

private class SseEvent(
    val name: String?,
    val id: String?,
    val retry: Long?,
    val data: String?,
    val size: Long,
)

/** Accumulates `text/event-stream` lines into events, per the SSE specification. */
private class SseEventBuilder {
    private var name: String? = null
    private var id: String? = null
    private var retry: Long? = null
    private var data: StringBuilder? = null
    private var size = 0L

    fun addRawSize(bytes: Long) {
        size += bytes
    }

    /** Returns the completed event when [line] is the blank line that ends it. */
    fun line(line: String): SseEvent? {
        if (line.isEmpty()) {
            val event = SseEvent(name, id, retry, data?.toString(), size)
            val hasContent = data != null || name != null || id != null || retry != null
            reset()
            return event.takeIf { hasContent }
        }
        if (line.startsWith(":")) return null
        val colon = line.indexOf(':')
        val field = if (colon < 0) line else line.substring(0, colon)
        val value = if (colon < 0) "" else line.substring(colon + 1).removePrefix(" ")
        when (field) {
            "event" -> name = value
            "id" -> id = value
            "retry" -> retry = value.toLongOrNull() ?: retry
            "data" -> data = (data?.append('\n') ?: StringBuilder()).append(value)
        }
        return null
    }

    private fun reset() {
        name = null
        id = null
        retry = null
        data = null
        size = 0L
    }
}
