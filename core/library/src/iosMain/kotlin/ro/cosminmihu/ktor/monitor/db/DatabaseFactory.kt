package ro.cosminmihu.ktor.monitor.db

import androidx.room3.Room
import androidx.sqlite.driver.bundled.BundledSQLiteDriver
import kotlinx.cinterop.ExperimentalForeignApi
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSUserDomainMask

internal actual fun createDatabase(): LibraryDatabase {
    return Room.databaseBuilder<LibraryDatabase>(name = documentDirectory() + "/" + DATABASE_NAME)
        .setDriver(BundledSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}

@OptIn(ExperimentalForeignApi::class)
private fun documentDirectory(): String {
    val url = NSFileManager.defaultManager.URLForDirectory(
        directory = NSDocumentDirectory,
        inDomain = NSUserDomainMask,
        appropriateForURL = null,
        create = false,
        error = null,
    )
    return requireNotNull(url?.path)
}
