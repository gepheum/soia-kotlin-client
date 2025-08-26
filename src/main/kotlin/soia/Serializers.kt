package soia.internal.soia

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.source
import soia.JsonFlavor
import soia.Serializer
import soia.SerializerImpl
import java.io.InputStream
import java.io.OutputStream
import java.time.Instant

private fun decodeNumber(buffer: Buffer): Long {
    val wire = buffer.readByte().toInt() and 0xFF
    return when (wire) {
        in 0..231 -> wire.toLong()
        232 -> buffer.readShortLe().toLong() and 0xFFFF
        233 -> buffer.readIntLe().toLong() and 0xFFFFFFFFL
        234 -> buffer.readLongLe()
        235 -> (buffer.readByte().toInt() and 0xFF) - 256L
        236 -> (buffer.readShortLe().toInt() and 0xFFFF) - 65536L
        237 -> buffer.readIntLe().toLong()
        238 -> buffer.readLongLe()
        239 -> buffer.readLongLe()
        240 -> Float.fromBits(buffer.readIntLe()).toLong()
        241 -> Double.fromBits(buffer.readLongLe()).toLong()
        else -> wire.toLong()
    }
}

object Serializers {
    val bool: Serializer<Boolean> = Serializer(BoolSerializer)
    val int32: Serializer<Int> = Serializer(Int32Serializer)
    val int64: Serializer<Long> = Serializer(Int64Serializer)
    val uint64: Serializer<Long> = Serializer(Uint64Serializer)
    val float32: Serializer<Float> = Serializer(Float32Serializer)
    val float64: Serializer<Double> = Serializer(Float64Serializer)
    val string: Serializer<String> = Serializer(StringSerializer)
    val bytes: Serializer<ByteString> = Serializer(BytesSerializer)
    val timestamp: Serializer<Instant> = Serializer(TimestampSerializer)
}

private object BoolSerializer : SerializerImpl<Boolean> {
    override fun isDefault(value: Boolean): Boolean {
        return !value
    }

    override fun encode(
        input: Boolean,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        buffer.writeByte(if (input) 1 else 0)
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): Boolean {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        return buffer.readByte() == 1.toByte()
    }

    override fun toJson(
        input: Boolean,
        flavor: JsonFlavor,
    ): JsonElement {
        return when (flavor) {
            JsonFlavor.DENSE -> JsonPrimitive(if (input) 1 else 0)
            JsonFlavor.READABLE -> JsonPrimitive(input)
        }
    }

    override fun fromJson(json: JsonElement): Boolean {
        val primitive = json.jsonPrimitive
        return when {
            primitive.content == "true" -> true
            primitive.content == "false" -> false
            else -> primitive.content.toIntOrNull() != 0
        }
    }
}

private object Int32Serializer : SerializerImpl<Int> {
    override fun isDefault(value: Int): Boolean {
        return value == 0
    }

    override fun encode(
        input: Int,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        when {
            input < 0 -> {
                when {
                    input >= -256 -> {
                        buffer.writeByte(235)
                        buffer.writeByte((input + 256))
                    }
                    input >= -65536 -> {
                        buffer.writeByte(236)
                        buffer.writeShortLe((input + 65536))
                    }
                    else -> {
                        buffer.writeByte(237)
                        val clampedValue = if (input >= -2147483648) input else -2147483648
                        buffer.writeIntLe(clampedValue)
                    }
                }
            }
            input < 232 -> {
                buffer.writeByte(input)
            }
            input < 65536 -> {
                buffer.writeByte(232)
                buffer.writeShortLe(input)
            }
            else -> {
                buffer.writeByte(233)
                val clampedValue = if (input <= 2147483647) input else 2147483647
                buffer.writeIntLe(clampedValue)
            }
        }
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): Int {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        return decodeNumber(buffer).toInt()
    }

    override fun toJson(
        input: Int,
        flavor: JsonFlavor,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(json: JsonElement): Int {
        return json.jsonPrimitive.content.toIntOrNull() ?: 0
    }
}

private object Int64Serializer : SerializerImpl<Long> {
    override fun isDefault(value: Long): Boolean {
        return value == 0L
    }

    override fun encode(
        input: Long,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        buffer.writeLongLe(input)
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): Long {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        return buffer.readLongLe()
    }

    override fun toJson(
        input: Long,
        flavor: JsonFlavor,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(json: JsonElement): Long {
        return json.jsonPrimitive.content.toLong()
    }
}

private object Uint64Serializer : SerializerImpl<Long> {
    override fun isDefault(value: Long): Boolean {
        return value == 0L
    }

    override fun encode(
        input: Long,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        buffer.writeLongLe(input)
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): Long {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        return buffer.readLongLe()
    }

    override fun toJson(
        input: Long,
        flavor: JsonFlavor,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(json: JsonElement): Long {
        return json.jsonPrimitive.content.toLong()
    }
}

private object Float32Serializer : SerializerImpl<Float> {
    override fun isDefault(value: Float): Boolean {
        return value == 0.0f
    }

    override fun encode(
        input: Float,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        if (input == 0.0f) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(240)
            buffer.writeIntLe(input.toBits())
        }
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): Float {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 0) {
            0.0f
        } else {
            Float.fromBits(buffer.readIntLe())
        }
    }

    override fun toJson(
        input: Float,
        flavor: JsonFlavor,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(json: JsonElement): Float {
        val primitive = json.jsonPrimitive
        return when (primitive.content) {
            "NaN" -> Float.NaN
            "Infinity" -> Float.POSITIVE_INFINITY
            "-Infinity" -> Float.NEGATIVE_INFINITY
            else -> primitive.content.toFloatOrNull() ?: 0.0f
        }
    }
}

private object Float64Serializer : SerializerImpl<Double> {
    override fun isDefault(value: Double): Boolean {
        return value == 0.0
    }

