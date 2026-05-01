package ro.cosminmihu.ktor.monitor.ui.list

import ro.cosminmihu.ktor.monitor.domain.model.ClientSource
import ro.cosminmihu.ktor.monitor.domain.model.ContentType

internal data class ListUiState(
    val calls: List<Call>? = null,
    val filter: Filter = Filter.NoFilter,
    val showNotification: Boolean = false,
    val clientSource: ClientSource? = null,
    val availableMethods: Set<String> = emptySet(),
) {
    data class Call(
        val id: String,
        val isSecure: Boolean,
        val request: Request,
        val response: Response,
    )

    data class Request(
        val method: String,
        val host: String,
        val pathAndQuery: String,
        val requestTime: String,
    )

    data class Response(
        val responseCode: String,
        val contentType: ContentType,
        val duration: String,
        val size: String,
        val error: String,
    )

    data class Filter(
        val searchQuery: String,
        val onlyError: Boolean,
        val methods: Set<String>,
        val responseCodeRanges: Set<ResponseCodeRange>,
        val sizeSort: SizeSort?,
    ) {
        enum class ResponseCodeRange(val label: String, val range: IntRange) {
            R2XX("2xx", 200..299),
            R3XX("3xx", 300..399),
            R4XX("4xx", 400..499),
            R5XX("5xx", 500..599),
        }

        enum class SizeSort { ASC, DESC }

        companion object {
            internal val NoFilter = Filter(
                searchQuery = "",
                onlyError = false,
                methods = emptySet(),
                responseCodeRanges = emptySet(),
                sizeSort = null
            )
        }
    }
}

internal val ListUiState.isLoading
    get() = calls == null

internal val ListUiState.isEmpty
    get() = calls.isNullOrEmpty() && filter == ListUiState.Filter.NoFilter

internal val ListUiState.Call.isLoading
    get() = response.responseCode.isBlank() && response.error.isBlank()


internal val ListUiState.Call.isRedirect
    get() = when {
        response.responseCode.isNotBlank() -> response.responseCode.toIntOrNull() in 300 until 400
        else -> false
    }

internal val ListUiState.Call.isError
    get() = when {
        response.responseCode.isBlank() && response.error.isNotBlank() -> true
        isRedirect -> false
        response.responseCode.isNotBlank() -> response.responseCode.toIntOrNull() !in 100 until 300
        else -> false
    }
