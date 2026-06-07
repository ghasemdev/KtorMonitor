package ro.cosminmihu.ktor.monitor.attr

import io.ktor.util.AttributeKey

/**
 * Attribute key to provide a custom request URL to be logged.
 * This is useful if the URL/query parameters are encrypted and you want to log the decrypted version.
 */
public val KtorMonitorRequestUrl: AttributeKey<String> = AttributeKey("KtorMonitorRequestUrl")

/**
 * Attribute key to provide a custom request body to be logged.
 * This is useful if the body is encrypted and you want to log the decrypted version.
 */
public val KtorMonitorRequestBody: AttributeKey<ByteArray> = AttributeKey("KtorMonitorRequestBody")

/**
 * Attribute key to provide a custom response body to be logged.
 * This is useful if the body is encrypted and you want to log the decrypted version.
 */
public val KtorMonitorResponseBody: AttributeKey<ByteArray> = AttributeKey("KtorMonitorResponseBody")
