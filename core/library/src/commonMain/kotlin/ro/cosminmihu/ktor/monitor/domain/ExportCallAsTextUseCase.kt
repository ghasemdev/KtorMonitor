package ro.cosminmihu.ktor.monitor.domain

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.cosminmihu.ktor.monitor.db.Call
import ro.cosminmihu.ktor.monitor.db.StreamEvent
import ro.cosminmihu.ktor.monitor.domain.model.decodeBody
import ro.cosminmihu.ktor.monitor.domain.model.prettyPrintBody

internal class ExportCallAsTextUseCase {

    suspend operator fun invoke(call: Call, events: List<StreamEvent> = emptyList()): String = withContext(Dispatchers.Default) {
        val protocol = call.protocol ?: "HTTP/1.1"
        val requestBody = call.requestBody?.decodeBody(call.requestHeaders)?.prettyPrintBody(call.requestContentType)
        val responseBody = call.responseBody?.decodeBody(call.responseHeaders)?.prettyPrintBody(call.responseContentType)

        buildString {
            // Request line + headers
            appendLine("${call.method} ${call.url} $protocol")
            call.requestHeaders.appendHeaderBlock(this)
            appendLine()

            // Request body
            requestBody
                ?.takeIf { it.isNotBlank() }
                ?.let { appendLine(it + call.isRequestBodyTruncated.truncatedLabel()) }
            appendLine()

            // Response line + headers
            appendLine("$protocol ${call.responseCode?.toString() ?: "-"}")
            call.responseHeaders.orEmpty().appendHeaderBlock(this)
            appendLine()

            // Response body
            responseBody
                ?.takeIf { it.isNotBlank() }
                ?.let { appendLine(it + call.isResponseBodyTruncated.truncatedLabel()) }
            appendLine()

            if (events.isNotEmpty()) {
                appendLine(events.asTranscript())
            }
        }
    }
}

private fun Map<String, List<String>>.appendHeaderBlock(out: StringBuilder) {
    if (isEmpty()) return
    entries.joinTo(out, separator = "\n", postfix = "\n") { (key, values) ->
        "$key: ${values.joinToString(separator = "; ")}"
    }
}

private fun Boolean?.truncatedLabel() = if (this == true) " (truncated)" else ""

internal fun List<StreamEvent>.asTranscript(): String = joinToString("\n") { event ->
    val arrow = if (event.isSent) "→" else "←"
    val label = listOfNotNull(event.type.uppercase(), event.name).joinToString(" ")
    val body = if (event.type == "BINARY") "<${event.size} bytes>" else event.payload?.decodeToString().orEmpty()
    "$arrow $label: $body".trimEnd() + if (event.isTruncated) " (truncated)" else ""
}
