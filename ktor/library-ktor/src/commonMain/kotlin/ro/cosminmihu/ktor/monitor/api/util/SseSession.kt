package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.client.call.HttpClientCall
import io.ktor.client.plugins.sse.SSEClientContent
import io.ktor.sse.ServerSentEvent
import io.ktor.utils.io.InternalAPI
import io.ktor.client.plugins.sse.SSESession
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onEach
import ro.cosminmihu.ktor.monitor.InternalKtorMonitorApi
import ro.cosminmihu.ktor.monitor.StreamRecorder
import kotlin.coroutines.CoroutineContext

/**
 * Ktor hands SSE responses to the client as an already parsed [SSESession], so the raw bytes are
 * not observable. Each event the application collects is recorded as a stream event of the call.
 */
@OptIn(InternalAPI::class)
internal fun HttpClientCall.isSseCall(): Boolean = request.content is SSEClientContent

@OptIn(InternalKtorMonitorApi::class)
internal fun SSESession.monitored(
    id: String,
    maxContentLength: Int,
): SSESession {
    val recorder = StreamRecorder(isWebSocket = false)
    recorder.bind(id, maxContentLength)
    return MonitoredSseSession(this, recorder)
}

@OptIn(InternalKtorMonitorApi::class)
private class MonitoredSseSession(
    private val delegate: SSESession,
    private val recorder: StreamRecorder,
) : SSESession {

    override val coroutineContext: CoroutineContext get() = delegate.coroutineContext

    override val incoming: Flow<ServerSentEvent> = delegate.incoming
        .onEach { event ->
            recorder.record(
                isSent = false,
                type = StreamRecorder.TYPE_SSE,
                payload = event.data?.encodeToByteArray(),
                name = event.event,
                eventId = event.id,
                retryMs = event.retry,
            )
        }
        .onCompletion { cause ->
            recorder.finish(
                error = cause
                    ?.takeIf { it !is CancellationException }
                    ?.let { "${it::class.simpleName}: ${it.message.orEmpty()}" },
            )
        }
}
