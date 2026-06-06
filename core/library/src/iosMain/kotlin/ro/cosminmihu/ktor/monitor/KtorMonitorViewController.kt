package ro.cosminmihu.ktor.monitor

import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.window.ComposeUIViewController
import platform.UIKit.UIViewController
import ro.cosminmihu.ktor.monitor.di.LibraryKoinContext
import ro.cosminmihu.ktor.monitor.domain.ConfigUseCase

/**
 * [UIViewController] for [KtorMonitor].
 *
 * ```kotlin
 * fun MainViewController() = KtorMonitorViewController()
 * ```
 *
 *```swift
 * struct KtorMonitorView: UIViewControllerRepresentable {
 *     func makeUIViewController(context: Context) -> UIViewController {
 *         MainViewControllerKt.MainViewController()
 *     }
 *
 *     func updateUIViewController(_ uiViewController: UIViewController, context: Context) {}
 * }
 *
 * struct ContentView: View {
 *     var body: some View {
 *         KtorMonitorView()
 *                 .ignoresSafeArea()
 *     }
 * }
 *```
 */
@OptIn(ExperimentalComposeUiApi::class)
public fun KtorMonitorViewController(
    sharedDBConfig: SharedDBConfig? = null,
): UIViewController = ComposeUIViewController(
    configure = {
        parallelRendering = true
    },
) {
    sharedDBConfig?.groupID?.let {
        LibraryKoinContext.koin
            .get<ConfigUseCase>()
            .setIosGroupId(it)
    }
    KtorMonitor()
}
