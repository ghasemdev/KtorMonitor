package ro.cosminmihu.ktor.monitor.sample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

private const val HTTP_BIN_URL = "https://httpbin.org"

/** 1x1 transparent PNG (67 bytes) used as a tiny multipart image attachment. */
@OptIn(kotlin.ExperimentalUnsignedTypes::class)
private val PNG_1X1: ByteArray = ubyteArrayOf(
    0x89u, 0x50u, 0x4Eu, 0x47u, 0x0Du, 0x0Au, 0x1Au, 0x0Au,
    0x00u, 0x00u, 0x00u, 0x0Du, 0x49u, 0x48u, 0x44u, 0x52u,
    0x00u, 0x00u, 0x00u, 0x01u, 0x00u, 0x00u, 0x00u, 0x01u,
    0x08u, 0x06u, 0x00u, 0x00u, 0x00u, 0x1Fu, 0x15u, 0xC4u,
    0x89u, 0x00u, 0x00u, 0x00u, 0x0Du, 0x49u, 0x44u, 0x41u,
    0x54u, 0x78u, 0x9Cu, 0x63u, 0x00u, 0x01u, 0x00u, 0x00u,
    0x05u, 0x00u, 0x01u, 0x0Du, 0x0Au, 0x2Du, 0xB4u, 0x00u,
    0x00u, 0x00u, 0x00u, 0x49u, 0x45u, 0x4Eu, 0x44u, 0xAEu,
    0x42u, 0x60u, 0x82u,
).toByteArray()

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
            .addFormDataPart(
                "avatar",
                "pixel.png",
                PNG_1X1.toRequestBody("image/png".toMediaType()),
            )
            .build()
        client.newCall(
            Request.Builder().url("$HTTP_BIN_URL/post").post(multipart).build()
        ).execute()
    }
}

