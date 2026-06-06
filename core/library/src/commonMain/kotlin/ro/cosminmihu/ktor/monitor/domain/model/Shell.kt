package ro.cosminmihu.ktor.monitor.domain.model

import ro.cosminmihu.ktor.monitor.db.sqldelight.Call

/**
 * Builds a multi-line shell command string (curl / wget style) for the given [Call].
 *
 * The flag callbacks ([method], [header], [data]) receive an already-quoted/escaped
 * payload and must format it with their tool-specific switch (e.g. `-X "..."`,
 * `--method="..."`).
 *
 * Lines are joined with ` \\\n  ` so the result can be pasted into a shell verbatim.
 */
internal fun buildShellCommand(
    call: Call,
    tool: String,
    method: (String) -> String,
    header: (String) -> String,
    data: (String) -> String,
): String = buildList {
    add(tool)
    add(method(call.method))
    call.requestHeaders.forEach { (key, values) ->
        val value = values.joinToString(separator = "; ")
        add(header("$key: $value".shellEscape()))
    }
    call.requestBody?.takeIf { it.isNotEmpty() }?.decodeToString()?.let {
        add(data(it.shellEscape()))
    }
    add("\"${call.url.shellEscape()}\"")
}.joinToString(separator = " \\\n  ")

