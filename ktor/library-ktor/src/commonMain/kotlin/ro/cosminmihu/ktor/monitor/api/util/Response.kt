package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.readRawBytes
import io.ktor.http.contentType
import io.ktor.utils.io.readAvailable
import ro.cosminmihu.ktor.monitor.ContentLength
import ro.cosminmihu.ktor.monitor.InternalKtorMonitorApi
import ro.cosminmihu.ktor.monitor.InternalLibraryBridge
import ro.cosminmihu.ktor.monitor.SanitizedHeader
import ro.cosminmihu.ktor.monitor.api.KtorMonitorResponseBody

@OptIn(InternalKtorMonitorApi::class)
internal suspend fun logResponseException(
    id: String,
    cause: Throwable,
) {
    InternalLibraryBridge.saveResponseError(
        id = id,
        error = cause,
    )
}

@OptIn(InternalKtorMonitorApi::class)
internal suspend fun logResponse(
    id: String,
    response: HttpResponse,
    sanitizedHeaders: List<SanitizedHeader>,
) {
    val headers = response.headers.sanitizedHeaders(sanitizedHeaders)

    // Save response.
    InternalLibraryBridge.saveResponse(
        id = id,
        protocol = response.version.toString(),
        requestTimestamp = response.requestTime.timestamp,
        responseCode = response.status.value,
        responseTimestamp = response.responseTime.timestamp,
        responseContentType = response.contentType()?.toString(),
        responseHeaders = headers,
    )
}

private const val WEBSOCKET_UPGRADE_STATUS = 101
private const val SSE_BUFFER_SIZE = 4 * 1024

private fun HttpResponse.isServerSentEvents(): Boolean =
    contentType()?.match("text/event-stream") == true

private fun HttpResponse.isWebSocketUpgrade(): Boolean =
    status.value == WEBSOCKET_UPGRADE_STATUS

@OptIn(InternalKtorMonitorApi::class)
internal suspend fun logResponseBody(
    id: String,
    maxContentLength: Int,
    response: HttpResponse,
) {
    // WebSocket frames are not delivered through the response body — there is nothing
    // to read here without wrapping the WebSocket session itself. Avoid blocking the
    // observer for the lifetime of the socket.
    if (response.isWebSocketUpgrade()) return

    // Server-Sent Events: stream the body chunk-by-chunk and append to the DB so the
    // detail screen updates in real time as new events arrive, instead of waiting for
    // the producer to close the connection.
    if (response.isServerSentEvents()) {
        streamResponseBody(id, maxContentLength, response)
        return
    }

    val overrideBody = if (response.call.attributes.contains(KtorMonitorResponseBody)) {
        response.call.attributes[KtorMonitorResponseBody]
    } else {
        null
    }

    // Read content.
    val responseBody = overrideBody ?: response.readRawBytes()

    val body = when {
        maxContentLength != ContentLength.Full -> responseBody
            .take(maxContentLength)
            .toByteArray()

        else -> responseBody
    }

    // Save response body.
    InternalLibraryBridge.saveResponseBody(
        id = id,
        responseContentLength = responseBody.size.toLong(),
        responseBody = body,
        isResponseBodyTruncated = responseBody.size > maxContentLength,
    )
}

@OptIn(InternalKtorMonitorApi::class)
private suspend fun streamResponseBody(
    id: String,
    maxContentLength: Int,
    response: HttpResponse,
) {
    val channel = response.bodyAsChannel()
    val buffer = ByteArray(SSE_BUFFER_SIZE)
    val unbounded = maxContentLength == ContentLength.Full
    var stored = 0L
    var truncated = false

    // Initialise the body so the UI shows an (empty) streaming response immediately.
    InternalLibraryBridge.saveResponseBody(
        id = id,
        responseContentLength = 0L,
        responseBody = ByteArray(0),
        isResponseBodyTruncated = false,
    )

    try {
        while (true) {
            val read = channel.readAvailable(buffer, 0, buffer.size)
            if (read == -1) break
            if (read == 0) continue

            val keep: ByteArray = when {
                unbounded -> buffer.copyOf(read)
                stored >= maxContentLength -> {
                    truncated = true
                    ByteArray(0)
                }

                stored + read > maxContentLength -> {
                    truncated = true
                    val remaining = (maxContentLength - stored).toInt()
                    buffer.copyOf(remaining)
                }

                else -> buffer.copyOf(read)
            }

            if (keep.isNotEmpty()) {
                stored += keep.size
            }

            InternalLibraryBridge.appendResponseBody(
                id = id,
                chunk = keep,
                deltaSize = read.toLong(),
                isResponseBodyTruncated = truncated,
            )
        }
    } catch (cause: Throwable) {
        // Surface stream errors but don't crash the observer.
        InternalLibraryBridge.saveResponseError(id = id, error = cause)
    }
}

