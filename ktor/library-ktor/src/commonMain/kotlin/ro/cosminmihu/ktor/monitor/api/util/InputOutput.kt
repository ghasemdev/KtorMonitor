package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpStatusCode
import io.ktor.http.content.OutgoingContent
import io.ktor.util.AttributeKey
import io.ktor.util.copyToBoth
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.writeFully
import io.ktor.utils.io.writer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers

internal suspend fun OutgoingContent.observe(log: ByteWriteChannel, scope: CoroutineScope): OutgoingContent = when (this) {
    is OutgoingContent.ByteArrayContent -> {
        log.writeFully(bytes())
        log.flushAndClose()
        this
    }

    is OutgoingContent.ReadChannelContent -> {
        val responseChannel = ByteChannel()
        val content = readFrom()

        content.copyToBoth(log, responseChannel)
        LoggedContent(this, responseChannel)
    }

    is OutgoingContent.WriteChannelContent -> {
        val responseChannel = ByteChannel()
        val content = toReadChannel(scope)
        content.copyToBoth(log, responseChannel)
        LoggedContent(this, responseChannel)
    }

    is OutgoingContent.ContentWrapper -> copy(delegate().observe(log, scope))
    is OutgoingContent.NoContent, is OutgoingContent.ProtocolUpgrade -> {
        log.flushAndClose()
        this
    }
}

private fun OutgoingContent.WriteChannelContent.toReadChannel(scope: CoroutineScope): ByteReadChannel =
    scope.writer(Dispatchers.Default) {
        writeTo(channel)
    }.channel

internal class LoggedContent(
    private val originalContent: OutgoingContent,
    private val channel: ByteReadChannel,
) : OutgoingContent.ReadChannelContent() {

    override val contentType: ContentType? = originalContent.contentType
    override val contentLength: Long? = originalContent.contentLength
    override val status: HttpStatusCode? = originalContent.status
    override val headers: Headers = originalContent.headers

    override fun <T : Any> getProperty(key: AttributeKey<T>): T? = originalContent.getProperty(key)

    override fun <T : Any> setProperty(key: AttributeKey<T>, value: T?) =
        originalContent.setProperty(key, value)

    override fun readFrom(): ByteReadChannel = channel
}

