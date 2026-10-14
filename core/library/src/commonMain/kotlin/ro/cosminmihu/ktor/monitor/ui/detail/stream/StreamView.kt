package ro.cosminmihu.ktor.monitor.ui.detail.stream

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SmallFloatingActionButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextOverflow
import kotlinx.coroutines.launch
import org.jetbrains.compose.resources.stringResource
import org.koin.compose.koinInject
import ro.cosminmihu.ktor.monitor.core.ClipboardManager
import ro.cosminmihu.ktor.monitor.ui.Dimens
import ro.cosminmihu.ktor.monitor.ui.detail.DetailUiState
import ro.cosminmihu.ktor.monitor.ui.detail.DetailUiState.StreamMessageType
import ro.cosminmihu.ktor.monitor.ui.icons.Icons
import ro.cosminmihu.ktor.monitor.ui.icons.filled.ContentCopy
import ro.cosminmihu.ktor.monitor.ui.icons.filled.KeyboardArrowDown
import ro.cosminmihu.ktor.monitor.ui.resources.Res
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_copy
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_all
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_control
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_copy_message
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_count
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_empty
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_format_json
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_received
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_scroll_to_end
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_sent
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_status_closed
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_status_closed_with
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_status_failed
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_status_open
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_stream_truncated

// The library theme is monochrome, so message kinds use explicit tints.
private val SentColor = Color(0xFF2DD4BF).copy(alpha = 0.22f)
private val ReceivedColor = Color(0xFF60A5FA).copy(alpha = 0.22f)
private val ControlColor = Color(0xFFF59E0B).copy(alpha = 0.22f)

private enum class Direction { ALL, SENT, RECEIVED }

