package ro.cosminmihu.ktor.monitor.domain.model

import ro.cosminmihu.ktor.monitor.InternalKtorMonitorApi

/**
 * Identifies which HTTP client library is being monitored.
 *
 * This type is exposed only for cross-module communication via
 * [ro.cosminmihu.ktor.monitor.InternalLibraryBridge] and is **not** part of the public API.
 */
public enum class ClientSource {
    Ktor,
    OkHttp,
}