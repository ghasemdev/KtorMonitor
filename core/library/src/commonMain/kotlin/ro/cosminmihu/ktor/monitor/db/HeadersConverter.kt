package ro.cosminmihu.ktor.monitor.db

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json

internal object HeadersConverter {

    private val json = Json { ignoreUnknownKeys = true }

    @ColumnTypeConverter
    fun fromJson(value: String): Map<String, List<String>> = try {
        json.decodeFromString(value)
    } catch (_: Exception) {
        emptyMap()
    }

    @ColumnTypeConverter
    fun toJson(value: Map<String, List<String>>): String =
        json.encodeToString(value)
}
