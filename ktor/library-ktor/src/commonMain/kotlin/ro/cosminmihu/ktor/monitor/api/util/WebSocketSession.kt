package ro.cosminmihu.ktor.monitor.api.util

import io.ktor.client.plugins.websocket.DefaultClientWebSocketSession
import io.ktor.websocket.DefaultWebSocketSession
import io.ktor.websocket.Frame
import io.ktor.websocket.readReason
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.ChannelIterator
import kotlinx.coroutines.channels.ChannelResult
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import ro.cosminmihu.ktor.monitor.InternalKtorMonitorApi
import ro.cosminmihu.ktor.monitor.StreamRecorder

/**
 * The WebSocket handshake response has no body; frames travel over the upgraded connection. Frames
 * sent and received through the session are recorded, in order, as stream events of the call, and
 * the connection status follows the lifetime of the session. Only frames the application actually
 * reads from [DefaultClientWebSocketSession.incoming] are recorded.
 */
@OptIn(InternalKtorMonitorApi::class, ExperimentalCoroutinesApi::class)
internal fun DefaultClientWebSocketSession.monitoredFrames(
    id: String,
    maxContentLength: Int,
): DefaultClientWebSocketSession {
    val recorder = StreamRecorder(isWebSocket = true)
    recorder.bind(id, maxContentLength)
    val delegate = this
    delegate.coroutineContext[Job]?.invokeOnCompletion { cause ->
        val reason = runCatching { delegate.closeReason.getCompleted() }.getOrNull()
        recorder.finish(
            closeCode = reason?.code?.toInt(),
            closeReason = reason?.message?.takeIf { it.isNotEmpty() },
            error = cause
                ?.takeIf { it !is CancellationException }
                ?.let { "${it::class.simpleName}: ${it.message.orEmpty()}" },
        )
    }
    return DefaultClientWebSocketSession(call, MonitoredWebSocketSession(delegate, recorder))
}

@OptIn(InternalKtorMonitorApi::class)
private fun StreamRecorder.recordFrame(sent: Boolean, frame: Frame) {
    when (frame) {
        is Frame.Text -> record(
            isSent = sent,
            type = StreamRecorder.TYPE_TEXT,
            payload = frame.data.copyOf(),
        )

        is Frame.Binary -> record(
            isSent = sent,
            type = StreamRecorder.TYPE_BINARY,
            payload = frame.data.copyOf(),
        )

        is Frame.Close -> {
            val reason = runCatching { frame.readReason() }.getOrNull()
            record(
                isSent = sent,
                type = StreamRecorder.TYPE_CLOSE,
                payload = reason?.message?.encodeToByteArray(),
                name = reason?.code?.toString(),
            )
        }

        is Frame.Ping -> record(isSent = sent, type = StreamRecorder.TYPE_PING)
        is Frame.Pong -> record(isSent = sent, type = StreamRecorder.TYPE_PONG)
    }
}

@OptIn(InternalKtorMonitorApi::class)
private class MonitoredWebSocketSession(
    private val delegate: DefaultWebSocketSession,
    recorder: StreamRecorder,
) : DefaultWebSocketSession by delegate {

    override val incoming: ReceiveChannel<Frame> =
        RecordingReceiveChannel(delegate.incoming, recorder)

    override val outgoing: SendChannel<Frame> =
        RecordingSendChannel(delegate.outgoing, recorder)

    // `send` is a member that `by delegate` would forward past the recording `outgoing`.
    override suspend fun send(frame: Frame) {
        outgoing.send(frame)
    }
}

@OptIn(InternalKtorMonitorApi::class)
private class RecordingReceiveChannel(
    private val delegate: ReceiveChannel<Frame>,
    private val recorder: StreamRecorder,
) : ReceiveChannel<Frame> by delegate {

    override suspend fun receive(): Frame = delegate.receive().also { recorder.recordFrame(false, it) }

    override suspend fun receiveCatching(): ChannelResult<Frame> =
        delegate.receiveCatching().also { result ->
            result.getOrNull()?.let { recorder.recordFrame(false, it) }
        }

    override fun tryReceive(): ChannelResult<Frame> =
        delegate.tryReceive().also { result ->
            result.getOrNull()?.let { recorder.recordFrame(false, it) }
        }

    override fun iterator(): ChannelIterator<Frame> {
        val iterator = delegate.iterator()
        return object : ChannelIterator<Frame> {
            override suspend fun hasNext(): Boolean = iterator.hasNext()

            override fun next(): Frame = iterator.next().also { recorder.recordFrame(false, it) }
        }
    }
}

@OptIn(InternalKtorMonitorApi::class)
private class RecordingSendChannel(
    private val delegate: SendChannel<Frame>,
    private val recorder: StreamRecorder,
) : SendChannel<Frame> by delegate {

    override suspend fun send(element: Frame) {
        delegate.send(element)
        recorder.recordFrame(true, element)
    }

    override fun trySend(element: Frame): ChannelResult<Unit> =
        delegate.trySend(element).also { if (it.isSuccess) recorder.recordFrame(true, element) }
}
