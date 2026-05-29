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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import ro.cosminmihu.ktor.monitor.ui.BothScrollbarsBox
import ro.cosminmihu.ktor.monitor.ui.detail.body.CodeLine
import ro.cosminmihu.ktor.monitor.ui.detail.body.LocalMaxLineNumber

// ======================== MARKDOWN HIGHLIGHTER FOR CODE VIEW ========================

@Composable
internal fun MarkdownHighlighter(
    markdown: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var lines by remember(markdown) { mutableStateOf<List<MarkdownHighlightedLine>>(emptyList()) }

    LaunchedEffect(markdown) {
        lines = withContext(Dispatchers.Default) {
            parseMarkdownLines(markdown)
        }
    }

    if (lines.isEmpty()) return

    val listState = rememberLazyListState()
    val hScrollState = rememberScrollState()
    BothScrollbarsBox(listState, hScrollState, modifier) {
        Box(Modifier.fillMaxSize().horizontalScroll(hScrollState)) {
            SelectionContainer {
                CompositionLocalProvider(LocalMaxLineNumber provides lines.size) {
                    LazyColumn(
                        state = listState,
                        contentPadding = contentPadding,
                    ) {
                        itemsIndexed(
                            items = lines,
                            key = { index, _ -> index },
                            contentType = { _, _ -> "markdown-line" },
                        ) { index, line ->
                            MarkdownLineView(lineNumber = index + 1, line = line)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownLineView(lineNumber: Int, line: MarkdownHighlightedLine) {
    CodeLine(lineNumber = lineNumber) {
        Row(
            modifier = Modifier.padding(start = 8.dp, top = 1.dp, bottom = 1.dp),
        ) {
            Text(
                text = buildAnnotatedString {
                    val mdColors = MarkdownHighlighterColors
                    when (line) {
                        is MarkdownHighlightedLine.Heading -> {
                            val color = if (line.level == 1) mdColors.heading else mdColors.heading2
                            withStyle(SpanStyle(color = color, fontWeight = FontWeight.Bold)) {
                                append(line.text)
                            }
                        }
                        is MarkdownHighlightedLine.Quote -> {
                            withStyle(SpanStyle(color = mdColors.quote)) {
                                append("| ")
                            }
                            append(line.text)
                        }
                        is MarkdownHighlightedLine.ListItem -> {
                            withStyle(SpanStyle(color = mdColors.listMarker)) {
                                append("• ")
                            }
                            append(line.text)
                        }
                        is MarkdownHighlightedLine.CodeFence -> {
                            withStyle(SpanStyle(color = mdColors.codeFence)) {
                                append(line.text)
                            }
                        }
                        is MarkdownHighlightedLine.CodeLine -> {
                            withStyle(SpanStyle(color = mdColors.code)) {
                                append(line.text)
                            }
                        }
                        else -> append(line.text)
                    }
                },
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                softWrap = false,
            )
        }
    }
}

private sealed interface MarkdownHighlightedLine {
    val text: String

    data class Heading(override val text: String, val level: Int) : MarkdownHighlightedLine
    data class Quote(override val text: String) : MarkdownHighlightedLine
    data class ListItem(override val text: String) : MarkdownHighlightedLine
    data class CodeFence(override val text: String) : MarkdownHighlightedLine
    data class CodeLine(override val text: String) : MarkdownHighlightedLine
    data class Paragraph(override val text: String) : MarkdownHighlightedLine
}

private object MarkdownHighlighterColors {
    val heading = Color(0xFFE91E63)
    val heading2 = Color(0xFFF06292)
    val quote = Color(0xFF9C27B0)
    val code = Color(0xFF00897B)
    val codeFence = Color(0xFF455A64)
    val listMarker = Color(0xFF2196F3)
}

private fun parseMarkdownLines(markdown: String): List<MarkdownHighlightedLine> {
    val lines = mutableListOf<MarkdownHighlightedLine>()
    val rawLines = markdown.split("\n")
    var inCodeFence = false

    for (rawLine in rawLines) {
        val line = rawLine.trimEnd()

        if (line.startsWith("```") || line.startsWith("~~~")) {
            inCodeFence = !inCodeFence
            lines.add(MarkdownHighlightedLine.CodeFence(line))
            continue
        }

        if (inCodeFence) {
            lines.add(MarkdownHighlightedLine.CodeLine(line))
            continue
        }

        if (line.isBlank()) {
            lines.add(MarkdownHighlightedLine.Paragraph(line))
            continue
        }

        val trimmed = line.trim()

        val headingLevel = trimmed.takeWhile { it == '#' }.length
        if (headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ') {
            lines.add(MarkdownHighlightedLine.Heading(trimmed.drop(headingLevel + 1), headingLevel))
            continue
        }

        if (trimmed.startsWith(">")) {
            lines.add(MarkdownHighlightedLine.Quote(trimmed.drop(1).trim()))
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            lines.add(MarkdownHighlightedLine.ListItem(trimmed.drop(2).trim()))
            continue
        }

        lines.add(MarkdownHighlightedLine.Paragraph(line))
    }

    return lines
}

// ======================== MARKDOWN PREVIEW ========================

@Composable
internal fun MarkdownPreview(
    markdown: String,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
) {
    var blocks by remember(markdown) { mutableStateOf<List<MarkdownBlock>>(emptyList()) }

    LaunchedEffect(markdown) {
        blocks = withContext(Dispatchers.Default) {
            parseMarkdown(markdown)
        }
    }

    if (blocks.isEmpty()) return

    val listState = rememberLazyListState()
    val hScrollState = rememberScrollState()
    BothScrollbarsBox(listState, hScrollState, modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().horizontalScroll(hScrollState)) {
            SelectionContainer {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = contentPadding,
                ) {
                    itemsIndexed(
                        items = blocks,
                        key = { index, _ -> index },
                        contentType = { _, block ->
                            when (block) {
                                is MarkdownBlock.Heading -> "heading"
                                is MarkdownBlock.Paragraph -> "paragraph"
                                is MarkdownBlock.BlockQuote -> "quote"
                                is MarkdownBlock.UnorderedItem -> "unordered-item"
                                is MarkdownBlock.OrderedItem -> "ordered-item"
                                is MarkdownBlock.CodeFence -> "code-fence"
                                MarkdownBlock.HorizontalRule -> "rule"
                            }
                        },
                    ) { _, block ->
                        when (block) {
                            is MarkdownBlock.Heading -> {
                                val style = when (block.level) {
                                    1 -> MaterialTheme.typography.headlineMedium
                                    2 -> MaterialTheme.typography.headlineSmall
                                    3 -> MaterialTheme.typography.titleLarge
                                    4 -> MaterialTheme.typography.titleMedium
                                    else -> MaterialTheme.typography.titleSmall
                                }
                                Text(
                                    text = block.text,
                                    style = style,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(vertical = 4.dp),
                                )
                            }

                            is MarkdownBlock.Paragraph -> Text(
                                text = block.text,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )

                            is MarkdownBlock.BlockQuote -> Text(
                                text = "| ${block.text}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 3.dp),
                            )

                            is MarkdownBlock.UnorderedItem -> Text(
                                text = "\u2022 ${block.text}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                            )

                            is MarkdownBlock.OrderedItem -> Text(
                                text = "${block.number}. ${block.text}",
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(start = 8.dp, top = 2.dp, bottom = 2.dp),
                            )

                            is MarkdownBlock.CodeFence -> Text(
                                text = block.code,
                                style = MaterialTheme.typography.bodySmall,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.padding(vertical = 4.dp),
                            )

                            MarkdownBlock.HorizontalRule -> Text(
                                text = "----------",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(vertical = 6.dp),
                            )
                        }
                    }
                }
            }
        }
    }
}

private sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BlockQuote(val text: String) : MarkdownBlock
    data class UnorderedItem(val text: String) : MarkdownBlock
    data class OrderedItem(val number: Int, val text: String) : MarkdownBlock
    data class CodeFence(val code: String) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
}

private fun parseMarkdown(markdown: String): List<MarkdownBlock> {
    val lines = markdown.replace("\r\n", "\n").replace('\r', '\n').split('\n')
    val blocks = mutableListOf<MarkdownBlock>()
    var index = 0

    while (index < lines.size) {
        val line = lines[index]
        val trimmed = line.trim()

        if (trimmed.isBlank()) {
            index++
            continue
        }

        if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
            val fence = trimmed.take(3)
            val codeLines = mutableListOf<String>()
            index++
            while (index < lines.size && !lines[index].trim().startsWith(fence)) {
                codeLines += lines[index]
                index++
            }
            if (index < lines.size) index++
            blocks += MarkdownBlock.CodeFence(codeLines.joinToString("\n"))
            continue
        }

        if (trimmed == "---" || trimmed == "***" || trimmed == "___") {
            blocks += MarkdownBlock.HorizontalRule
            index++
            continue
        }

        val headingLevel = trimmed.takeWhile { it == '#' }.length
        if (headingLevel in 1..6 && trimmed.getOrNull(headingLevel) == ' ') {
            blocks += MarkdownBlock.Heading(headingLevel, trimmed.drop(headingLevel + 1))
            index++
            continue
        }

        if (trimmed.startsWith(">")) {
            blocks += MarkdownBlock.BlockQuote(trimmed.removePrefix(">").trim())
            index++
            continue
        }

        if (trimmed.startsWith("- ") || trimmed.startsWith("* ") || trimmed.startsWith("+ ")) {
            blocks += MarkdownBlock.UnorderedItem(trimmed.drop(2).trim())
            index++
            continue
        }

        val dotIndex = trimmed.indexOf('.')
        if (dotIndex > 0 && trimmed.substring(0, dotIndex).all { it.isDigit() } && trimmed.getOrNull(dotIndex + 1) == ' ') {
            blocks += MarkdownBlock.OrderedItem(
                number = trimmed.substring(0, dotIndex).toIntOrNull() ?: 1,
                text = trimmed.drop(dotIndex + 2).trim(),
            )
            index++
            continue
        }

        val paragraphLines = mutableListOf(trimmed)
        index++
        while (index < lines.size) {
            val nextTrimmed = lines[index].trim()
            if (nextTrimmed.isBlank()) {
                index++
                break
            }
            if (
                nextTrimmed.startsWith("#") ||
                nextTrimmed.startsWith(">") ||
                nextTrimmed.startsWith("- ") ||
                nextTrimmed.startsWith("* ") ||
                nextTrimmed.startsWith("+ ") ||
                nextTrimmed.startsWith("```") ||
                nextTrimmed.startsWith("~~~") ||
                nextTrimmed == "---" ||
                nextTrimmed == "***" ||
                nextTrimmed == "___"
            ) {
                break
            }
            paragraphLines += nextTrimmed
            index++
        }
        blocks += MarkdownBlock.Paragraph(paragraphLines.joinToString(" "))
    }

    return blocks
}

