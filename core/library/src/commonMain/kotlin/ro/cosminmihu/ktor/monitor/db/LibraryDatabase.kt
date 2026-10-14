package ro.cosminmihu.ktor.monitor.db

import androidx.room3.ColumnTypeConverters
import androidx.room3.ConstructedBy
import androidx.room3.Database
import androidx.room3.RoomDatabase
import androidx.room3.RoomDatabaseConstructor

internal const val DATABASE_NAME = "ktor_monitor.db"

// Version 1 was the SQLDelight schema; moving to Room drops it (see fallbackToDestructiveMigration).
@Database(entities = [Call::class, StreamEvent::class], version = 3)
@ColumnTypeConverters(HeadersConverter::class)
@ConstructedBy(LibraryDatabaseConstructor::class)
internal abstract class LibraryDatabase : RoomDatabase() {
    abstract fun callDao(): CallDao
    abstract fun streamEventDao(): StreamEventDao
}

@Suppress("KotlinNoActualForExpect", "NO_ACTUAL_FOR_EXPECT")
internal expect object LibraryDatabaseConstructor : RoomDatabaseConstructor<LibraryDatabase> {
    override fun initialize(): LibraryDatabase
}

internal expect fun createDatabase(): LibraryDatabase
