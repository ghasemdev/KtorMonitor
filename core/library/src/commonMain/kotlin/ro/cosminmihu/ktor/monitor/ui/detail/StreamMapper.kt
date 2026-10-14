package ro.cosminmihu.ktor.monitor.ui.detail

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import ro.cosminmihu.ktor.monitor.db.Call
import ro.cosminmihu.ktor.monitor.db.StreamEvent
import ro.cosminmihu.ktor.monitor.db.StreamEventType
import ro.cosminmihu.ktor.monitor.db.StreamKind
import ro.cosminmihu.ktor.monitor.db.StreamStatus
import ro.cosminmihu.ktor.monitor.domain.model.formatTimeMillis
import ro.cosminmihu.ktor.monitor.domain.model.sizeAsText

private const val HEX_PREVIEW_BYTES = 64
private const val MAX_PRETTY_JSON_CHARS = 200_000

private val prettyJson = Json { prettyPrint = true }

internal fun Call.toStream(events: List<StreamEvent>): DetailUiState.Stream? {
    val kind = when (streamKind) {
        StreamKind.WEB_SOCKET -> DetailUiState.StreamKind.WebSocket
        StreamKind.SSE -> DetailUiState.StreamKind.Sse
        else -> return null
    }
    return DetailUiState.Stream(
        kind = kind,
        status = when (streamStatus) {
            StreamStatus.CLOSED -> DetailUiState.StreamStatus.Closed
            StreamStatus.FAILED -> DetailUiState.StreamStatus.Failed
            else -> DetailUiState.StreamStatus.Open
        },
        closeCode = streamCloseCode?.toString().orEmpty(),
        closeReason = streamCloseReason.orEmpty(),
        error = streamError.orEmpty(),
        messages = events.map { it.toMessage() },
    )
}

internal fun StreamEvent.toMessage(): DetailUiState.StreamMessage {
    val type = when (type) {
        StreamEventType.BINARY -> DetailUiState.StreamMessageType.Binary
        StreamEventType.PING -> DetailUiState.StreamMessageType.Ping
        StreamEventType.PONG -> DetailUiState.StreamMessageType.Pong
        StreamEventType.CLOSE -> DetailUiState.StreamMessageType.Close
        StreamEventType.SSE -> DetailUiState.StreamMessageType.Sse
        else -> DetailUiState.StreamMessageType.Text
    }
    val isBinary = type == DetailUiState.StreamMessageType.Binary
    val text = if (isBinary) null else payload?.decodeToString()
    return DetailUiState.StreamMessage(
        id = id,
        isSent = isSent,
        type = type,
        name = name,
        eventId = eventId,
        text = text,
        prettyJson = text?.let(::prettyPrintJson),
        hexPreview = if (isBinary) payload?.hexPreview() else null,
        size = size.sizeAsText(),
        time = timestamp.formatTimeMillis(),
        isTruncated = isTruncated,
    )
}

private fun prettyPrintJson(text: String): String? {
    val trimmed = text.trim()
    if (trimmed.length > MAX_PRETTY_JSON_CHARS) return null
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), Json.parseToJsonElement(trimmed))
    } catch (_: Exception) {
        null
    }
}

private fun ByteArray.hexPreview(): String {
    val head = take(HEX_PREVIEW_BYTES).joinToString(" ") { byte ->
        (byte.toInt() and 0xFF).toString(16).padStart(2, '0')
    }
    return if (size > HEX_PREVIEW_BYTES) "$head …" else head
}

/** Plain-text transcript of the stream, used by copy and export. */
internal fun DetailUiState.Stream.asText(): String = messages.joinToString(separator = "\n") { message ->
    val arrow = if (message.isSent) "→" else "←"
    val body = when {
        message.text != null -> message.text
        message.hexPreview != null -> "BINARY ${message.size}: ${message.hexPreview}"
        else -> ""
    }
    val label = listOfNotNull(message.type.name.uppercase(), message.name).joinToString(" ")
    "[${message.time}] $arrow $label: $body".trimEnd()
}
