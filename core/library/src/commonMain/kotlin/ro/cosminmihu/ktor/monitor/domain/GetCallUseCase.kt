package ro.cosminmihu.ktor.monitor.domain

import kotlinx.coroutines.flow.distinctUntilChanged
import ro.cosminmihu.ktor.monitor.db.LibraryDao

internal class GetCallUseCase(
    private val dao: LibraryDao,
) {

    operator fun invoke(id: String) = dao.getCall(id)
        .distinctUntilChanged()
}
