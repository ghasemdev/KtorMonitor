package ro.cosminmihu.ktor.monitor

import kotlinx.coroutines.launch
import org.http4k.core.ContentType
import org.http4k.core.Filter
import org.http4k.core.HttpHandler
import org.http4k.core.Response
import ro.cosminmihu.ktor.monitor.domain.model.NetworkClient
import java.io.BufferedInputStream
import java.io.FilterInputStream
import java.io.IOException
import kotlin.time.Clock

/**
 * An [http4k](https://www.http4k.org/) [Filter] that provides the capability to log HTTP calls.
 *
 * You can learn more from [KtorMonitor](https://github.com/CosminMihuMDC/KtorMonitor).
 *
 * ```kotlin
 * KtorMonitorFilter {
 *     sanitizeHeader { header -> header == "Authorization" }
 *     isActive = true
 *     showNotification = true
 *     retentionPeriod = RetentionPeriod.OneHour
 *     maxContentLength = ContentLength.Default
 * }.then(JavaHttpClient())
 * ```
 */
public class KtorMonitorFilter(
    private val config: KtorMonitorFilterConfig = KtorMonitorFilterConfig(),
) : Filter {

    public constructor(block: KtorMonitorFilterConfig.() -> Unit) : this(
        KtorMonitorFilterConfig().apply(block)
    )

    init {
        @OptIn(InternalKtorMonitorApi::class)
        InternalLibraryBridge.setConfig(
            isActive = config.isActive,
            showNotification = config.showNotification,
            retentionPeriod = config.retentionPeriod,
            maxContentLength = config.maxContentLength,
            networkClient = NetworkClient.Http4k,
        )

        if (config.isActive && config.retentionPeriod.isPositive()) {
            @OptIn(InternalKtorMonitorApi::class)
            InternalLibraryBridge.startListening()
        }
    }

    @OptIn(InternalKtorMonitorApi::class)
    override fun invoke(next: HttpHandler): HttpHandler = { request ->
        // Check if plugin is active.
        if (!config.isActive || !config.retentionPeriod.isPositive()) {
            next(request)
        } else {
            // Filter.
            val shouldLog = config.filters.isEmpty() || config.filters.any { it(request) }
            if (!shouldLog) {
                next(request)
            } else {
                // Generate call id.
                val id = InternalLibraryBridge.newCallId()

                // Log request.
                val requestTimestamp = Clock.System.now().toEpochMilliseconds()
                val method = request.method.name
                val url = request.uri.toString()
                val requestContentType = request.header("Content-Type")

                // Request body bytes — duplicate ByteBuffer to avoid consuming the original.
                var requestBodySize: Long? = null
                val requestBodyBytes = try {
                    val buf = request.body.payload.duplicate()
                    requestBodySize = buf.remaining().toLong()
                    ByteArray(minOf(buf.remaining(), config.maxContentLength)).also { buf.get(it) }
                } catch (_: Throwable) {
                    null
                }
                val requestContentLength = request.body.length
                    ?: requestBodySize
                    ?: 0L

                // Request headers.
                val requestHeaders = InternalLibraryBridge.sanitizeHeaders(
                    request.headers.groupBy({ it.first }) { it.second ?: "" },
                    { name -> config.sanitizedHeaders.firstOrNull { it.predicate(name) }?.placeholder },
                )

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
                            requestBody = requestBodyBytes,
                            isRequestBodyTruncated = requestContentLength != 0L && requestContentLength > config.maxContentLength,
                        )
                    } catch (_: Throwable) {
                    }
                }

                // Proceed with request.
                val response = try {
                    next(request)
                } catch (cause: Throwable) {
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
                val responseCode = response.status.code
                // http4k does not expose the HTTP protocol version; default to HTTP/1.1.
                val protocol = "HTTP/1.1"
                val responseContentType = response.header("Content-Type")

                // Response headers.
                val responseHeaders = InternalLibraryBridge.sanitizeHeaders(
                    response.headers.groupBy({ it.first }) { it.second ?: "" },
                    { name -> config.sanitizedHeaders.firstOrNull { it.predicate(name) }?.placeholder },
                )

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
                    } catch (_: Throwable) {
                    }
                }

                // Server-Sent Events — the body never closes on its own, so eagerly reading
                // `payload` (which fully drains the stream) would block indefinitely. Instead,
                // wrap the stream so each chunk read by the consumer is mirrored into the DB,
                // letting the detail screen update live.
                val isServerSentEvents = responseContentType
                    ?.substringBefore(';')
                    ?.trim()
                    ?.equals(ContentType.TEXT_EVENT_STREAM.value, ignoreCase = true) == true

                if (isServerSentEvents) {
                    wrapForStreaming(response, id, config.maxContentLength)
                } else {
                    // Response body — duplicate ByteBuffer so the caller still receives a full body.
                    var responseBodySize: Long? = null
                    val truncatedBody = try {
                        val buf = response.body.payload.duplicate()
                        responseBodySize = buf.remaining().toLong()
                        ByteArray(minOf(buf.remaining(), config.maxContentLength)).also { buf.get(it) }
                    } catch (_: Throwable) {
                        null
                    }
                    val responseContentLength = responseBodySize
                        ?: response.body.length
                        ?: 0L
                    val isResponseBodyTruncated = responseContentLength > config.maxContentLength

                    InternalLibraryBridge.coroutineScope().launch {
                        try {
                            InternalLibraryBridge.saveResponseBody(
                                id = id,
                                responseContentLength = responseContentLength,
                                responseBody = truncatedBody ?: ByteArray(0),
                                isResponseBodyTruncated = isResponseBodyTruncated,
                            )
                        } catch (_: Throwable) {
                        }
                    }

                    response
                }
            }
        }
    }
}

@OptIn(InternalKtorMonitorApi::class)
private fun wrapForStreaming(
    response: Response,
    id: String,
    maxContentLength: Int,
): Response {
    val recorder = StreamRecorder(isWebSocket = false)
    recorder.bind(id, maxContentLength)

    val originalLength = response.body.length

    val tee = object : FilterInputStream(BufferedInputStream(response.body.stream)) {
        override fun read(): Int {
            val byte = try {
                super.read()
            } catch (cause: IOException) {
                recorder.finish(error = "${cause::class.simpleName}: ${cause.message.orEmpty()}")
                throw cause
            }
            if (byte < 0) {
                recorder.finish()
                return byte
            }
            recorder.appendSseBytes(byteArrayOf(byte.toByte()))
            return byte
        }

        // Hand data to the consumer one line at a time, so only what the consumer actually
        // received is recorded instead of everything that was buffered.
        override fun read(b: ByteArray, off: Int, len: Int): Int {
            if (len == 0) return 0
            var read = 0
            try {
                while (read < len) {
                    val byte = super.read()
                    if (byte < 0) break
                    b[off + read++] = byte.toByte()
                    if (byte == '\n'.code) break
                }
            } catch (cause: IOException) {
                if (read == 0) {
                    recorder.finish(error = "${cause::class.simpleName}: ${cause.message.orEmpty()}")
                    throw cause
                }
            }
            if (read == 0) {
                recorder.finish()
                return -1
            }
            recorder.appendSseBytes(b, off, read)
            return read
        }

        override fun available(): Int = 0

        override fun close() {
            recorder.finish()
            super.close()
        }
    }

    return response.body(tee, originalLength)
}
