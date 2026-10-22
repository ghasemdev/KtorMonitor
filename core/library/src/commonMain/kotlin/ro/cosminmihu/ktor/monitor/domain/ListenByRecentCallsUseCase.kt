package ro.cosminmihu.ktor.monitor.domain

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.retryWhen
import kotlinx.coroutines.launch
import ro.cosminmihu.ktor.monitor.db.LibraryDao
import ro.cosminmihu.ktor.monitor.domain.model.encodedPathAndQuery
import ro.cosminmihu.ktor.monitor.domain.model.isError
import ro.cosminmihu.ktor.monitor.domain.model.isInProgress
import ro.cosminmihu.ktor.monitor.ui.notification.NotificationManager
import kotlin.time.Duration.Companion.milliseconds

private const val LAST_CALLS = 5L
private val RETRY_DELAY_MS = 5.milliseconds

internal class ListenByRecentCallsUseCase(
    private val dao: LibraryDao,
    private val coroutineScope: CoroutineScope,
    private val notificationManager: NotificationManager,
    private val setupUseCase: ConfigUseCase,
    private val retentionUseCase: RetentionUseCase,
) {

    operator fun invoke() {
        coroutineScope.launch {
            dao.getCalls(LAST_CALLS)
                .map {
                    it.map {
                        buildString {
                            append(
                                when {
                                    it.isInProgress -> "⏳"
                                    it.isError -> "❌"
                                    else -> it.responseCode
                                }
                            )
                            append(" ")
                            append(it.method)
                            append(" ")
                            append(it.encodedPathAndQuery)
                        }
                    }
                }
                .distinctUntilChanged()
                .retryWhen { cause, _ ->
                    // Never let a transient DB failure (e.g. a native SQLite open/prepare
                    // error) escape this flow and crash the host app, nor stop it for good.
                    if (cause is CancellationException) return@retryWhen false
                    delay(RETRY_DELAY_MS)
                    true
                }
                .collectLatest {
                    try {
                        // Show notification (or actively clear it when the user disabled it).
                        if (setupUseCase.isShowNotification()) {
                            notificationManager.notify(it)
                        } else {
                            notificationManager.clear()
                        }

                        // Delete old calls.
                        retentionUseCase()
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        println("KtorMonitor: failed to update notification/retention: $e")
                    }
                }
        }
    }
}