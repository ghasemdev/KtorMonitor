package ro.cosminmihu.ktor.monitor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okhttp3.WebSocket
import okhttp3.WebSocketListener
import okio.ByteString

/**
 * Opens a WebSocket like [OkHttpClient.newWebSocket] and additionally logs every frame sent and
 * received (text, binary size, close) in [KtorMonitorInterceptor], next to the handshake call.
 *
 * The client must have [KtorMonitorInterceptor] installed with `addInterceptor` (OkHttp does not
 * run network interceptors for WebSocket calls). Without the interceptor this behaves exactly like
 * `newWebSocket`.
 *
 * ```kotlin
 * val webSocket = client.newMonitoredWebSocket(request, listener)
 * ```
 */
@OptIn(InternalKtorMonitorApi::class)
public fun OkHttpClient.newMonitoredWebSocket(
    request: Request,
    listener: WebSocketListener,
): WebSocket {
    val recorder = StreamRecorder(isWebSocket = true)
    val webSocket = newWebSocket(
        request.newBuilder()
            .tag(WebSocketMonitorTag::class.java, WebSocketMonitorTag(recorder))
            .build(),
        MonitoredListener(listener, recorder),
    )
    return MonitoredWebSocket(webSocket, recorder)
}

/** Lets [KtorMonitorInterceptor] attach the logged call to the recorder of a WebSocket. */
@OptIn(InternalKtorMonitorApi::class)
internal class WebSocketMonitorTag(val recorder: StreamRecorder)

@OptIn(InternalKtorMonitorApi::class)
private fun StreamRecorder.text(sent: Boolean, text: String) = record(
    isSent = sent,
    type = StreamRecorder.TYPE_TEXT,
    payload = text.encodeToByteArray(),
)

@OptIn(InternalKtorMonitorApi::class)
private fun StreamRecorder.binary(sent: Boolean, bytes: ByteString) = record(
    isSent = sent,
    type = StreamRecorder.TYPE_BINARY,
    payload = bytes.toByteArray(),
)

@OptIn(InternalKtorMonitorApi::class)
private fun StreamRecorder.close(sent: Boolean, code: Int, reason: String?) = record(
    isSent = sent,
    type = StreamRecorder.TYPE_CLOSE,
    payload = reason?.takeIf { it.isNotEmpty() }?.encodeToByteArray(),
    name = code.toString(),
)

@OptIn(InternalKtorMonitorApi::class)
private class MonitoredListener(
    private val delegate: WebSocketListener,
    private val recorder: StreamRecorder,
) : WebSocketListener() {

    override fun onOpen(webSocket: WebSocket, response: Response) =
        delegate.onOpen(MonitoredWebSocket(webSocket, recorder), response)

    override fun onMessage(webSocket: WebSocket, text: String) {
        recorder.text(false, text)
        delegate.onMessage(MonitoredWebSocket(webSocket, recorder), text)
    }

    override fun onMessage(webSocket: WebSocket, bytes: ByteString) {
        recorder.binary(false, bytes)
        delegate.onMessage(MonitoredWebSocket(webSocket, recorder), bytes)
    }

    override fun onClosing(webSocket: WebSocket, code: Int, reason: String) {
        recorder.close(false, code, reason)
        delegate.onClosing(MonitoredWebSocket(webSocket, recorder), code, reason)
    }

    override fun onClosed(webSocket: WebSocket, code: Int, reason: String) {
        recorder.finish(closeCode = code, closeReason = reason.takeIf { it.isNotEmpty() })
        delegate.onClosed(MonitoredWebSocket(webSocket, recorder), code, reason)
    }

    override fun onFailure(webSocket: WebSocket, t: Throwable, response: Response?) {
        recorder.finish(error = "${t::class.simpleName}: ${t.message.orEmpty()}")
        delegate.onFailure(MonitoredWebSocket(webSocket, recorder), t, response)
    }
}

@OptIn(InternalKtorMonitorApi::class)
private class MonitoredWebSocket(
    private val delegate: WebSocket,
    private val recorder: StreamRecorder,
) : WebSocket by delegate {

    override fun send(text: String): Boolean =
        delegate.send(text).also { if (it) recorder.text(true, text) }

    override fun send(bytes: ByteString): Boolean =
        delegate.send(bytes).also { if (it) recorder.binary(true, bytes) }

    override fun close(code: Int, reason: String?): Boolean =
        delegate.close(code, reason).also {
            if (it) recorder.close(true, code, reason)
        }
}
