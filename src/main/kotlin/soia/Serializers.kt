// TODO: throw exception when parsing bytes/JSON and invalid

package soia

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import java.time.Instant

object Serializers {
    val bool: Serializer<Boolean> = Serializer(BoolSerializer)
    val int32: Serializer<Int> = Serializer(Int32Serializer)
    val int64: Serializer<Long> = Serializer(Int64Serializer)
    val uint64: Serializer<ULong> = Serializer(Uint64Serializer)
    val float32: Serializer<Float> = Serializer(Float32Serializer)
    val float64: Serializer<Double> = Serializer(Float64Serializer)
    val string: Serializer<String> = Serializer(StringSerializer)
    val bytes: Serializer<ByteString> = Serializer(BytesSerializer)
    val timestamp: Serializer<Instant> = Serializer(TimestampSerializer)
}

private fun decodeNumber(buffer: Buffer): Number {
    return when (val wire = buffer.readByte().toInt() and 0xFF) {
        in 0..231 -> wire.toLong()
        232 -> buffer.readShortLe().toInt() and 0xFFFF // uint16
        233 -> buffer.readIntLe().toLong() and 0xFFFFFFFF // uint32
        234 -> buffer.readLongLe() // uint64
        235 -> (buffer.readByte().toInt() and 0xFF) - 256L
        236 -> (buffer.readShortLe().toInt() and 0xFFFF) - 65536L
        237 -> buffer.readIntLe()
        238 -> buffer.readLongLe()
        239 -> buffer.readLongLe()
        240 -> Float.fromBits(buffer.readIntLe())
        241 -> Double.fromBits(buffer.readLongLe())
        else -> wire
    }
}

private fun encodeLengthPrefix(
    length: Int,
    buffer: Buffer,
) {
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
}

private object BoolSerializer : SerializerImpl<Boolean> {
    override fun isDefault(value: Boolean): Boolean {
        return !value
    }

    override fun encode(
        input: Boolean,
        buffer: Buffer,
    ) {
        buffer.writeByte(if (input) 1 else 0)
    }

    override fun decode(buffer: Buffer): Boolean {
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
        return when (primitive.content) {
            "0" -> false
            "false" -> false
            else -> true
        }
    }
}

private object Int32Serializer : SerializerImpl<Int> {
    override fun isDefault(value: Int): Boolean {
        return value == 0
    }

    override fun encode(
        input: Int,
        buffer: Buffer,
    ) {
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
                        buffer.writeIntLe(input)
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
                buffer.writeIntLe(input)
            }
        }
    }

    override fun decode(buffer: Buffer): Int {
        return decodeNumber(buffer).toInt()
    }

    override fun toJson(
        input: Int,
        flavor: JsonFlavor,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(json: JsonElement): Int {
        return json.jsonPrimitive.content.toInt()
    }
}

private const val MIN_SAFE_JAVASCRIPT_INT = -9007199254740992; // -(2 ^ 53)
private const val MAX_SAFE_JAVASCRIPT_INT = 9007199254740992; // -(2 ^ 53)

private object Int64Serializer : SerializerImpl<Long> {
    override fun isDefault(value: Long): Boolean {
        return value == 0L
    }

    override fun encode(
        input: Long,
        buffer: Buffer,
    ) {
        if (input in -2147483648..2147483647) {
            Int32Serializer.encode(input.toInt(), buffer)
        } else {
            buffer.writeByte(238)
            buffer.writeLongLe(input)
        }
    }

    override fun decode(buffer: Buffer): Long {
        return decodeNumber(buffer).toLong()
    }

    override fun toJson(
        input: Long,
        flavor: JsonFlavor,
    ): JsonElement {
        return if (input in MIN_SAFE_JAVASCRIPT_INT..MAX_SAFE_JAVASCRIPT_INT) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive("$input")
        }
    }

    override fun fromJson(json: JsonElement): Long {
        return json.jsonPrimitive.content.toLong()
    }
}

private object Uint64Serializer : SerializerImpl<ULong> {
    override fun isDefault(value: ULong): Boolean {
        return value == 0UL
    }

    override fun encode(
        input: ULong,
        buffer: Buffer,
    ) {
        when {
            input < 232UL -> {
                buffer.writeByte(input.toInt())
            }
            input < 4294967296UL -> {
                if (input < 65536UL) {
                    buffer.writeByte(232)
                    buffer.writeShortLe(input.toInt())
                } else {
                    buffer.writeByte(233)
                    buffer.writeIntLe(input.toInt())
                }
            }
            else -> {
                buffer.writeByte(234)
                buffer.writeLongLe(input.toLong())
            }
        }
    }

    override fun decode(buffer: Buffer): ULong {
        return decodeNumber(buffer).toLong().toULong()
    }

    override fun toJson(
        input: ULong,
        flavor: JsonFlavor,
    ): JsonElement {
        return if (input <= MAX_SAFE_JAVASCRIPT_INT.toULong()) {
            JsonPrimitive(input.toLong())
        } else {
            JsonPrimitive("$input")
        }
    }

    override fun fromJson(json: JsonElement): ULong {
        return json.jsonPrimitive.content.toULong()
    }
}

private object Float32Serializer : SerializerImpl<Float> {
    override fun isDefault(value: Float): Boolean {
        return value == 0.0f
    }

