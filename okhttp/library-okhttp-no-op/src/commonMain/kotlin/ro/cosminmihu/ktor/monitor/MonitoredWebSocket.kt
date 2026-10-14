package ro.cosminmihu.ktor.monitor

import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.WebSocket
import okhttp3.WebSocketListener

/**
 * No-op implementation: behaves exactly like [OkHttpClient.newWebSocket].
 */
public fun OkHttpClient.newMonitoredWebSocket(
    request: Request,
    listener: WebSocketListener,
): WebSocket = newWebSocket(request, listener)
