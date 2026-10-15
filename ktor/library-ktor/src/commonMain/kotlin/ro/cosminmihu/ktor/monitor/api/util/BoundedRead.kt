package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlin.math.min

private const val READ_BUFFER_SIZE = 8 * 1024

/** The first bytes of a channel, kept up to a limit, and the total number of bytes it carried. */
internal class BoundedBytes(val bytes: ByteArray, val totalSize: Long)

/**
 * Drains the channel, keeping at most [max] bytes in memory. The rest is counted and discarded,
 * so large payloads do not exhaust memory while the producer is still unblocked.
 */
internal suspend fun ByteReadChannel.readBounded(max: Int): BoundedBytes {
    val buffer = ByteArray(READ_BUFFER_SIZE)
    var kept = ByteArray(min(max, READ_BUFFER_SIZE))
    var keptSize = 0
    var total = 0L
    while (true) {
        val read = readAvailable(buffer, 0, buffer.size)
        if (read == -1) break
        total += read
        val toKeep = min(read, max - keptSize)
        if (toKeep > 0) {
            if (keptSize + toKeep > kept.size) {
                kept = kept.copyOf(min(max.toLong(), maxOf(kept.size * 2L, (keptSize + toKeep).toLong())).toInt())
            }
            buffer.copyInto(kept, keptSize, 0, toKeep)
            keptSize += toKeep
        }
    }
    return BoundedBytes(if (keptSize == kept.size) kept else kept.copyOf(keptSize), total)
}
