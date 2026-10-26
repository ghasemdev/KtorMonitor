package ro.cosminmihu.ktor.monitor.domain

import ro.cosminmihu.ktor.monitor.db.LibraryDao

internal class GetCallsUseCase(
    private val dao: LibraryDao,
) {

    operator fun invoke() = dao.getCalls()
}
