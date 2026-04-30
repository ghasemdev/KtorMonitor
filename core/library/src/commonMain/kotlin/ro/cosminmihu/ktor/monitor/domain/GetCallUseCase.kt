package ro.cosminmihu.ktor.monitor.domain

import app.cash.sqldelight.async.coroutines.awaitAsOneOrNull
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import ro.cosminmihu.ktor.monitor.db.LibraryDao

internal class GetCallUseCase(
    private val dao: LibraryDao,
) {

    operator fun invoke(id: String) = dao.getCall(id)
        .map { it.awaitAsOneOrNull() }
        .distinctUntilChanged()
}