package ro.cosminmihu.ktor.monitor.ui.detail.formater

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.cosminmihu.ktor.monitor.ui.detail.body.CodeLine
import ro.cosminmihu.ktor.monitor.ui.detail.body.LocalMaxLineNumber

// --------------------------------------------------------------------------------
// PUBLIC API
// --------------------------------------------------------------------------------

/**
 * Renders a multipart body (e.g. `multipart/form-data`) as a tree of parts.
 * Each part shows its Content-Disposition (name / filename), Content-Type and
 * body preview, and can be collapsed.
 *
 * The boundary is auto-detected from the first line that starts with `--`.
 */
@Composable
internal fun Multipart(
    body: String,
    modifier: Modifier = Modifier,
    colors: MultipartColors = MultipartDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    initialExpanded: Boolean = true,
    onError: (Throwable) -> Unit = {}
) {
    var parts by remember(body) { mutableStateOf<List<MultipartPart>>(emptyList()) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(body) {
        try {
            parts = withContext(Dispatchers.Default) { parseMultipart(body) }
            error = null
        } catch (e: Exception) {
            error = e.message
            onError(e)
        }
    }

    if (error != null || parts.isEmpty()) return

    val rows = remember(parts) { parts.assignLineNumbers() }
    val maxLine = remember(rows) { rows.maxOfOrNull { it.maxLine } ?: 0 }

    val collapsed = remember(rows) { mutableStateMapOf<Int, Boolean>() }
    if (!initialExpanded) {
        remember(rows) { rows.forEach { collapsed[it.openingLine] = true } }
    }

    val visible by remember(rows) {
        derivedStateOf { flattenMultipart(rows, collapsed) }
    }

    SelectionContainer {
        CompositionLocalProvider(LocalMaxLineNumber provides maxLine) {
            LazyColumn(
                modifier = modifier,
                contentPadding = contentPadding,
            ) {
                itemsIndexed(
                    items = visible,
                    key = { _, row -> row.id },
                    contentType = { _, row -> row.kind.name },
                ) { _, row ->
                    MultipartRowView(
                        row = row,
                        colors = colors,
                        onToggle = { id -> collapsed[id] = !(collapsed[id] == true) },
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------
// UI ROW
// --------------------------------------------------------------------------------

@Composable
private fun MultipartRowView(
    row: MultipartRow,
    colors: MultipartColors,
    onToggle: (Int) -> Unit,
) {
    val indentation = 16.dp

    when (row.kind) {
        MultipartRowKind.PART_OPEN -> {
            val arrowRotation by animateFloatAsState(targetValue = if (!row.isCollapsed) 0f else -90f)
            CodeLine(
                lineNumber = row.lineNumber,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle(row.id) },
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = indentation * row.depth, top = 2.dp, bottom = 2.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Box(modifier = Modifier.size(24.dp)) {
                        Image(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            colorFilter = ColorFilter.tint(colors.arrowColor),
                            modifier = Modifier.fillMaxSize().rotate(arrowRotation),
                        )
                    }
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.partColor)) { append("Part ${row.partIndex}") }
                            if (!row.partName.isNullOrBlank()) {
                                withStyle(SpanStyle(color = colors.symbolColor)) { append(" — ") }
                                withStyle(SpanStyle(color = colors.keyColor)) { append(row.partName) }
                            }
                            if (!row.partFilename.isNullOrBlank()) {
                                withStyle(SpanStyle(color = colors.symbolColor)) { append(" (") }
                                withStyle(SpanStyle(color = colors.fileColor)) { append(row.partFilename) }
                                withStyle(SpanStyle(color = colors.symbolColor)) { append(")") }
                            }
                            if (row.isCollapsed) {
                                withStyle(SpanStyle(color = colors.commentColor)) { append("  …") }
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
        }

        MultipartRowKind.HEADER -> {
            CodeLine(
                lineNumber = row.lineNumber,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = indentation * row.depth + 24.dp + 4.dp, top = 1.dp, bottom = 1.dp),
                ) {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(color = colors.headerColor)) { append(row.text.orEmpty()) }
                            withStyle(SpanStyle(color = colors.symbolColor)) { append(": ") }
                            withStyle(SpanStyle(color = colors.valueColor)) { append(row.value.orEmpty()) }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                }
            }
        }

        MultipartRowKind.BODY_LINE -> {
            CodeLine(
                lineNumber = row.lineNumber,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = indentation * row.depth + 24.dp + 4.dp, top = 1.dp, bottom = 1.dp),
                ) {
                    Text(
                        text = row.text.orEmpty(),
                        color = colors.valueColor,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------
// FLATTENING
// --------------------------------------------------------------------------------

private enum class MultipartRowKind { PART_OPEN, HEADER, BODY_LINE }

private data class MultipartRow(
    val id: Int,
    val kind: MultipartRowKind,
    val depth: Int,
    val lineNumber: Int,
    val partIndex: Int = 0,
    val partName: String? = null,
    val partFilename: String? = null,
    val text: String? = null,
    val value: String? = null,
    val isCollapsed: Boolean = false,
)

private data class NumberedPart(
    val part: MultipartPart,
    val openingLine: Int,
    val headerLines: List<Int>,
    val bodyLines: List<Int>,
) {
    val maxLine: Int
        get() = maxOf(openingLine, headerLines.maxOrNull() ?: 0, bodyLines.maxOrNull() ?: 0)
}

private fun List<MultipartPart>.assignLineNumbers(): List<NumberedPart> {
    var counter = 0
    return map { part ->
        val opening = ++counter
        val headerLines = part.headers.map { ++counter }
        val bodyLines = if (part.bodyPreview.isEmpty()) emptyList()
        else part.bodyPreview.map { ++counter }
        NumberedPart(part, opening, headerLines, bodyLines)
    }
}

private fun flattenMultipart(
    parts: List<NumberedPart>,
    collapsed: SnapshotStateMap<Int, Boolean>,
): List<MultipartRow> {
    val out = ArrayList<MultipartRow>()
    parts.forEachIndexed { index, p ->
        val isCollapsed = collapsed[p.openingLine] == true
        out += MultipartRow(
            id = p.openingLine,
            kind = MultipartRowKind.PART_OPEN,
            depth = 0,
            lineNumber = p.openingLine,
            partIndex = index + 1,
            partName = p.part.name,
            partFilename = p.part.filename,
            isCollapsed = isCollapsed,
        )
        if (isCollapsed) return@forEachIndexed

        p.part.headers.forEachIndexed { hi, header ->
            out += MultipartRow(
                id = p.headerLines[hi],
                kind = MultipartRowKind.HEADER,
                depth = 1,
                lineNumber = p.headerLines[hi],
                text = header.first,
                value = header.second,
            )
        }
        p.part.bodyPreview.forEachIndexed { bi, line ->
            out += MultipartRow(
                id = p.bodyLines[bi] + BODY_ID_OFFSET,
                kind = MultipartRowKind.BODY_LINE,
                depth = 1,
                lineNumber = p.bodyLines[bi],
                text = line,
            )
        }
    }
    return out
}

private const val BODY_ID_OFFSET = 1_000_000

// --------------------------------------------------------------------------------
// PARSER
// --------------------------------------------------------------------------------

internal data class MultipartPart(
    val headers: List<Pair<String, String>>,
    val name: String?,
    val filename: String?,
    val bodyPreview: List<String>,
)

private const val MAX_BODY_LINES = 20
private const val MAX_BODY_LINE_LEN = 240

private fun parseMultipart(input: String): List<MultipartPart> {
    if (input.isBlank()) return emptyList()

    // Detect boundary: the first line starting with "--".
    val lines = input.split("\r\n", "\n")
    val first = lines.firstOrNull { it.startsWith("--") } ?: return emptyList()
    val boundary = first.removePrefix("--").trimEnd('-').trim()
    if (boundary.isEmpty()) return emptyList()

    val delimiter = "--$boundary"
    val closeDelimiter = "--$boundary--"

    val parts = mutableListOf<MultipartPart>()
    var i = 0
    var insidePart = false
    val current = StringBuilder()

    fun flush() {
        if (!insidePart) return
        val raw = current.toString().trimEnd('\r', '\n')
        parts += parsePart(raw)
        current.clear()
        insidePart = false
    }

    while (i < lines.size) {
        val line = lines[i]
        when {
            line == closeDelimiter -> {
                flush()
                break
            }
            line == delimiter -> {
                flush()
                insidePart = true
            }
            insidePart -> {
                if (current.isNotEmpty()) current.append('\n')
                current.append(line)
            }
        }
        i++
    }
    flush()
    return parts
}

private fun parsePart(raw: String): MultipartPart {
    val headerEnd = raw.indexOf("\n\n").let { if (it == -1) raw.indexOf("\r\n\r\n") else it }
    val (headerBlock, body) = if (headerEnd == -1) {
        raw to ""
    } else {
        raw.substring(0, headerEnd) to raw.substring(headerEnd).trimStart('\r', '\n')
    }

    val headers = headerBlock.lines()
        .mapNotNull { line ->
            val idx = line.indexOf(':')
            if (idx <= 0) null else line.substring(0, idx).trim() to line.substring(idx + 1).trim()
        }

    val disposition = headers.firstOrNull { it.first.equals("Content-Disposition", true) }?.second.orEmpty()
    val name = extractParam(disposition, "name")
    val filename = extractParam(disposition, "filename")

    val bodyPreview = if (body.isEmpty()) emptyList()
    else body.split('\n')
        .take(MAX_BODY_LINES)
        .map { if (it.length > MAX_BODY_LINE_LEN) it.substring(0, MAX_BODY_LINE_LEN) + "…" else it }
        .let { previews ->
            val total = body.split('\n').size
            if (total > MAX_BODY_LINES) previews + "… (${total - MAX_BODY_LINES} more lines)"
            else previews
        }

    return MultipartPart(
        headers = headers,
        name = name,
        filename = filename,
        bodyPreview = bodyPreview,
    )
}

private fun extractParam(header: String, name: String): String? {
    val pattern = Regex("""${Regex.escape(name)}\s*=\s*"?([^";]+)"?""", RegexOption.IGNORE_CASE)
    return pattern.find(header)?.groupValues?.getOrNull(1)?.trim()
}

// --------------------------------------------------------------------------------
// COLORS
// --------------------------------------------------------------------------------

internal data class MultipartColors(
    val partColor: Color,
    val keyColor: Color,
    val fileColor: Color,
    val headerColor: Color,
    val valueColor: Color,
    val symbolColor: Color,
    val arrowColor: Color,
    val commentColor: Color,
)

internal object MultipartDefaults {
    @Composable
    fun colors(
        partColor: Color = Color(0xFFE91E63),
        keyColor: Color = Color(0xFFFF9800),
        fileColor: Color = Color(0xFF9C27B0),
        headerColor: Color = Color(0xFF03A9F4),
        valueColor: Color = LocalTextStyle.current.color,
        symbolColor: Color = Color(0xFF2E86C1),
        arrowColor: Color = Color(0xFF2E86C1),
        commentColor: Color = LocalTextStyle.current.color.copy(alpha = 0.55f),
    ) = MultipartColors(
        partColor = partColor,
        keyColor = keyColor,
        fileColor = fileColor,
        headerColor = headerColor,
        valueColor = valueColor,
        symbolColor = symbolColor,
        arrowColor = arrowColor,
        commentColor = commentColor,
    )
}

