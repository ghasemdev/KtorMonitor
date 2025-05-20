package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.readRawBytes
import io.ktor.http.contentType
import ro.cosminmihu.ktor.monitor.ContentLength
import ro.cosminmihu.ktor.monitor.SanitizedHeader
import ro.cosminmihu.ktor.monitor.db.LibraryDao

internal suspend fun logResponseException(
    dao: LibraryDao,
    id: String,
    cause: Throwable,
) {
    dao.saveResponse(
        id = id,
        error = cause,
    )
}

internal suspend fun logResponse(
    dao: LibraryDao,
    id: String,
    response: HttpResponse,
    sanitizedHeaders: List<SanitizedHeader>,
) {
    val headers = response.headers.sanitizedHeaders(sanitizedHeaders)

    // Save response.
    dao.saveResponse(
        id = id,
        protocol = response.version.toString(),
        requestTimestamp = response.requestTime.timestamp,
        responseCode = response.status.value,
        responseTimestamp = response.responseTime.timestamp,
        responseContentType = response.contentType()?.toString(),
        responseHeaders = headers,
    )
}

internal suspend fun logResponseBody(
    dao: LibraryDao,
    id: String,
    maxContentLength: Int,
    response: HttpResponse,
) {
    // Read content.
    val responseBody = response.readRawBytes()

    val body = when {
        maxContentLength != ContentLength.Full -> responseBody
            .take(maxContentLength)
            .toByteArray()

        else -> responseBody
    }

    // Save response body.
    dao.saveResponseBody(
        id = id,
        responseContentLength = responseBody.size.toLong(),
        responseBody = body,
        isResponseBodyTruncated = responseBody.size > maxContentLength,
    )
}