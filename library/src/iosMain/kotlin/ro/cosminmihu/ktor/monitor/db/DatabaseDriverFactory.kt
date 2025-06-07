package ro.cosminmihu.ktor.monitor.db

import app.cash.sqldelight.async.coroutines.synchronous
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.native.NativeSqliteDriver
import co.touchlab.sqliter.DatabaseConfiguration
import platform.Foundation.NSFileManager
import ro.cosminmihu.ktor.monitor.db.sqldelight.LibraryDatabase
import ro.cosminmihu.ktor.monitor.di.LibraryKoinContext
import ro.cosminmihu.ktor.monitor.domain.ConfigUseCase

internal actual fun createDatabaseDriver(): SqlDriver {
    val appGroupID = LibraryKoinContext.koin
        .get<ConfigUseCase>()
        .getIosGroupId()

    val onConfiguration: (DatabaseConfiguration) -> DatabaseConfiguration =
        if (appGroupID != null) {
            try {
                val path = getSharedDatabasePath(appGroupID)
                val callback: (DatabaseConfiguration) -> DatabaseConfiguration = { config ->
                    config.copy(
                        extendedConfig = config.extendedConfig.copy(
                            basePath = path
                        )
                    )
                }
                callback
            } catch (_: Exception) {
                { it }
            }
        } else {
            { it }
        }

    return NativeSqliteDriver(
        schema = LibraryDatabase.Schema.synchronous(),
        name = DATABASE_NAME,
        onConfiguration = onConfiguration,
    )
}

private fun getSharedDatabasePath(groupID: String): String {
    val fileManager = NSFileManager.defaultManager
    val containerURL = fileManager.containerURLForSecurityApplicationGroupIdentifier(groupID)
        ?: error("App Group \"$groupID\" not found.")
    return containerURL.path ?: error("Cannot resolve database path.")
}
