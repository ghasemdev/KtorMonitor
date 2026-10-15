package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import ro.cosminmihu.ktor.monitor.InternalKtorMonitorApi
import ro.cosminmihu.ktor.monitor.InternalLibraryBridge
import ro.cosminmihu.ktor.monitor.SanitizedHeader
import kotlin.time.Clock

@OptIn(InternalKtorMonitorApi::class)
internal suspend fun logRequestException(
    id: String,
    cause: Throwable,
) {
    InternalLibraryBridge.saveRequestError(
        id = id,
        error = cause,
    )
}

@OptIn(InternalKtorMonitorApi::class)
internal suspend fun logRequest(
    id: String,
    maxContentLength: Int,
    request: HttpRequestBuilder,
    coroutineScope: CoroutineScope,
    sanitizedHeaders: List<SanitizedHeader>,
): OutgoingContent? {
    val content = request.body as? OutgoingContent
    // Taken before the body is read, so the duration includes the upload.
    val requestTimestamp = Clock.System.now().toEpochMilliseconds()

    // Headers.
    val url = request.url.toString()
    val method = request.method.value
    val headers = InternalLibraryBridge.sanitizeHeaders(request.headers.entries().associate { it.key to it.value }, { name -> sanitizedHeaders.firstOrNull { it.predicate(name) }?.placeholder })
    if (content == null) {
        // Some requests still carry domain objects at this pipeline stage.
        InternalLibraryBridge.saveRequest(
            id = id,
            method = method,
            url = url,
            requestTimestamp = requestTimestamp,
            requestHeaders = headers,
            requestContentType = request.headers["Content-Type"],
            requestContentLength = 0L,
            requestBody = null,
            isRequestBodyTruncated = false,
        )
        return null
    }

    val contentLength = content.contentLength ?: 0
    val contentType = content.contentType?.toString()

    // Body.
    val channel = ByteChannel()
    coroutineScope.launch(Dispatchers.Default) {

        // Read content, keeping only what is stored.
        val bounded = channel.readBounded(maxContentLength)
        val body = bounded.bytes
        val size = if (contentLength != 0L) contentLength else bounded.totalSize

        // Save request.
        InternalLibraryBridge.saveRequest(
            id = id,
            method = method,
            url = url,
            requestTimestamp = requestTimestamp,
            requestHeaders = headers,
            requestContentType = contentType,
            requestContentLength = size,
            requestBody = body,
            isRequestBodyTruncated = size > maxContentLength,
        )
    }

    return content.observe(channel, coroutineScope)
}

