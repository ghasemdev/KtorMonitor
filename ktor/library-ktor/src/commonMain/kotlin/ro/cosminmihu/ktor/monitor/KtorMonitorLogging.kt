package ro.cosminmihu.ktor.monitor

import io.ktor.client.plugins.api.ClientPlugin
import ro.cosminmihu.ktor.monitor.api.LoggingPlugin

/**
 * A [Ktor](https://ktor.io/) client plugin that provides the capability to log HTTP calls.
 *
 * You can learn more from [KtorMonitor](https://github.com/CosminMihuMDC/KtorMonitor).
 *
 * ```kotlin
 * HttpClient {
 *    install(KtorMonitorLogging) {
 *       sanitizeHeader { header -> header == "Authorization" }
 *       filter { request -> !request.url.host.contains("cosminmihu.ro") }
 *       isActive = true
 *       showNotification = true
 *       retentionPeriod = RetentionPeriod.OneHour
 *       maxContentLength = ContentLength.Default
 *    }
 * }
 * ```
 *
 * Server-Sent Events are recorded from the events parsed by the Ktor `SSE` plugin, so the stored
 * body is reconstructed. SSE comments and `retry` events are only recorded when the
 * session is opened with `showCommentEvents = true` and `showRetryEvents = true`.
 */
public val KtorMonitorLogging: ClientPlugin<KtorMonitorLoggingConfig> = LoggingPlugin