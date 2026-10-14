package ro.cosminmihu.ktor.monitor.db

import androidx.room3.Dao
import androidx.room3.Insert
import androidx.room3.Query
import kotlinx.coroutines.flow.Flow

@Dao
internal interface StreamEventDao {

    @Insert
    suspend fun insert(event: StreamEvent)

    @Query("SELECT * FROM stream_event WHERE callId = :callId ORDER BY id ASC")
    fun selectEvents(callId: String): Flow<List<StreamEvent>>

    @Query("SELECT * FROM stream_event WHERE callId IN (:callIds) ORDER BY callId, id ASC")
    suspend fun selectEventsByCallIds(callIds: Collection<String>): List<StreamEvent>
}
