package ro.cosminmihu.ktor.monitor.sample

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.http4k.core.Method
import org.http4k.core.Request

private const val HTTP_BIN_URL = "https://httpbin.org"


internal suspend fun samples() = withContext(Dispatchers.IO) {
    val client = httpClient()

    // HTTP Methods
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/get")) }
    runCatching { client(Request(Method.POST, "$HTTP_BIN_URL/post").body("")) }
    runCatching { client(Request(Method.PUT, "$HTTP_BIN_URL/put").body("")) }
    runCatching { client(Request(Method.DELETE, "$HTTP_BIN_URL/delete")) }
    runCatching { client(Request(Method.PATCH, "$HTTP_BIN_URL/patch").body("")) }

    // Status codes
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/status/200")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/status/404")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/status/500")) }

    // Request inspection
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/headers")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/ip")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/user-agent")) }

    // Response formats
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/json")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/html")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/xml")) }

    // Images
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/image/jpeg")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/image/png")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/image/svg")) }

    // Dynamic data
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/uuid")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/bytes/1024")) }
    runCatching { client(Request(Method.GET, "$HTTP_BIN_URL/delay/1")) }

    // Form data (url-encoded)
    runCatching {
        client(
            Request(Method.POST, "$HTTP_BIN_URL/post")
                .header("Content-Type", "application/x-www-form-urlencoded")
                .body("username=ktor-monitor&email=demo%40example.com&notes=Hello+from+http4k+Monitor+sample")
        )
    }
}

