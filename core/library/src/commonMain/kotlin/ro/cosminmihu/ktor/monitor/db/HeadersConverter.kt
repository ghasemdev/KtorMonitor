package ro.cosminmihu.ktor.monitor.db

import androidx.room3.ColumnTypeConverter
import kotlinx.serialization.json.Json

internal object HeadersConverter {

    @ColumnTypeConverter
    fun fromJson(value: String): Map<String, List<String>> =
        Json.decodeFromString(value)

    @ColumnTypeConverter
    fun toJson(value: Map<String, List<String>>): String =
        Json.encodeToString(value)
}
