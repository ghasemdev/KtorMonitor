package ro.cosminmihu.ktor.monitor.ui.notification

import kotlinx.coroutines.suspendCancellableCoroutine
import platform.UserNotifications.UNAuthorizationOptionAlert
import platform.UserNotifications.UNAuthorizationOptionBadge
import platform.UserNotifications.UNAuthorizationOptionSound
import platform.UserNotifications.UNMutableNotificationContent
import platform.UserNotifications.UNNotificationInterruptionLevel
import platform.UserNotifications.UNNotificationRequest
import platform.UserNotifications.UNTimeIntervalNotificationTrigger
import platform.UserNotifications.UNUserNotificationCenter
import kotlin.coroutines.resume

internal actual class NotificationManager {

    private val notificationCenter = UNUserNotificationCenter.currentNotificationCenter().apply {
        setDelegate(NotificationDelegate(appNotificationDelegate = delegate()))
    }

    // A fixed identifier stops new notifications from showing after the previous one was tapped,
    // so every post gets a unique identifier.
    private var sequence = 0L
    private var lastIdentifier: String? = null

    actual suspend fun clear() {
        val identifiers = listOfNotNull(lastIdentifier, NOTIFICATION_ID.toString())
        notificationCenter.removePendingNotificationRequestsWithIdentifiers(identifiers)
        notificationCenter.removeDeliveredNotificationsWithIdentifiers(identifiers)
        lastIdentifier = null
    }

    actual suspend fun notify(messages: List<String>) {
        if (messages.isEmpty()) {
            clear()
            return
        }

        if (!notificationCenter.isNotificationPermissionGranted()) {
            return
        }

        val previous = lastIdentifier
        val identifier = "$NOTIFICATION_ID-${sequence++}"
        lastIdentifier = identifier
        val body = messages.joinToString("\n") { it }
        val content = UNMutableNotificationContent().apply {
            setTitle(NOTIFICATION_TITLE)
            setBody(body)
            setSound(null)
            setInterruptionLevel(UNNotificationInterruptionLevel.UNNotificationInterruptionLevelActive)
        }

        val trigger = UNTimeIntervalNotificationTrigger
            .triggerWithTimeInterval(0.1, repeats = false)
        val request = UNNotificationRequest
            .requestWithIdentifier(identifier, content, trigger)

        notificationCenter.addNotificationRequest(request) { error ->
            if (error != null) {
                println("Error showing notification: $error")
            } else if (previous != null) {
                // Remove the superseded notification only after the new one is scheduled.
                notificationCenter.removeDeliveredNotificationsWithIdentifiers(listOf(previous))
            }
        }
    }
}

internal suspend fun UNUserNotificationCenter.isNotificationPermissionGranted(): Boolean =
    suspendCancellableCoroutine { continuation ->
        requestAuthorizationWithOptions(
            UNAuthorizationOptionAlert or UNAuthorizationOptionSound or UNAuthorizationOptionBadge
        ) { granted, error ->
            continuation.resume(granted)
        }
    }