package ro.cosminmihu.ktor.monitor.ui.detail.formater

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import ro.cosminmihu.ktor.monitor.ui.detail.body.CodeLine
import ro.cosminmihu.ktor.monitor.ui.detail.body.LocalMaxLineNumber
import kotlin.math.max

// --------------------------------------------------------------------------------
// PUBLIC API
// --------------------------------------------------------------------------------

/**
 * Entry point for the JsonTree.
 * Parses the JSON string and displays the tree.
 *
 * @param json The JSON content string.
 * @param modifier Modifier for the root layout.
 * @param colors Custom colors for the tree.
 * @param contentPadding Padding for the container.
 * @param initialExpanded Whether composite nodes start expanded.
 * @param verticalScroll Whether the tree should be vertically scrollable.
 * @param onError Callback for parsing errors.
 */
@Composable
internal fun JsonTree(
    json: String,
    modifier: Modifier = Modifier,
    colors: JsonTreeColors = JsonTreeDefaults.colors(),
    contentPadding: PaddingValues = PaddingValues(0.dp),
    initialExpanded: Boolean = true,
    verticalScroll: Boolean = true,
    onError: (Throwable) -> Unit = {}
) {
    var root by remember(json) { mutableStateOf<JsonNode?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(json) {
        try {
            val element = withContext(Dispatchers.Default) {
                jsonParser.parseToJsonElement(json)
            }
            root = withContext(Dispatchers.Default) {
                buildJsonNode(key = null, element = element).assignLineNumbers()
            }
            error = null
        } catch (e: Exception) {
            error = e.message
            onError(e)
        }
    }

    val node = root ?: return
    if (error != null) return

    val maxLine = remember(node) { node.maxLine() }

    SelectionContainer {
        Column(
            modifier = modifier
                .then(if (verticalScroll) Modifier.verticalScroll(rememberScrollState()) else Modifier)
                .padding(contentPadding),
        ) {
            CompositionLocalProvider(LocalMaxLineNumber provides maxLine) {
                JsonNodeView(
                    node = node,
                    colors = colors,
                    depth = 0,
                    isLast = true,
                    isInitiallyExpanded = initialExpanded,
                )
            }
        }
    }
}

private val jsonParser = Json { isLenient = true; ignoreUnknownKeys = true }

// --------------------------------------------------------------------------------
// UI COMPONENTS
// --------------------------------------------------------------------------------

@Composable
private fun JsonNodeView(
    node: JsonNode,
    colors: JsonTreeColors,
    depth: Int,
    isLast: Boolean,
    isInitiallyExpanded: Boolean,
) {
    val indentation = 16.dp

    when (node) {
        is JsonNode.Composite -> {
            var isExpanded by remember { mutableStateOf(isInitiallyExpanded) }
            val arrowRotation by animateFloatAsState(targetValue = if (isExpanded) 0f else -90f)
            val hasChildren = node.children.isNotEmpty()
            val openBrace = if (node.isArray) "[" else "{"
            val closeBrace = if (node.isArray) "]" else "}"

            Column {
                // Opening line: "key": { ...
                CodeLine(
                    lineNumber = node.openingLine,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(enabled = hasChildren) { isExpanded = !isExpanded },
                ) {
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .padding(start = (indentation * depth), top = 2.dp, bottom = 2.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        Box(modifier = Modifier.size(24.dp)) {
                            if (hasChildren) {
                                Image(
                                    imageVector = Icons.Default.ArrowDropDown,
                                    contentDescription = null,
                                    colorFilter = ColorFilter.tint(colors.arrowColor),
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .rotate(arrowRotation)
                                )
                            }
                        }

                        Text(
                            text = buildAnnotatedString {
                                if (node.key != null) {
                                    withStyle(SpanStyle(color = colors.keyColor)) {
                                        append("\"${node.key}\"")
                                    }
                                    withStyle(SpanStyle(color = colors.symbolColor)) {
                                        append(": ")
                                    }
                                }
                                withStyle(SpanStyle(color = colors.symbolColor)) {
                                    append(openBrace)
                                }
                                if (!hasChildren) {
                                    withStyle(SpanStyle(color = colors.symbolColor)) {
                                        append(closeBrace)
                                        if (!isLast) append(",")
                                    }
                                } else if (!isExpanded) {
                                    withStyle(SpanStyle(color = colors.symbolColor)) {
                                        append(" ... ")
                                        append(closeBrace)
                                        if (!isLast) append(",")
                                    }
                                }
                            },
                            fontFamily = FontFamily.Monospace,
                            fontSize = 14.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }

                AnimatedVisibility(visible = isExpanded && hasChildren) {
                    Column {
                        node.children.forEachIndexed { index, child ->
                            JsonNodeView(
                                node = child,
                                colors = colors,
                                depth = depth + 1,
                                isLast = index == node.children.lastIndex,
                                isInitiallyExpanded = isInitiallyExpanded,
                            )
                        }

                        // Closing brace
                        CodeLine(
                            lineNumber = node.closingLine,
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(
                                        start = (indentation * depth) + 24.dp + 4.dp,
                                        bottom = 2.dp
                                    )
                            ) {
                                Text(
                                    text = buildAnnotatedString {
                                        withStyle(SpanStyle(color = colors.symbolColor)) {
                                            append(closeBrace)
                                            if (!isLast) append(",")
                                        }
                                    },
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 14.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        is JsonNode.Primitive -> {
            CodeLine(
                lineNumber = node.line,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .padding(
                            start = (indentation * depth) + 24.dp + 4.dp,
                            top = 1.dp,
                            bottom = 1.dp
                        )
                ) {
                    Text(
                        text = buildAnnotatedString {
                            if (node.key != null) {
                                withStyle(SpanStyle(color = colors.keyColor)) {
                                    append("\"${node.key}\"")
                                }
                                withStyle(SpanStyle(color = colors.symbolColor)) {
                                    append(": ")
                                }
                            }
                            val valueColor = when (node.kind) {
                                JsonPrimitiveKind.STRING -> colors.stringColor
                                JsonPrimitiveKind.NUMBER -> colors.numberColor
                                JsonPrimitiveKind.BOOLEAN -> colors.booleanColor
                                JsonPrimitiveKind.NULL -> colors.nullColor
                            }
                            withStyle(SpanStyle(color = valueColor)) {
                                append(node.displayValue)
                            }
                            if (!isLast) {
                                withStyle(SpanStyle(color = colors.symbolColor)) {
                                    append(",")
                                }
                            }
                        },
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp
                    )
                }
            }
        }
    }
}

// --------------------------------------------------------------------------------
// DATA MODEL
// --------------------------------------------------------------------------------

internal enum class JsonPrimitiveKind { STRING, NUMBER, BOOLEAN, NULL }

internal sealed class JsonNode {
    abstract val key: String?

    data class Composite(
        override val key: String?,
        val isArray: Boolean,
        val children: List<JsonNode>,
        val openingLine: Int = 0,
        val closingLine: Int = 0,
    ) : JsonNode()

    data class Primitive(
        override val key: String?,
        val kind: JsonPrimitiveKind,
        val displayValue: String,
        val line: Int = 0,
    ) : JsonNode()
}

private fun buildJsonNode(key: String?, element: JsonElement): JsonNode = when (element) {
    is JsonObject -> {
        val children = element.entries.map { (k, v) -> buildJsonNode(k, v) }
        JsonNode.Composite(key = key, isArray = false, children = children)
    }
    is JsonArray -> {
        val children = element.map { v -> buildJsonNode(null, v) }
        JsonNode.Composite(key = key, isArray = true, children = children)
    }
    is JsonNull -> JsonNode.Primitive(
        key = key,
        kind = JsonPrimitiveKind.NULL,
        displayValue = "null",
    )
    is JsonPrimitive -> {
        val (kind, display) = when {
            element.isString -> JsonPrimitiveKind.STRING to "\"${element.content}\""
            element.content == "true" || element.content == "false" -> JsonPrimitiveKind.BOOLEAN to element.content
            else -> JsonPrimitiveKind.NUMBER to element.content
        }
        JsonNode.Primitive(key = key, kind = kind, displayValue = display)
    }
}


/**
 * Walks the parsed tree once and assigns a stable, monotonically increasing
 * line number to every row that will ever be rendered. Numbers are kept across
 * collapse/expand toggles so the gutter behaves like code folding.
 */
private fun JsonNode.assignLineNumbers(): JsonNode {
    var counter = 0
    fun walk(node: JsonNode): JsonNode = when (node) {
        is JsonNode.Composite -> {
            val opening = ++counter
            val newChildren = node.children.map(::walk)
            val closing = if (node.children.isEmpty()) 0 else ++counter
            node.copy(
                openingLine = opening,
                closingLine = closing,
                children = newChildren,
            )
        }
        is JsonNode.Primitive -> node.copy(line = ++counter)
    }
    return walk(this)
}

private fun JsonNode.maxLine(): Int {
    var m = 0
    fun visit(node: JsonNode) {
        when (node) {
            is JsonNode.Composite -> {
                m = max(m, max(node.openingLine, node.closingLine))
                node.children.forEach(::visit)
            }
            is JsonNode.Primitive -> m = max(m, node.line)
        }
    }
    visit(this)
    return m
}

// --------------------------------------------------------------------------------
// COLORS
// --------------------------------------------------------------------------------

internal data class JsonTreeColors(
    val keyColor: Color,
    val stringColor: Color,
    val numberColor: Color,
    val booleanColor: Color,
    val nullColor: Color,
    val symbolColor: Color,
    val arrowColor: Color,
)

internal object JsonTreeDefaults {
    @Composable
    fun colors(
        keyColor: Color = Color(0xFF9C27B0),
        stringColor: Color = Color(0xFF4CAF50),
        numberColor: Color = Color(0xFFFF9800),
        booleanColor: Color = Color(0xFFE91E63),
        nullColor: Color = LocalTextStyle.current.color,
        symbolColor: Color = Color(0xFF2E86C1),
        arrowColor: Color = Color(0xFF2E86C1),
    ) = JsonTreeColors(
        keyColor = keyColor,
        stringColor = stringColor,
        numberColor = numberColor,
        booleanColor = booleanColor,
        nullColor = nullColor,
        symbolColor = symbolColor,
        arrowColor = arrowColor,
    )
}

