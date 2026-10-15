package ro.cosminmihu.ktor.monitor.domain.model

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import com.fleeksoft.ksoup.Ksoup
import ro.cosminmihu.ktor.monitor.ui.detail.DetailUiState.ContentFormat
import ro.cosminmihu.ktor.monitor.ui.detail.contentFormat
import ro.cosminmihu.ktor.monitor.ui.detail.formater.beautifyJavaScript

private const val MAX_PRETTY_JSON_CHARS = 200_000

private val prettyJson = Json { prettyPrint = true; isLenient = true }

/** Returns this text pretty-printed when it is a JSON object/array, or `null` otherwise. */
internal fun String.prettyPrintJsonOrNull(): String? {
    val trimmed = trim()
    if (trimmed.length > MAX_PRETTY_JSON_CHARS) return null
    if (!trimmed.startsWith('{') && !trimmed.startsWith('[')) return null
    return try {
        prettyJson.encodeToString(JsonElement.serializer(), prettyJson.parseToJsonElement(trimmed))
    } catch (_: Exception) {
        null
    }
}

/**
 * Re-formats a decoded body for text exports based on its content type, reusing the same
 * formatters as the detail screen. Falls back to the original text when it cannot be formatted.
 */
internal fun String.prettyPrintBody(contentType: String?): String {
    val type = contentType?.contentType
    val formatted = when (type?.contentFormat) {
        ContentFormat.JSON -> prettyPrintJsonOrNull()
        ContentFormat.JAVASCRIPT -> runCatching { beautifyJavaScript(this) }.getOrNull()
        ContentFormat.XML ->
            if (type == ContentType.TEXT_HTML) null else prettyPrintXmlOrNull()
        else -> if (type == null || type == ContentType.UNKNOWN) prettyPrintJsonOrNull() else null
    }
    return formatted ?: this
}

private fun String.prettyPrintXmlOrNull(): String? = try {
    Ksoup.parseXml(this, "").also { it.outputSettings().prettyPrint(true).indentAmount(2) }
        .outerHtml().takeIf { it.isNotBlank() }
} catch (_: Exception) {
    null
}