    override fun encode(
        input: Float,
        buffer: Buffer,
    ) {
        if (input == 0.0f) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(240)
            buffer.writeIntLe(input.toBits())
        }
    }

    override fun decode(buffer: Buffer): Float {
        return decodeNumber(buffer).toFloat()
    }

    override fun toJson(
        input: Float,
        flavor: JsonFlavor,
    ): JsonElement {
        return if (input.isFinite()) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive(input.toString())
        }
    }

    override fun fromJson(json: JsonElement): Float {
        val primitive = json.jsonPrimitive
        return if (primitive.isString) {
            primitive.content.toFloat()
        } else {
            primitive.float
        }
    }
}

private object Float64Serializer : SerializerImpl<Double> {
    override fun isDefault(value: Double): Boolean {
        return value == 0.0
    }

    override fun encode(
        input: Double,
        buffer: Buffer,
    ) {
        if (input == 0.0) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(241)
            buffer.writeLongLe(input.toBits())
        }
    }

    override fun decode(buffer: Buffer): Double {
        return decodeNumber(buffer).toDouble()
    }

    override fun toJson(
        input: Double,
        flavor: JsonFlavor,
    ): JsonElement {
        return if (input.isFinite()) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive(input.toString())
        }
    }

    override fun fromJson(json: JsonElement): Double {
        val primitive = json.jsonPrimitive
        return if (primitive.isString) {
            primitive.content.toDouble()
        } else {
            primitive.double
        }
    }
}

private object StringSerializer : SerializerImpl<String> {
    override fun isDefault(value: String): Boolean {
        return value.isEmpty()
    }

    override fun encode(
        input: String,
        buffer: Buffer,
    ) {
        if (input.isEmpty()) {
            buffer.writeByte(242)
        } else {
            buffer.writeByte(243)
            val bytes = input.toByteArray(Charsets.UTF_8)
            val length = bytes.size
            encodeLengthPrefix(length, buffer)
            buffer.write(bytes)
        }
    }

    override fun decode(buffer: Buffer): String {
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
        val jsonPrimitive = json.jsonPrimitive
        return if ((0) == jsonPrimitive.intOrNull) {
            ""
        } else {
            jsonPrimitive.content
        }
    }
}

private object BytesSerializer : SerializerImpl<ByteString> {
    override fun isDefault(value: ByteString): Boolean {
        return value.size == 0
    }

    override fun encode(
        input: ByteString,
        buffer: Buffer,
    ) {
        if (input.size == 0) {
            buffer.writeByte(244)
        } else {
            buffer.writeByte(245)
            val length = input.size
            encodeLengthPrefix(length, buffer)
            buffer.write(input)
        }
    }

    override fun decode(buffer: Buffer): ByteString {
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
        return JsonPrimitive(input.base64())
    }

    override fun fromJson(json: JsonElement): ByteString {
        val jsonPrimitive = json.jsonPrimitive
        return if ((0) == jsonPrimitive.intOrNull) {
            ByteString.EMPTY
        } else {
            jsonPrimitive.content.decodeBase64()!!
        }
    }
}

private object TimestampSerializer : SerializerImpl<Instant> {
    override fun isDefault(value: Instant): Boolean {
        return value == Instant.EPOCH
    }

    override fun encode(
        input: Instant,
        buffer: Buffer,
    ) {
        val unixMillis = clampUnixMillis(input.toEpochMilli())
        if (unixMillis == 0L) {
            buffer.writeByte(0)
        } else {
            buffer.writeByte(239)
            buffer.writeLongLe(unixMillis)
        }
    }

    override fun decode(buffer: Buffer): Instant {
        val wire = buffer.readByte().toInt()
        return if (wire == 0) {
            Instant.EPOCH
        } else {
            // Should be wire 239
            val unixMillis = clampUnixMillis(buffer.readLongLe())
            Instant.ofEpochMilli(unixMillis)
        }
    }

    override fun toJson(
        input: Instant,
        flavor: JsonFlavor,
    ): JsonElement {
        val unixMillis = clampUnixMillis(input.toEpochMilli())
        return when (flavor) {
            JsonFlavor.DENSE -> JsonPrimitive(unixMillis)
            JsonFlavor.READABLE -> {
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
                val unixMillis = clampUnixMillis(json["unix_millis"]!!.jsonPrimitive.content.toLong())
                Instant.ofEpochMilli(unixMillis)
            }
            else -> {
                val content = json.jsonPrimitive.content
                // Try to parse as ISO-8601 first, fall back to unix millis
                try {
                    if (content.contains('T') || content.contains('Z')) {
                        Instant.parse(content)
                    } else {
                        val unixMillis = clampUnixMillis(content.toLong())
                        Instant.ofEpochMilli(unixMillis)
                    }
                } catch (e: Exception) {
                    // If parsing as ISO fails, try as unix millis
                    try {
                        val unixMillis = clampUnixMillis(content.toLong())
                        Instant.ofEpochMilli(unixMillis)
                    } catch (e2: Exception) {
                        // If both fail, try parsing as ISO again (for better error message)
                        Instant.parse(content)
                    }
                }
            }
        }
    }

    fun clampUnixMillis(unixMillis: Long): Long {
        return unixMillis.coerceIn(-8640000000000000, 8640000000000000)
    }
}
