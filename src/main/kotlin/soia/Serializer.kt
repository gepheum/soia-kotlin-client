package soia

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import okio.Buffer
import okio.ByteString

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
                Json.Default.encodeToString(JsonElement.serializer(), jsonElement)
            JsonFlavor.READABLE ->
                readableJson.encodeToString(JsonElement.serializer(), jsonElement)
        }
    }

    fun fromJson(json: JsonElement): T {
        return this.impl.fromJson(json)
    }

    fun fromJsonCode(jsonCode: String): T {
        val jsonElement = Json.Default.decodeFromString(JsonElement.serializer(), jsonCode)
        return this.impl.fromJson(jsonElement)
    }

    fun toBytes(input: T): ByteString {
        val buffer = Buffer()
        buffer.writeUtf8("soia")
        this.impl.encode(input, buffer)
        return buffer.readByteString()
    }

    fun fromBytes(bytes: ByteArray): T {
        val buffer = Buffer()
        buffer.write(bytes)
        return this.fromBytes(buffer)
    }

    fun fromBytes(bytes: ByteString): T {
        val buffer = Buffer()
        buffer.write(bytes)
        return this.fromBytes(buffer)
    }

    private fun fromBytes(buffer: Buffer): T {
        return if (buffer.readByte().toInt() == 's'.code &&
            buffer.readByte().toInt() == 'o'.code &&
            buffer.readByte().toInt() == 'i'.code &&
            buffer.readByte().toInt() == 'a'.code
        ) {
            val result = this.impl.decode(buffer)
            if (!buffer.exhausted()) {
                throw IllegalArgumentException("Extra bytes after deserialization")
            }
            result
        } else {
            this.fromJsonCode(buffer.readUtf8())
        }
    }

    companion object {
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val readableJson =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
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
        buffer: Buffer,
    )

    fun decode(buffer: Buffer): T
}
