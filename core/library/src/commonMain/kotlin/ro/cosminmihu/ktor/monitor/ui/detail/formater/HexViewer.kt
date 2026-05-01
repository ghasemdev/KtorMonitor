package ro.cosminmihu.ktor.monitor.ui.detail.formater

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.cosminmihu.ktor.monitor.ui.BothScrollbarsBox
import ro.cosminmihu.ktor.monitor.ui.detail.body.CodeLine
import ro.cosminmihu.ktor.monitor.ui.detail.body.LocalMaxLineNumber

private const val BYTES_PER_ROW = 16

/**
 * Hex dump viewer. Parses the space-separated **decimal** byte string produced
 * by `ByteArray.toBytesString()` and renders it in the classic format:
 *
 * ```
 * 00000000  48 65 6C 6C 6F 20 57 6F  72 6C 64 0A              Hello World.
 * ```
 *
 * Each row shows:
 * - 8-digit hex offset
 * - 16 bytes as two-digit uppercase hex values (space-separated, mid-gap after 8)
 * - ASCII representation (printable chars or `.`)
 *
 * @param bytes Space-separated decimal byte string (e.g. `"72 101 108"`).
 * @param modifier Modifier for the root layout.
 * @param contentPadding Padding for the lazy column.
 */
@Composable
internal fun HexViewer(
    bytes: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    val offsetColor = LocalTextStyle.current.color.copy(alpha = 0.55f)
    val hexColor = LocalTextStyle.current.color
    val asciiColor = Color(0xFF4CAF50)

    var rows by remember(bytes) { mutableStateOf<List<HexRow>>(emptyList()) }

    LaunchedEffect(bytes) {
        rows = withContext(Dispatchers.Default) { buildHexRows(bytes) }
    }

    if (rows.isEmpty()) return

    val hScrollState = rememberScrollState()
    val vListState = rememberLazyListState()

    BothScrollbarsBox(vListState, hScrollState, modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(hScrollState),
        ) {
            SelectionContainer {
                CompositionLocalProvider(LocalMaxLineNumber provides rows.size) {
                    LazyColumn(
                        state = vListState,
                        contentPadding = contentPadding,
                    ) {
                        itemsIndexed(
                            items = rows,
                            key = { _, row -> row.offset },
                            contentType = { _, _ -> "hex-row" },
                        ) { index, row ->
                            CodeLine(
                                lineNumber = index + 1,
                            ) {
                                Row(
                                    modifier = Modifier
                                        .padding(start = 8.dp, top = 1.dp, bottom = 1.dp),
                                ) {
                                    Text(
                                        text = buildAnnotatedString {
                                            withStyle(SpanStyle(color = offsetColor)) {
                                                append(row.offsetText)
                                                append("  ")
                                            }
                                            withStyle(SpanStyle(color = hexColor)) {
                                                append(row.hexText)
                                            }
                                            append("  ")
                                            withStyle(SpanStyle(color = asciiColor)) {
                                                append(row.asciiText)
                                            }
                                        },
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        softWrap = false,
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private data class HexRow(
    val offset: Int,
    val offsetText: String,
    val hexText: String,
    val asciiText: String,
)

/**
 * Parses the space-separated decimal byte string and builds one [HexRow] per
 * [BYTES_PER_ROW] bytes.  Returns an empty list if the input is blank or
 * cannot be parsed.
 */
private fun buildHexRows(bytesStr: String): List<HexRow> {
    if (bytesStr.isBlank()) return emptyList()
    val byteValues: ByteArray = try {
        val tokens = bytesStr.trim().split(' ')
        ByteArray(tokens.size) { i -> tokens[i].toInt().toByte() }
    } catch (_: Exception) {
        return emptyList()
    }

    val rows = ArrayList<HexRow>((byteValues.size + BYTES_PER_ROW - 1) / BYTES_PER_ROW)
    var offset = 0
    while (offset < byteValues.size) {
        val slice = byteValues.copyOfRange(offset, minOf(offset + BYTES_PER_ROW, byteValues.size))

        // Hex section: "XX XX XX XX XX XX XX XX  XX XX XX XX XX XX XX XX"
        val hexBuilder = StringBuilder(3 * BYTES_PER_ROW + 1)
        slice.forEachIndexed { i, b ->
            if (i == BYTES_PER_ROW / 2) hexBuilder.append(' ') // mid-gap
            hexBuilder.append(
                (b.toInt() and 0xFF).toString(16).uppercase().padStart(2, '0')
            )
            if (i < slice.lastIndex) hexBuilder.append(' ')
        }
        // Pad short last row so ASCII column is aligned
        val expectedHexLen = 3 * BYTES_PER_ROW + 1 - 1  // 3*16+1-1 = 48 chars
        while (hexBuilder.length < expectedHexLen) hexBuilder.append(' ')

        // ASCII section
        val asciiBuilder = StringBuilder(BYTES_PER_ROW)
        slice.forEach { b ->
            val c = b.toInt() and 0xFF
            asciiBuilder.append(if (c in 0x20..0x7E) c.toChar() else '.')
        }

        rows += HexRow(
            offset = offset,
            offsetText = offset.toString(16).uppercase().padStart(8, '0'),
            hexText = hexBuilder.toString(),
            asciiText = asciiBuilder.toString(),
        )
        offset += slice.size
    }
    return rows
}