package ro.cosminmihu.ktor.monitor.ui.detail.body

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
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
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
