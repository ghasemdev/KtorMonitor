package ro.cosminmihu.ktor.monitor.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

@Composable
internal actual fun VerticalScrollbarBox(
    state: LazyListState,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier, content = content)
}

@Composable
internal actual fun VerticalScrollbarBox(
    state: ScrollState,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier, content = content)
}

@Composable
internal actual fun BothScrollbarsBox(
    vState: LazyListState,
    hState: ScrollState,
    modifier: Modifier,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(modifier = modifier, content = content)
}

