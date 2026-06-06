package ro.cosminmihu.ktor.monitor

/**
 * Configuration block for setting up shared database access on iOS via App Group.
 *
 * This is used to specify the App Group ID for accessing a shared Core Data or SQLite
 * database between the main iOS app and its extensions.
 *
 * Example usage:
 * ```
 * sharedDB {
 *     groupID = "group.com.example.shared"
 * }
 * ```
 *
 * This configuration is only effective on iOS targets.
 */
public class SharedDBConfig {
    /**
     * The App Group identifier used to locate the shared container on iOS.
     * Must match the group ID configured in the Apple Developer portal.
     */
    public var groupID: String? = null
}

/**
 * Configures the iOS shared database App Group ID for the logging system.
 *
 * This function should only be used from iOS-specific source sets.
 * It will be ignored on other platforms.
 *
 * @param block A configuration block where the shared App Group ID can be specified.
 */
public fun sharedDB(block: SharedDBConfig.() -> Unit): SharedDBConfig =
    SharedDBConfig().apply(block)

/**
 * Configures the iOS shared database App Group ID for the logging system.
 *
 * This function should only be used from iOS-specific source sets.
 * It will be ignored on other platforms.
 *
 * @param block A configuration block where the shared App Group ID can be specified.
 */
public fun KtorMonitorLoggingConfig.sharedDB(block: SharedDBConfig.() -> Unit) {
    val config = SharedDBConfig().apply(block)
    config.groupID?.let { iosGroupId = it }
}
