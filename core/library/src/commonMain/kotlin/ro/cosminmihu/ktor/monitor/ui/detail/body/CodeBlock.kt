package ro.cosminmihu.ktor.monitor.ui.detail.body

import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip

@Composable
internal fun Modifier.codeBlock(): Modifier {
    val shape = MaterialTheme.shapes.medium
    return this
        .clip(shape)
        .background(MaterialTheme.colorScheme.surfaceContainer, shape)
}
