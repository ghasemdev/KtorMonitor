package ro.cosminmihu.ktor.monitor.ui

import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/** Wraps [content] in a Box and overlays a vertical scrollbar driven by [state]. */
@Composable
internal expect fun VerticalScrollbarBox(
    state: LazyListState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)

/** Wraps [content] in a Box and overlays a vertical scrollbar driven by [state]. */
@Composable
internal expect fun VerticalScrollbarBox(
    state: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)

/**
 * Wraps [content] in a Box and overlays both a vertical scrollbar (driven by [vState])
 * and a horizontal scrollbar (driven by [hState]).
 */
@Composable
internal expect fun BothScrollbarsBox(
    vState: LazyListState,
    hState: ScrollState,
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit,
)
