package ro.cosminmihu.ktor.monitor.ui.notification

internal actual class NotificationManager {

    actual suspend fun clear() {
        // Web doesn't support notifications.
    }

    actual suspend fun notify(messages: List<String>) {
        // Web doesn't support notifications.
    }
}