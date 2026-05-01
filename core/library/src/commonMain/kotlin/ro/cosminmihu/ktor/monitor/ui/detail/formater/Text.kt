package ro.cosminmihu.ktor.monitor.ui.detail.formater

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.cosminmihu.ktor.monitor.ui.VerticalScrollbarBox
import ro.cosminmihu.ktor.monitor.ui.detail.body.CodeLine
import ro.cosminmihu.ktor.monitor.ui.detail.body.LocalMaxLineNumber

/**
 * Plain text viewer. For very large content prefer [TextLines] which renders
 * each line lazily with an optional line-number gutter.
 */
@Composable
internal fun Text(
    text: String,
    modifier: Modifier = Modifier,
    verticalScroll: Boolean = true,
) {
    if (verticalScroll) {
        val scrollState = rememberScrollState()
        VerticalScrollbarBox(scrollState, modifier) {
            SelectionContainer(modifier = Modifier.verticalScroll(scrollState)) {
                Text(
                    text = text,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    } else {
        SelectionContainer(modifier = modifier) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

/**
 * Line-by-line text viewer backed by a [LazyColumn] so very large bodies do
 * not measure / lay out all lines at once. When [showLineNumbers] is true the
 * left-side gutter from [CodeLine] is used.
 */
@Composable
internal fun TextLines(
    text: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    showLineNumbers: Boolean = false,
) {
    var lines by remember(text) { mutableStateOf<List<String>>(emptyList()) }

    LaunchedEffect(text) {
        lines = withContext(Dispatchers.Default) { text.split('\n') }
    }

    if (lines.isEmpty()) return

    val listState = rememberLazyListState()
    VerticalScrollbarBox(listState, modifier) {
        SelectionContainer {
            CompositionLocalProvider(LocalMaxLineNumber provides lines.size) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(
                        items = lines,
                        key = { index, _ -> index },
                        contentType = { _, _ -> "text-line" },
                    ) { index, line ->
                        if (showLineNumbers) {
                            CodeLine(
                                lineNumber = index + 1,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Row(
                                    modifier = Modifier
                                        .weight(1f)
                                        .padding(start = 8.dp, top = 1.dp, bottom = 1.dp),
                                ) {
                                    Text(
                                        text = line,
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = line,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                    }
                }
            }
        }
    }
}
