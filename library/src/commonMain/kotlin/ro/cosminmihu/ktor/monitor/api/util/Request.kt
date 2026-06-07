package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.client.request.HttpRequestBuilder
import io.ktor.http.content.OutgoingContent
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.toByteArray
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import ro.cosminmihu.ktor.monitor.ContentLength
import ro.cosminmihu.ktor.monitor.SanitizedHeader
import ro.cosminmihu.ktor.monitor.attr.KtorMonitorRequestBody
import ro.cosminmihu.ktor.monitor.attr.KtorMonitorRequestUrl
import ro.cosminmihu.ktor.monitor.db.LibraryDao

internal suspend fun logRequestException(
    dao: LibraryDao,
    id: String,
    cause: Throwable,
) {
    dao.saveRequest(
        id = id,
        error = cause,
    )
}

internal suspend fun logRequest(
    dao: LibraryDao,
    id: String,
    maxContentLength: Int,
    request: HttpRequestBuilder,
    coroutineScope: CoroutineScope,
    sanitizedHeaders: List<SanitizedHeader>,
): OutgoingContent? {
    val content = request.body as OutgoingContent
    val overrideBody = if (request.attributes.contains(KtorMonitorRequestBody)) {
        request.attributes[KtorMonitorRequestBody]
    } else {
        null
    }

    // Headers.
    val url = if (request.attributes.contains(KtorMonitorRequestUrl)) {
        request.attributes[KtorMonitorRequestUrl]
    } else {
        request.url.toString()
    }
    val method = request.method.value
    val headers = request.headers.sanitizedHeaders(sanitizedHeaders)
    val contentType = content.contentType?.toString()

    if (overrideBody != null) {
        val contentLength = overrideBody.size.toLong()
        coroutineScope.launch(Dispatchers.Default) {
            val body = when {
                maxContentLength != ContentLength.Full -> overrideBody
                    .take(maxContentLength)
                    .toByteArray()

                else -> overrideBody
            }

            // Save request.
            dao.saveRequest(
                id = id,
                method = method,
                url = url,
                requestTimestamp = Clock.System.now().toEpochMilliseconds(),
                requestHeaders = headers,
                requestContentType = contentType,
                requestContentLength = contentLength,
                requestBody = body,
                isRequestBodyTruncated = contentLength != 0L && contentLength > maxContentLength,
            )
        }
        return content
    }

    val contentLength = content.contentLength ?: 0

    // Body.
    val channel = ByteChannel()
    coroutineScope.launch(Dispatchers.Default) {

        // Read content.
        val requestBody = channel.toByteArray()
        val body = when {
            maxContentLength != ContentLength.Full -> requestBody
                .take(maxContentLength)
                .toByteArray()

            else -> requestBody
        }

        // Save request.
        dao.saveRequest(
            id = id,
            method = method,
            url = url,
            requestTimestamp = Clock.System.now().toEpochMilliseconds(),
            requestHeaders = headers,
            requestContentType = contentType,
            requestContentLength = contentLength,
            requestBody = body,
            isRequestBodyTruncated = contentLength != 0L && contentLength > maxContentLength,
        )
    }

    return content.observe(channel)
}
