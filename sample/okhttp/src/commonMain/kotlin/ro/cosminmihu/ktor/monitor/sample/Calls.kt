package ro.cosminmihu.ktor.monitor.sample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val HTTP_BIN_URL = "https://httpbin.org"

internal suspend fun samples() = withContext(Dispatchers.IO) {
    val client = httpClient()

    // HTTP Methods
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/get").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/post").post("".toRequestBody(null)).build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/put").put("".toRequestBody(null)).build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/delete").delete().build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/patch").patch("".toRequestBody(null)).build()).execute() }

    // Status codes
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/status/200").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/status/404").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/status/500").build()).execute() }

    // Request inspection
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/headers").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/ip").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/user-agent").build()).execute() }

    // Response formats
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/json").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/html").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/xml").build()).execute() }

    // Images
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/image/jpeg").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/image/png").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/image/svg").build()).execute() }

    // Dynamic data
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/uuid").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/bytes/1024").build()).execute() }
    runCatching { client.newCall(Request.Builder().url("$HTTP_BIN_URL/delay/1").build()).execute() }

    // Multipart / form-data
    runCatching {
        val multipart = MultipartBody.Builder()
            .setType(MultipartBody.FORM)
            .addFormDataPart("username", "ktor-monitor")
            .addFormDataPart("email", "demo@example.com")
            .addFormDataPart("notes", "Hello from OkHttp Monitor sample")
            .addFormDataPart(
                "file",
                "sample.txt",
                "Sample file content\nLine 2\nLine 3".toRequestBody("text/plain".toMediaType()),
            )
            .build()
        client.newCall(
            Request.Builder().url("$HTTP_BIN_URL/post").post(multipart).build()
        ).execute()
    }
}

