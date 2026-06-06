package ro.cosminmihu.ktor.monitor.ui.detail.body

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.SubcomposeLayout
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.max

/**
 * Provides the maximum line number that will be rendered inside a code view,
 * so [CodeLine] can left-pad smaller numbers with spaces and keep the gutter
 * pixel-perfect (monospace font ⇒ identical glyph + space advance).
 *
 * Top-level formatters (e.g. CSS / XML / Form trees) wrap their content with
 * `CompositionLocalProvider(LocalMaxLineNumber provides max)`.
 */
internal val LocalMaxLineNumber = compositionLocalOf { 0 }

/**
 * A single "code editor" row: a fixed-width gutter cell with [lineNumber], a
 * 1.dp vertical divider, then the row [content].
 *
 * Because the gutter cell is part of the same Row layout as the content, the
 * line number is positioned together with the content in a single layout pass
 * and follows it pixel-perfectly during expand / collapse animations (no
 * lag from a separate gutter measurement).
 */
@Composable
internal fun CodeLine(
    lineNumber: Int,
    modifier: Modifier = Modifier,
    gutterColor: Color = LocalContentColor.current.copy(alpha = 0.55f),
    dividerColor: Color = MaterialTheme.colorScheme.outlineVariant,
    fontSize: TextUnit = 14.sp,
    cellWidth: Dp = 32.dp,
    horizontalPadding: Dp = 8.dp,
    content: @Composable RowScope.() -> Unit,
) {
    val maxLine = LocalMaxLineNumber.current
    val digits = max(maxLine.toString().length, lineNumber.toString().length)
    val label = lineNumber.toString().padStart(digits)
    Row(modifier = modifier.height(IntrinsicSize.Min)) {
        Text(
            text = label,
            color = gutterColor,
            fontFamily = FontFamily.Monospace,
            fontSize = fontSize,
            textAlign = TextAlign.End,
            softWrap = false,
            modifier = Modifier
                .widthIn(min = cellWidth)
                .padding(horizontal = horizontalPadding),
        )
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight()
                .background(dividerColor),
        )
        content()
    }
}

/**
 * Renders [content] with a left-side line-number gutter, approximating the
 * line count from the intrinsic content height divided by [rowHeight].
 *
 * Use this for plain monospace text content (e.g. RAW body). For tree-shaped
 * content with per-row collapse/expand, prefer [CodeLine] inside each row so
 * numbers stay glued to their rows during animations.
 */
@Composable
internal fun LineNumberGutter(
    modifier: Modifier = Modifier,
    rowHeight: Dp = 22.dp,
    fontSize: TextUnit = 14.sp,
    horizontalPadding: Dp = 8.dp,
    content: @Composable () -> Unit,
) {
    val gutterColor = LocalContentColor.current.copy(alpha = 0.55f)
    val dividerColor = MaterialTheme.colorScheme.outlineVariant
    val scrollState = rememberScrollState()

    SubcomposeLayout(modifier = modifier.verticalScroll(scrollState)) { constraints ->
        // Probe to determine number-cell width using the widest expected number.
        val probe = subcompose("probe") {
            Text(
                text = "9999",
                fontFamily = FontFamily.Monospace,
                fontSize = fontSize,
            )
        }.first().measure(Constraints())
        val padPx = horizontalPadding.roundToPx()
        val dividerPx = 1.dp.roundToPx()
        val gutterWidthPx = probe.width + padPx * 2

        // Measure content with the remaining width and unbounded height.
        val contentMaxWidth = max(0, constraints.maxWidth - gutterWidthPx - dividerPx)
        val contentConstraints = Constraints(
            minWidth = 0,
            maxWidth = contentMaxWidth,
            minHeight = 0,
            maxHeight = Constraints.Infinity,
        )
        val contentPlaceable = subcompose("content") {
            Box { content() }
        }.first().measure(contentConstraints)

        val rowHeightPx = max(1, rowHeight.roundToPx())
        // Base line count strictly on the content's intrinsic height so that:
        //  - empty content => 0 lines (no gutter rendered)
        //  - a single-row content => only "1", without padding numbers down to fill the viewport
        val numLines = if (contentPlaceable.height == 0) {
            0
        } else {
            (contentPlaceable.height + rowHeightPx - 1) / rowHeightPx
        }
        val totalHeight = max(contentPlaceable.height, constraints.minHeight)

        if (numLines == 0) {
            return@SubcomposeLayout layout(contentPlaceable.width, totalHeight) {
                contentPlaceable.place(0, 0)
            }
        }

        val gutterPlaceable = subcompose("gutter") {
            Layout(
                content = {
                    repeat(numLines) { index ->
                        Text(
                            text = (index + 1).toString(),
                            color = gutterColor,
                            fontFamily = FontFamily.Monospace,
                            fontSize = fontSize,
                            textAlign = TextAlign.End,
                            modifier = Modifier
                                .height(rowHeight)
                                .widthIn(min = probe.width.toDp()),
                        )
                    }
                },
            ) { measurables, _ ->
                val cellW = probe.width
                val placeables = measurables.map {
                    it.measure(Constraints.fixed(cellW, rowHeightPx))
                }
                layout(gutterWidthPx, totalHeight) {
                    val x = gutterWidthPx - cellW - padPx
                    placeables.forEachIndexed { i, p -> p.place(x, i * rowHeightPx) }
                }
            }
        }.first().measure(Constraints.fixed(gutterWidthPx, totalHeight))

        val dividerPlaceable = subcompose("divider") {
            Canvas(
                modifier = Modifier
                    .width(1.dp)
                    .height(totalHeight.toDp()),
            ) {
                drawRect(color = dividerColor)
            }
        }.first().measure(Constraints.fixed(dividerPx, totalHeight))

        val width = gutterWidthPx + dividerPx + contentPlaceable.width
        layout(width, totalHeight) {
            gutterPlaceable.place(0, 0)
            dividerPlaceable.place(gutterWidthPx, 0)
            contentPlaceable.place(gutterWidthPx + dividerPx, 0)
        }
    }
}
