package ro.cosminmihu.ktor.monitor.domain.model

import ro.cosminmihu.ktor.monitor.db.Call

/**
 * Builds a multi-line shell command string (curl / wget style) for the given [Call].
 *
 * The flag callbacks ([method], [header], [data]) receive an already-quoted/escaped
 * payload and must format it with their tool-specific switch (e.g. `-X "..."`,
 * `--method="..."`).
 *
 * Binary request bodies are emitted as a reference to [BINARY_BODY_FILE].
 *
 * Lines are joined with ` \\\n  ` so the result can be pasted into a shell verbatim.
 */
private const val BINARY_BODY_FILE = "request-body.bin"

internal fun buildShellCommand(
    call: Call,
    tool: String,
    method: (String) -> String,
    header: (String) -> String,
    data: (String) -> String,
    binaryData: (String) -> String,
): String = buildList {
    add(tool)
    add(method(call.method.shellQuote()))
    call.requestHeaders.forEach { (key, values) ->
        val value = values.joinToString(separator = "; ")
        add(header("$key: $value".shellQuote()))
    }
    call.requestBody?.takeIf { it.isNotEmpty() }?.let { body ->
        val text = body.decodeToString()
        if (text.encodeToByteArray().contentEquals(body)) {
            add(data(text.shellQuote()))
        } else {
            // Binary payload cannot be represented as text; reference a file instead.
            add(binaryData(BINARY_BODY_FILE.shellQuote()))
        }
    }
    add(call.url.shellQuote())
}.joinToString(separator = " \\\n  ")

