package ro.cosminmihu.ktor.monitor

import kotlinx.coroutines.launch
import okhttp3.Interceptor
import okhttp3.MediaType
import okhttp3.Response
import okhttp3.ResponseBody
import okhttp3.ResponseBody.Companion.asResponseBody
import okio.Buffer
import okio.BufferedSource
import okio.ForwardingSource
import okio.Source
import okio.buffer
import ro.cosminmihu.ktor.monitor.domain.model.NetworkClient
import okio.IOException
import kotlin.math.min
import kotlin.time.Clock
import kotlin.time.ExperimentalTime
import kotlin.uuid.Uuid

/**
 * An [OkHttp](https://square.github.io/okhttp/) [Interceptor] that provides the capability to log HTTP calls.
 *
 * You can learn more from [KtorMonitor](https://github.com/CosminMihuMDC/KtorMonitor).
 *
 * ```kotlin
 * OkHttpClient.Builder()
 *     .addInterceptor(
 *         KtorMonitorInterceptor {
 *             sanitizeHeader { header -> header == "Authorization" }
 *             isActive = true
 *             showNotification = true
 *             retentionPeriod = RetentionPeriod.OneHour
 *             maxContentLength = ContentLength.Default
 *         }
 *     )
 *     .build()
 * ```
 *
 * Install it with `addInterceptor`, not `addNetworkInterceptor`: OkHttp does not run network
 * interceptors for WebSocket calls, so a WebSocket handshake would not be logged. To also log
 * WebSocket frames, open the socket with [newMonitoredWebSocket].
 */
public class KtorMonitorInterceptor(
    private val config: KtorMonitorInterceptorConfig = KtorMonitorInterceptorConfig(),
) : Interceptor {

    public constructor(block: KtorMonitorInterceptorConfig.() -> Unit) : this(
        KtorMonitorInterceptorConfig().apply(block)
    )

    init {
        @OptIn(InternalKtorMonitorApi::class)
        InternalLibraryBridge.setConfig(
            isActive = config.isActive,
            showNotification = config.showNotification,
            retentionPeriod = config.retentionPeriod,
            maxContentLength = config.maxContentLength,
            networkClient = NetworkClient.OkHttp,
        )

        if (config.isActive && config.retentionPeriod.isPositive()) {
            @OptIn(InternalKtorMonitorApi::class)
            InternalLibraryBridge.startListening()
        }
    }

    @OptIn(InternalKtorMonitorApi::class, ExperimentalTime::class)
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        // Present only for WebSocket calls opened through `newMonitoredWebSocket`.
        val webSocketTag = request.tag(WebSocketMonitorTag::class.java)

        // Check if plugin is active.
        if (!config.isActive) return chain.proceed(request).also { webSocketTag?.recorder?.discard() }
        // Check if retention period is zero.
        if (!config.retentionPeriod.isPositive()) {
            return chain.proceed(request).also { webSocketTag?.recorder?.discard() }
        }

        // Filter.
        val shouldLog = config.filters.isEmpty() || config.filters.any { it(request) }
        if (!shouldLog) return chain.proceed(request).also { webSocketTag?.recorder?.discard() }

        // Generate call id.
        val id = callIdentifier

        // Log request.
        val requestTimestamp = Clock.System.now().toEpochMilliseconds()
        val method = request.method
        val url = request.url.toString()
        val requestContentType = request.body?.contentType()?.toString()
        val requestContentLength = request.body?.contentLength() ?: 0L

        // Headers.
        val requestHeaders = request.headers.toMultimap()
            .mapValues { (key, values) ->
                val sanitized = config.sanitizedHeaders.firstOrNull { it.predicate(key) }
                if (sanitized != null) listOf(sanitized.placeholder) else values
            }
            .toSortedMap()

        // Request body.
        val requestBody = try {
            val buffer = okio.Buffer()
            request.body?.writeTo(buffer)
            val bytes = buffer.readByteArray()
            if (config.maxContentLength != ContentLength.Full) {
                bytes.take(config.maxContentLength).toByteArray()
            } else bytes
        } catch (_: Throwable) {
            null
        }

        InternalLibraryBridge.coroutineScope().launch {
            try {
                InternalLibraryBridge.saveRequest(
                    id = id,
                    method = method,
                    url = url,
                    requestTimestamp = requestTimestamp,
                    requestHeaders = requestHeaders,
                    requestContentType = requestContentType,
                    requestContentLength = requestContentLength,
                    requestBody = requestBody,
                    isRequestBodyTruncated = requestContentLength != 0L && requestContentLength > config.maxContentLength,
                )
            } catch (_: Throwable) {
            }
        }

        // Proceed with request.
        val response: Response
        try {
            response = chain.proceed(request)
        } catch (cause: Throwable) {
            webSocketTag?.recorder?.discard()
            InternalLibraryBridge.coroutineScope().launch {
                try {
                    InternalLibraryBridge.saveRequestError(id = id, error = cause)
                } catch (_: Throwable) {
                }
            }
            throw cause
        }

        // Log response.
        val responseTimestamp = Clock.System.now().toEpochMilliseconds()
        val responseCode = response.code
        val protocol = response.protocol.toString()
        val responseContentType = response.body.contentType()?.toString()

        // Response headers.
        val responseHeaders = response.headers.toMultimap()
            .mapValues { (key, values) ->
                val sanitized = config.sanitizedHeaders.firstOrNull { it.predicate(key) }
                if (sanitized != null) listOf(sanitized.placeholder) else values
            }
            .toSortedMap()

        // Response body.
        val responseBody = response.body
        val responseContentTypeRaw = responseBody.contentType()?.toString()
        val isWebSocketUpgrade = responseCode == WEBSOCKET_UPGRADE_STATUS
        val isServerSentEvents = responseContentTypeRaw
            ?.substringBefore(';')
            ?.trim()
            ?.equals("text/event-stream", ignoreCase = true) == true

        // Always persist the response head first.
        InternalLibraryBridge.coroutineScope().launch {
            try {
                InternalLibraryBridge.saveResponse(
                    id = id,
                    protocol = protocol,
                    requestTimestamp = requestTimestamp,
                    responseCode = responseCode,
                    responseTimestamp = responseTimestamp,
                    responseContentType = responseContentType,
                    responseHeaders = responseHeaders,
                )
                // Frames can only be appended once the call row exists.
                if (isWebSocketUpgrade) {
                    webSocketTag?.recorder?.bind(id, config.maxContentLength)
                }
            } catch (_: Throwable) {
            } finally {
                webSocketTag?.recorder?.discard()
            }
        }

        // WebSocket upgrade — frames travel through the upgraded socket and are recorded by
        // `newMonitoredWebSocket`. Don't try to read the body or we'd block the upgrade
        // indefinitely.
        if (isWebSocketUpgrade) {
            return response
        }

        // Server-Sent Events — wrap the body so each chunk read by the consumer is
        // mirrored into the DB, letting the detail screen update live.
        if (isServerSentEvents) {
            return wrapForStreaming(response, responseBody, id, config.maxContentLength)
        }

        // Default (non-streaming) path: drain the buffered body once.
        val responseBodySource = responseBody.source()
        responseBodySource.request(Long.MAX_VALUE)
        val responseBodyBytes = responseBodySource.buffer.clone().readByteArray()
        val responseContentLength = responseBodyBytes.size.toLong()

        val truncatedBody =
            if (config.maxContentLength != ContentLength.Full) {
                responseBodyBytes.take(config.maxContentLength).toByteArray()
            } else {
                responseBodyBytes
            }
        val isResponseBodyTruncated = responseContentLength > config.maxContentLength

        InternalLibraryBridge.coroutineScope().launch {
            try {
                InternalLibraryBridge.saveResponseBody(
                    id = id,
                    responseContentLength = responseContentLength,
                    responseBody = truncatedBody,
                    isResponseBodyTruncated = isResponseBodyTruncated,
                )
            } catch (_: Throwable) {
            }
        }

        return response
    }
}

