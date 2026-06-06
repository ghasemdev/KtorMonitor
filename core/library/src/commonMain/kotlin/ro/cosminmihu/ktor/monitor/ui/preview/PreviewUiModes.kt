package ro.cosminmihu.ktor.monitor.ui.preview

/**
 * `Configuration.UI_MODE_NIGHT_YES or Configuration.UI_MODE_TYPE_NORMAL`.
 *
 * Hard-coded so it can live in `commonMain` (the `android.content.res.Configuration`
 * constants are only available on Android). Used by `@Preview(uiMode = …)` to render
 * a composable in dark mode.
 */
internal const val UI_MODE_NIGHT_YES: Int = 0x21