@Composable
internal fun StreamView(stream: DetailUiState.Stream, modifier: Modifier = Modifier) {
    val clipboard = koinInject<ClipboardManager>()
    val scope = rememberCoroutineScope()
    var direction by remember { mutableStateOf(Direction.ALL) }
    var showControl by remember { mutableStateOf(true) }
    var formatJson by remember { mutableStateOf(true) }
    val listState = rememberLazyListState()

    val isWs = stream.kind == DetailUiState.StreamKind.WebSocket
    val visible = remember(stream.messages, direction, showControl) {
        stream.messages.filter { m ->
            val isControl = m.type == StreamMessageType.Ping ||
                m.type == StreamMessageType.Pong || m.type == StreamMessageType.Close
            (showControl || !isControl) && when (direction) {
                Direction.ALL -> true
                Direction.SENT -> m.isSent
                Direction.RECEIVED -> !m.isSent
            }
        }
    }

    val atEnd by remember {
        derivedStateOf {
            val info = listState.layoutInfo
            info.totalItemsCount == 0 ||
                (info.visibleItemsInfo.lastOrNull()?.index ?: 0) >= info.totalItemsCount - 1
        }
    }
    LaunchedEffect(visible.size) {
        if (visible.isNotEmpty() && atEnd) listState.scrollToItem(visible.lastIndex)
    }

    Column(modifier = modifier.fillMaxSize()) {
        StreamHeader(stream)

        Row(
            horizontalArrangement = Arrangement.spacedBy(Dimens.ExtraSmall, Alignment.End),
            modifier = Modifier.fillMaxWidth(),
        ) {
            if (isWs) {
                FilterChip(
                    selected = direction == Direction.ALL,
                    onClick = { direction = Direction.ALL },
                    label = { Text(stringResource(Res.string.ktor_stream_all)) },
                )
                FilterChip(
                    selected = direction == Direction.SENT,
                    onClick = { direction = Direction.SENT },
                    label = { Text("↑ " + stringResource(Res.string.ktor_stream_sent)) },
                )
                FilterChip(
                    selected = direction == Direction.RECEIVED,
                    onClick = { direction = Direction.RECEIVED },
                    label = { Text("↓ " + stringResource(Res.string.ktor_stream_received)) },
                )
                FilterChip(
                    selected = showControl,
                    onClick = { showControl = !showControl },
                    label = { Text(stringResource(Res.string.ktor_stream_control)) },
                )
            }
            FilterChip(
                selected = formatJson,
                onClick = { formatJson = !formatJson },
                label = { Text(stringResource(Res.string.ktor_stream_format_json)) },
            )
        }

        Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
            if (visible.isEmpty()) {
                Text(
                    text = stringResource(Res.string.ktor_stream_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.align(Alignment.Center),
                )
            } else {
                LazyColumn(
                    state = listState,
                    verticalArrangement = Arrangement.spacedBy(Dimens.Small),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(visible, key = { it.id }, contentType = { it.type }) { message ->
                        StreamMessageItem(
                            message = message,
                            isWs = isWs,
                            formatJson = formatJson,
                            onCopy = { scope.launch { clipboard.setText(message.copyText()) } },
                        )
                    }
                }
                if (!atEnd) {
                    SmallFloatingActionButton(
                        onClick = { scope.launch { listState.animateScrollToItem(visible.lastIndex) } },
                        modifier = Modifier.align(Alignment.BottomEnd).padding(Dimens.Small),
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(Res.string.ktor_stream_scroll_to_end),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StreamHeader(stream: DetailUiState.Stream) {
    val (icon, label, color) = when (stream.status) {
        DetailUiState.StreamStatus.Open ->
            Triple("●", stringResource(Res.string.ktor_stream_status_open), MaterialTheme.colorScheme.primary)
        DetailUiState.StreamStatus.Closed -> {
            val code = stream.closeCode
            Triple(
                "○",
                if (code.isBlank()) stringResource(Res.string.ktor_stream_status_closed)
                else stringResource(Res.string.ktor_stream_status_closed_with, closeCodeLabel(code)),
                MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        DetailUiState.StreamStatus.Failed ->
            Triple("✕", stringResource(Res.string.ktor_stream_status_failed), MaterialTheme.colorScheme.error)
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Dimens.Small),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(
            text = "$icon $label",
            style = MaterialTheme.typography.labelLarge,
            color = color,
        )
        Text(
            text = stringResource(Res.string.ktor_stream_count, stream.messages.size),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.weight(1f),
        )
    }
    val details = listOf(stream.closeReason, stream.error).filter { it.isNotBlank() }.joinToString(" · ")
    if (details.isNotBlank()) {
        Text(
            text = details,
            style = MaterialTheme.typography.bodySmall,
            color = if (stream.status == DetailUiState.StreamStatus.Failed) {
                MaterialTheme.colorScheme.error
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun StreamMessageItem(
    message: DetailUiState.StreamMessage,
    isWs: Boolean,
    formatJson: Boolean,
    onCopy: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isControl = message.type == StreamMessageType.Ping ||
        message.type == StreamMessageType.Pong || message.type == StreamMessageType.Close
    val alignment = when {
        !isWs || isControl -> Alignment.CenterHorizontally
        message.isSent -> Alignment.End
        else -> Alignment.Start
    }
    val container = when {
        isControl -> ControlColor
        message.isSent -> SentColor
        else -> ReceivedColor
    }
    val arrow = if (message.isSent) "↑" else "↓"

    Column(modifier = modifier.fillMaxWidth(), horizontalAlignment = alignment) {
        Card(
            colors = CardDefaults.cardColors(containerColor = container),
            modifier = Modifier.fillMaxWidth(if (isWs && !isControl) 0.85f else 1f),
        ) {
            Column(modifier = Modifier.padding(Dimens.Small)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val title = when (message.type) {
                        StreamMessageType.Sse -> message.name ?: "message"
                        StreamMessageType.Close -> "CLOSE ${message.name.orEmpty()}".trim()
                        StreamMessageType.Ping -> "PING"
                        StreamMessageType.Pong -> "PONG"
                        StreamMessageType.Binary -> "BINARY"
                        StreamMessageType.Text -> "TEXT"
                    }
                    Text(
                        text = if (isWs) "$arrow $title" else title,
                        style = MaterialTheme.typography.labelMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(onClick = onCopy) {
                        Icon(
                            imageVector = Icons.Default.ContentCopy,
                            contentDescription = stringResource(Res.string.ktor_stream_copy_message),
                        )
                    }
                }
                val body = when {
                    message.hexPreview != null -> message.hexPreview
                    formatJson && message.prettyJson != null -> message.prettyJson
                    else -> message.text
                }
                if (!body.isNullOrEmpty()) {
                    SelectionContainer {
                        Text(
                            text = body,
                            style = MaterialTheme.typography.bodySmall,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                val meta = buildList {
                    add(message.time)
                    add(message.size)
                    message.eventId?.takeIf { it.isNotBlank() }?.let { add("id: $it") }
                    if (message.isTruncated) add(stringResource(Res.string.ktor_stream_truncated))
                }.joinToString(" · ")
                Text(
                    text = meta,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

private fun DetailUiState.StreamMessage.copyText(): String =
    text ?: hexPreview.orEmpty()

internal fun DetailUiState.Stream.asTranscript(): String =
    messages.joinToString("\n") { m ->
        val arrow = if (m.isSent) "→" else "←"
        "[${m.time}] $arrow ${m.type.name.uppercase()} ${m.name.orEmpty()}: ${m.copyText()}".trim()
    }

private fun closeCodeLabel(code: String): String = when (code) {
    "1000" -> "Normal closure"
    "1001" -> "Going away"
    "1002" -> "Protocol error"
    "1003" -> "Unsupported data"
    "1005" -> "No status"
    "1006" -> "Abnormal closure"
    "1007" -> "Invalid payload"
    "1008" -> "Policy violation"
    "1009" -> "Message too big"
    "1011" -> "Internal error"
    else -> "Code"
} + " ($code)"