private const val WEBSOCKET_UPGRADE_STATUS = 101
private const val MAX_STREAM_LINE_LENGTH = 64L * 1024

@OptIn(InternalKtorMonitorApi::class)
private fun wrapForStreaming(
    response: Response,
    body: ResponseBody,
    id: String,
    maxContentLength: Int,
): Response {
    val recorder = StreamRecorder(isWebSocket = false)
    recorder.bind(id, maxContentLength)

    val contentType: MediaType? = body.contentType()
    val originalContentLength = body.contentLength()

    val upstream = body.source()
    val tee = object : ForwardingSource(upstream) {
        override fun read(sink: Buffer, byteCount: Long): Long {
            // Hand data to the consumer one line at a time, so only what the consumer actually
            // received is recorded instead of everything the socket had buffered.
            val lineEnd = upstream.indexOf('\n'.code.toByte(), 0L, MAX_STREAM_LINE_LENGTH)
            val available = if (lineEnd >= 0L) lineEnd + 1 else upstream.buffer.size
            val sizeBefore = sink.size
            val read = try {
                super.read(sink, if (available > 0L) min(byteCount, available) else byteCount)
            } catch (cause: IOException) {
                recorder.finish(error = "${cause::class.simpleName}: ${cause.message.orEmpty()}")
                throw cause
            }
            if (read < 0L) recorder.finish()
            if (read <= 0L) return read

            // Snapshot only the newly read bytes from the sink.
            val snapshot = Buffer()
            sink.copyTo(snapshot, sizeBefore, read)
            recorder.appendSseBytes(snapshot.readByteArray())
            return read
        }

        override fun close() {
            recorder.finish()
            super.close()
        }
    }

    val wrappedSource: BufferedSource = (tee as Source).buffer()
    val wrappedBody: ResponseBody = wrappedSource.asResponseBody(contentType, originalContentLength)
    return response.newBuilder().body(wrappedBody).build()
}

private val callIdentifier: String
    get() = Uuid.random().toString()
