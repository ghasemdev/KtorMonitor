package ro.cosminmihu.ktor.monitor.sample

import io.ktor.client.request.delete
import io.ktor.client.request.forms.FormDataContent
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.Parameters
import io.ktor.http.contentType
import ro.cosminmihu.ktor.monitor.sample.shared.HTTP_BIN_URL
import ro.cosminmihu.ktor.monitor.sample.shared.MARKDOWN_SAMPLE
import ro.cosminmihu.ktor.monitor.sample.shared.TEXT_FILE_CONTENT_SAMPLE
import ro.cosminmihu.ktor.monitor.sample.shared.TEXT_SAMPLE
import ro.cosminmihu.ktor.monitor.sample.shared.SVG_SAMPLE
import ro.cosminmihu.ktor.monitor.sample.shared.YAML_SAMPLE


internal suspend fun samples() {
    with(httpClient()) {
        // HTTP Methods
        runCatching { this@with.get("$HTTP_BIN_URL/get") }
        runCatching { this@with.post("$HTTP_BIN_URL/post") { setBody("") } }
        runCatching { this@with.put("$HTTP_BIN_URL/put") { setBody("") } }
        runCatching { this@with.delete("$HTTP_BIN_URL/delete") }
        runCatching { this@with.patch("$HTTP_BIN_URL/patch") { setBody("") } }

        // Status codes
        runCatching { this@with.get("$HTTP_BIN_URL/status/200") }
        runCatching { this@with.get("$HTTP_BIN_URL/status/404") }
        runCatching { this@with.get("$HTTP_BIN_URL/status/500") }

        // Request inspection
        runCatching { this@with.get("$HTTP_BIN_URL/headers") }
        runCatching { this@with.get("$HTTP_BIN_URL/ip") }
        runCatching { this@with.get("$HTTP_BIN_URL/user-agent") }

        // Response formats
        runCatching { this@with.get("$HTTP_BIN_URL/json") }
        runCatching { this@with.get("$HTTP_BIN_URL/html") }
        runCatching { this@with.get("$HTTP_BIN_URL/xml") }
        runCatching {
            this@with.post("$HTTP_BIN_URL/anything/markdown") {
                contentType(ContentType.parse("text/markdown"))
                setBody(MARKDOWN_SAMPLE)
            }
        }
        runCatching {
            this@with.post("$HTTP_BIN_URL/anything/yaml") {
                contentType(ContentType.parse("application/yaml"))
                setBody(YAML_SAMPLE)
            }
        }

        // Images
        runCatching { this@with.get("$HTTP_BIN_URL/image/jpeg") }
        runCatching { this@with.get("$HTTP_BIN_URL/image/png") }
        runCatching { this@with.get("$HTTP_BIN_URL/image/svg") }

        // Dynamic data
        runCatching { this@with.get("$HTTP_BIN_URL/uuid") }
        runCatching { this@with.get("$HTTP_BIN_URL/bytes/1024") }
        runCatching { this@with.get("$HTTP_BIN_URL/delay/1") }

        // Form data (url-encoded)
        runCatching {
            this@with.post("$HTTP_BIN_URL/post") {
                contentType(ContentType.Application.FormUrlEncoded)
                setBody(
                    FormDataContent(
                        Parameters.build {
                            append("username", "ktor-monitor")
                            append("email", "demo@example.com")
                            append("notes", TEXT_SAMPLE)
                        }
                    )
                )
            }
        }

        // Multipart / form-data
        runCatching {
            this@with.post("$HTTP_BIN_URL/post") {
                setBody(
                    MultiPartFormDataContent(
                        formData {
                            append("username", "ktor-monitor")
                            append("email", "demo@example.com")
                            append(
                                key = "notes",
                                value = TEXT_SAMPLE,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, "text/plain; charset=utf-8")
                                },
                            )
                            append(
                                key = "file",
                                value = TEXT_FILE_CONTENT_SAMPLE.encodeToByteArray(),
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, "text/plain")
                                    append(HttpHeaders.ContentDisposition, "filename=\"sample.txt\"")
                                },
                            )
                            append(
                                key = "logo",
                                value = SVG_SAMPLE,
                                headers = Headers.build {
                                    append(HttpHeaders.ContentType, "image/svg+xml")
                                    append(HttpHeaders.ContentDisposition, "filename=\"logo.svg\"")
                                },
                            )
                        }
                    )
                )
            }
        }
    }
}