    override fun encode(
        input: Double,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        if (input == 0.0) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(241)
            buffer.writeLongLe(input.toBits())
        }
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): Double {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 0) {
            0.0
        } else {
            Double.fromBits(buffer.readLongLe())
        }
    }

    override fun toJson(
        input: Double,
        flavor: JsonFlavor,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(json: JsonElement): Double {
        val primitive = json.jsonPrimitive
        return when (primitive.content) {
            "NaN" -> Double.NaN
            "Infinity" -> Double.POSITIVE_INFINITY
            "-Infinity" -> Double.NEGATIVE_INFINITY
            else -> primitive.content.toDoubleOrNull() ?: 0.0
        }
    }
}

private object StringSerializer : SerializerImpl<String> {
    override fun isDefault(value: String): Boolean {
        return value.isEmpty()
    }

    override fun encode(
        input: String,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        if (input.isEmpty()) {
            buffer.writeByte(242)
        } else {
            buffer.writeByte(243)
            val bytes = input.toByteArray(Charsets.UTF_8)
            val length = bytes.size
            // Encode length using the same scheme as int32
            when {
                length < 232 -> buffer.writeByte(length)
                length < 65536 -> {
                    buffer.writeByte(232)
                    buffer.writeShortLe(length)
                }
                else -> {
                    buffer.writeByte(233)
                    buffer.writeIntLe(length)
                }
            }
            buffer.write(bytes)
        }
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): String {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 242) {
            ""
        } else {
            // Should be wire 243
            val length = decodeNumber(buffer).toInt()
            val bytes = buffer.readByteArray(length.toLong())
            String(bytes, Charsets.UTF_8)
        }
    }

    override fun toJson(
        input: String,
        flavor: JsonFlavor,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(json: JsonElement): String {
        return json.jsonPrimitive.content
    }
}

private object BytesSerializer : SerializerImpl<ByteString> {
    override fun isDefault(value: ByteString): Boolean {
        return value.size == 0
    }

    override fun encode(
        input: ByteString,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        if (input.size == 0) {
            buffer.writeByte(244)
        } else {
            buffer.writeByte(245)
            val length = input.size
            // Encode length using the same scheme as int32
            when {
                length < 232 -> buffer.writeByte(length)
                length < 65536 -> {
                    buffer.writeByte(232)
                    buffer.writeShortLe(length)
                }
                else -> {
                    buffer.writeByte(233)
                    buffer.writeIntLe(length)
                }
            }
            buffer.write(input)
        }
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): ByteString {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 0 || wire == 244) {
            ByteString.EMPTY
        } else {
            // Should be wire 245
            val length = decodeNumber(buffer).toInt()
            buffer.readByteString(length.toLong())
        }
    }

    override fun toJson(
        input: ByteString,
        flavor: JsonFlavor,
    ): JsonElement {
        return when (flavor) {
            JsonFlavor.DENSE -> JsonPrimitive(input.base64())
            JsonFlavor.READABLE -> {
                JsonObject(
                    mapOf(
                        "base64" to JsonPrimitive(input.base64()),
                        "size" to JsonPrimitive(input.size),
                    ),
                )
            }
        }
    }

    override fun fromJson(json: JsonElement): ByteString {
        return when {
            json is JsonObject -> json["base64"]!!.jsonPrimitive.content.decodeBase64()!!
            else -> json.jsonPrimitive.content.decodeBase64()!!
        }
    }
}

private object TimestampSerializer : SerializerImpl<Instant> {
    override fun isDefault(value: Instant): Boolean {
        return value == Instant.EPOCH
    }

    override fun encode(
        input: Instant,
        stream: OutputStream,
    ) {
        val buffer = Buffer()
        val unixMillis = input.toEpochMilli()
        if (unixMillis == 0L) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(239)
            buffer.writeLongLe(unixMillis)
        }
        buffer.copyTo(stream)
    }

    override fun decode(stream: InputStream): Instant {
        val buffer = Buffer()
        buffer.writeAll(stream.source())
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 0) {
            Instant.EPOCH
        } else {
            // Should be wire 239
            val unixMillis = buffer.readLongLe()
            Instant.ofEpochMilli(unixMillis)
        }
    }

    override fun toJson(
        input: Instant,
        flavor: JsonFlavor,
    ): JsonElement {
        return when (flavor) {
            JsonFlavor.DENSE -> JsonPrimitive(input.toEpochMilli())
            JsonFlavor.READABLE -> {
                val unixMillis = input.toEpochMilli()
                JsonObject(
                    mapOf(
                        "unix_millis" to JsonPrimitive(unixMillis),
                        "formatted" to JsonPrimitive(input.toString()),
                    ),
                )
            }
        }
    }

    override fun fromJson(json: JsonElement): Instant {
        return when {
            json is JsonObject -> {
                val unixMillis = json["unix_millis"]!!.jsonPrimitive.content.toLong()
                Instant.ofEpochMilli(unixMillis)
            }
            else -> {
                val content = json.jsonPrimitive.content
                // Try to parse as ISO-8601 first, fall back to unix millis
                try {
                    if (content.contains('T') || content.contains('Z')) {
                        Instant.parse(content)
                    } else {
                        val unixMillis = content.toLong()
                        Instant.ofEpochMilli(unixMillis)
                    }
                } catch (e: Exception) {
                    // If parsing as ISO fails, try as unix millis
                    try {
                        val unixMillis = content.toLong()
                        Instant.ofEpochMilli(unixMillis)
                    } catch (e2: Exception) {
                        // If both fail, try parsing as ISO again (for better error message)
                        Instant.parse(content)
                    }
                }
            }
        }
    }
}
