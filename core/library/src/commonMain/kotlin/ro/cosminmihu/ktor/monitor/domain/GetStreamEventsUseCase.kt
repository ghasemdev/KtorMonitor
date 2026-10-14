package ro.cosminmihu.ktor.monitor.domain

import kotlinx.coroutines.flow.Flow
import ro.cosminmihu.ktor.monitor.db.LibraryDao
import ro.cosminmihu.ktor.monitor.db.StreamEvent

internal class GetStreamEventsUseCase(
    private val dao: LibraryDao,
) {

    operator fun invoke(callId: String): Flow<List<StreamEvent>> = dao.getStreamEvents(callId)
}
