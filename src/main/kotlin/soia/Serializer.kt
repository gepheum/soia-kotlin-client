package soia

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.Buffer
import okio.ByteString
import java.io.InputStream
import java.io.OutputStream

private val jsonInstance = Json {
    // TODO: rm
    allowSpecialFloatingPointValues = true
}

enum class JsonFlavor {
    DENSE,
    READABLE,
}

class Serializer<T> internal constructor(
    private val impl: SerializerImpl<T>,
) {
    fun toJson(
        input: T,
        flavor: JsonFlavor = JsonFlavor.DENSE,
    ): JsonElement {
        return this.impl.toJson(input, flavor)
    }

    fun toJsonCode(
        input: T,
        flavor: JsonFlavor = JsonFlavor.DENSE,
    ): String {
        val jsonElement = this.impl.toJson(input, flavor)
        return when (flavor) {
            JsonFlavor.DENSE ->
                jsonInstance.encodeToString(JsonElement.serializer(), jsonElement)
            JsonFlavor.READABLE ->
                Json {
                    prettyPrint = true
                    prettyPrintIndent = "  "
                }.encodeToString(JsonElement.serializer(), jsonElement)
        }
    }

    fun fromJson(json: JsonElement): T {
        return this.impl.fromJson(json)
    }

    fun fromJsonCode(jsonCode: String): T {
        val jsonElement = jsonInstance.decodeFromString(JsonElement.serializer(), jsonCode)
        return this.impl.fromJson(jsonElement)
    }

    fun toBytes(input: T): ByteString {
        val buffer = Buffer()
        this.impl.encode(input, buffer.outputStream())
        return buffer.readByteString()
    }

    fun fromBytes(stream: InputStream): T {
        return this.impl.decode(stream)
    }
}

internal interface SerializerImpl<T> {
    fun isDefault(value: T): Boolean

    fun toJson(
        input: T,
        flavor: JsonFlavor,
    ): JsonElement

    fun fromJson(json: JsonElement): T

    fun encode(
        input: T,
        stream: OutputStream,
    ): Unit

    fun decode(stream: InputStream): T
}
