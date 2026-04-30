package ro.cosminmihu.ktor.monitor.ui.detail.headers

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import org.jetbrains.compose.resources.stringResource
import ro.cosminmihu.ktor.monitor.ui.detail.transaction.TransactionSection
import ro.cosminmihu.ktor.monitor.ui.resources.Res
import ro.cosminmihu.ktor.monitor.ui.resources.ktor_headers
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.tooling.preview.PreviewParameter
import androidx.compose.ui.tooling.preview.datasource.LoremIpsum
import ro.cosminmihu.ktor.monitor.ui.preview.UI_MODE_NIGHT_YES
import ro.cosminmihu.ktor.monitor.ui.theme.LibraryTheme

@Composable
internal fun Headers(headers: Map<String, List<String>>) {
    TransactionSection(title = stringResource(Res.string.ktor_headers)) {
        if (headers.isEmpty()) {
            NoHeaders()
            return@TransactionSection
        }

        SelectionContainer {
            Column {
                headers.forEach {
                    Text(
                        text = buildAnnotatedString {
                            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                append(it.key)
                            }
                            append(": ")
                            append(it.value.joinToString(", "))
                        },
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
        }
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HeadersPreview(
    @PreviewParameter(LoremIpsum::class) lorem: String,
) {
    val userAgent = lorem.take(48)
    LibraryTheme {
        Headers(
            headers = mapOf(
                "Content-Type" to listOf("application/json"),
                "Content-Length" to listOf("1234"),
                "Accept" to listOf("text/html", "application/xhtml+xml"),
                "User-Agent" to listOf(userAgent),
            ),
        )
    }
}

@Preview(name = "Light")
@Preview(name = "Dark", uiMode = UI_MODE_NIGHT_YES)
@Composable
private fun HeadersEmptyPreview() {
    LibraryTheme {
        Headers(headers = emptyMap())
    }
}
