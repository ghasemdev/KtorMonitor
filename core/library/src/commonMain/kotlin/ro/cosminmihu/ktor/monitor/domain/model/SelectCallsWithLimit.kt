package ro.cosminmihu.ktor.monitor.domain.model

import ro.cosminmihu.ktor.monitor.db.SelectCalls
import ro.cosminmihu.ktor.monitor.db.SelectCallsWithLimit
import ro.cosminmihu.ktor.monitor.db.StreamKind
import kotlin.time.Duration.Companion.milliseconds

internal val SelectCallsWithLimit.isInProgress
    get() = responseCode == null && error == null

internal val SelectCallsWithLimit.isError
    get() = !error.isNullOrBlank()

internal val SelectCallsWithLimit.encodedPathAndQuery
    get() = ParsedUrl.parse(url).encodedPathAndQuery


internal val SelectCalls.durationAsText
    get() = responseTimestamp?.minus(requestTimestamp)?.milliseconds?.toComponents { hours, minutes, seconds, nanoseconds ->
        val milliseconds = nanoseconds / 1_000_000
        when {
            hours > 0 -> "$hours:$minutes:$seconds $milliseconds ms"
            minutes > 0 -> "$minutes:$seconds $milliseconds ms"
            seconds > 0 -> "$seconds $milliseconds ms"
            milliseconds > 0 -> "$milliseconds ms"
            else -> null
        }
    }

internal val SelectCalls.requestTimeAsText
    get() = requestTimestamp.formatTime()

internal val SelectCalls.isSecure
    get() = ParsedUrl.parse(url).isSecure

internal val SelectCalls.isWebsocket
    get() = streamKind == StreamKind.WEB_SOCKET || ParsedUrl.parse(url).isWebsocket

internal val SelectCalls.host
    get() = ParsedUrl.parse(url).host

internal val SelectCalls.encodedPathAndQuery
    get() = ParsedUrl.parse(url).encodedPathAndQuery
