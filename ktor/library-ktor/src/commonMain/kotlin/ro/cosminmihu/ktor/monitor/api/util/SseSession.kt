package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.sse.SSEClientContent
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.InternalAPI
import io.ktor.client.plugins.sse.SSESession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onEach
import ro.cosminmihu.ktor.monitor.ContentLength
import ro.cosminmihu.ktor.monitor.InternalKtorMonitorApi
import ro.cosminmihu.ktor.monitor.InternalLibraryBridge
import kotlin.coroutines.CoroutineContext

/**
 * Ktor hands SSE responses to the client as an already parsed [SSESession], so the raw bytes are
 * not observable. Events are re-serialized to the `text/event-stream` format and appended to the
 * stored body in the order they are received.
 */
@OptIn(InternalAPI::class)
internal fun HttpClientCall.isSseCall(): Boolean = request.content is SSEClientContent

@OptIn(InternalKtorMonitorApi::class)
internal suspend fun SSESession.monitored(
    id: String,
    maxContentLength: Int,
): SSESession {
    try {
        InternalLibraryBridge.saveResponseBody(
            id = id,
            responseContentLength = 0L,
            responseBody = ByteArray(0),
            isResponseBodyTruncated = false,
        )
    } catch (cause: CancellationException) {
        throw cause
    } catch (_: Throwable) {
    }
    return MonitoredSseSession(this, id, maxContentLength)
}

@OptIn(InternalKtorMonitorApi::class)
private class MonitoredSseSession(
    private val delegate: SSESession,
    private val id: String,
    private val maxContentLength: Int,
) : SSESession {

    private var stored = 0L
    private var truncated = false

    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    override val incoming: Flow<ServerSentEvent> = delegate.incoming.onEach { record(it) }

    private suspend fun record(event: ServerSentEvent) {
        val bytes = event.serialize().encodeToByteArray()
        val keep = when {
            maxContentLength == ContentLength.Full -> bytes
            stored >= maxContentLength -> {
                truncated = true
                ByteArray(0)
            }
            stored + bytes.size > maxContentLength -> {
                truncated = true
                bytes.copyOf((maxContentLength - stored).toInt())
            }
            else -> bytes
        }
        stored += keep.size

        try {
            InternalLibraryBridge.appendResponseBody(
                id = id,
                chunk = keep,
                deltaSize = bytes.size.toLong(),
                isResponseBodyTruncated = truncated,
            )
        } catch (cause: CancellationException) {
            throw cause
        } catch (_: Throwable) {
        }
    }
}

private fun ServerSentEvent.serialize(): String = buildString {
    comments?.lineSequence()?.forEach { append(": ").append(it).append('\n') }
    id?.let { append("id: ").append(it).append('\n') }
    event?.let { append("event: ").append(it).append('\n') }
    retry?.let { append("retry: ").append(it).append('\n') }
    data?.lineSequence()?.forEach { append("data: ").append(it).append('\n') }
    append('\n')
}
