package ro.cosminmihu.ktor.monitor.db

import androidx.room3.Room
import androidx.sqlite.driver.web.WebWorkerSQLiteDriver
import org.w3c.dom.Worker

internal actual fun createDatabase(): LibraryDatabase {
    return Room.databaseBuilder<LibraryDatabase>(name = DATABASE_NAME)
        .setDriver(WebWorkerSQLiteDriver(jsWorker()))
        .fallbackToDestructiveMigration(dropAllTables = true)
        .build()
}

private fun jsWorker(): Worker =
    js("""new Worker(new URL("ktor-monitor-sqlite-worker/sqlite.worker.js", import.meta.url))""")
