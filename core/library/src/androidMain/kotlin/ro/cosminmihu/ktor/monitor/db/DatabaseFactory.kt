package ro.cosminmihu.ktor.monitor.db

import android.content.Context
import androidx.room3.Room
import androidx.room3.RoomDatabaseConstructor
import androidx.sqlite.driver.AndroidSQLiteDriver
import ro.cosminmihu.ktor.monitor.di.LibraryKoinContext

internal actual fun createDatabase(): LibraryDatabase {
    val context = LibraryKoinContext.koin.get<Context>()
    return Room.databaseBuilder<LibraryDatabase>(
        context = context,
        name = context.getDatabasePath(DATABASE_NAME).absolutePath,
    )
        .setDriver(AndroidSQLiteDriver())
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}
