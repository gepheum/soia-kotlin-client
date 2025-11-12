package land.soia

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.double
import kotlinx.serialization.json.float
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import land.soia.internal.INDENT_UNIT
import land.soia.internal.SerializerImpl
import land.soia.internal.decodeNumber
import land.soia.internal.encodeInt32
import land.soia.internal.encodeLengthPrefix
import land.soia.internal.listSerializer
import land.soia.reflection.OptionalDescriptor
import land.soia.reflection.PrimitiveDescriptor
import land.soia.reflection.PrimitiveType
import land.soia.reflection.TypeDescriptor
import okio.Buffer
import okio.BufferedSource
import okio.ByteString
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.decodeHex
import java.time.Instant
import java.time.format.DateTimeFormatter

/**
 * Provides predefined serializers for all primitive types and utilities for creating
 * composite serializers such as optional and list serializers.
 *
 * This object serves as the main entry point for accessing serializers for basic types
 * like integers, strings, timestamps, etc., as well as for constructing more complex
 * serializers for optional values and collections.
 */
object Serializers {
    /** Serializer for Boolean values. */
    val bool: Serializer<Boolean> = Serializer(BoolSerializer)

    /** Serializer for 32-bit signed integers. */
    val int32: Serializer<Int> = Serializer(Int32Serializer)

    /** Serializer for 64-bit signed integers. */
    val int64: Serializer<Long> = Serializer(Int64Serializer)

    /** Serializer for 64-bit unsigned integers. */
    val uint64: Serializer<ULong> = Serializer(Uint64Serializer)

    /** Serializer for 32-bit floating-point numbers. */
    val float32: Serializer<Float> = Serializer(Float32Serializer)

    /** Serializer for 64-bit floating-point numbers. */
    val float64: Serializer<Double> = Serializer(Float64Serializer)

    /** Serializer for UTF-8 strings. */
    val string: Serializer<String> = Serializer(StringSerializer)

    /** Serializer for binary data (byte arrays). */
    val bytes: Serializer<ByteString> = Serializer(BytesSerializer)

    /** Serializer for timestamp values. */
    val timestamp: Serializer<Instant> = Serializer(TimestampSerializer)

    /**
     * Creates a serializer for optional values of type [T].
     *
     * @param other The serializer for the wrapped type
     * @return A serializer that can handle null values of the given type
     */
    fun <T> optional(other: Serializer<T>): Serializer<T?> {
        val otherImpl = other.impl
        return if (otherImpl is OptionalSerializer<*>) {
            @Suppress("UNCHECKED_CAST")
            other as Serializer<T?>
        } else {
            Serializer(OptionalSerializer(otherImpl))
        }
    }

    /**
     * Creates a serializer for lists of elements of type [E].
     *
     * @param item The serializer for individual list elements
     * @return A serializer that can handle lists of the given element type
     */
    fun <E> list(item: Serializer<E>): Serializer<List<E>> {
        return listSerializer(item)
    }
}

private abstract class PrimitiveSerializer<T> : SerializerImpl<T>() {
    abstract val typeName: String

    override val typeSignature: JsonElement get() =
        JsonObject(
            mapOf(
                "kind" to JsonPrimitive("primitive"),
                "value" to JsonPrimitive(typeName),
            ),
        )

    override fun addRecordDefinitionsTo(out: MutableMap<String, JsonElement>) {}
}

private object BoolSerializer : PrimitiveSerializer<Boolean>(), PrimitiveDescriptor {
    override fun isDefault(value: Boolean): Boolean {
        return !value
    }

    override fun encode(
        input: Boolean,
        buffer: Buffer,
    ) {
        buffer.writeByte(if (input) 1 else 0)
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Boolean {
        return decodeNumber(buffer).toInt() != 0
    }

    override fun appendString(
        input: Boolean,
        out: StringBuilder,
        eolIndent: String,
    ) {
        out.append(input)
    }

    override fun toJson(
        input: Boolean,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (readableFlavor) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive(if (input) 1 else 0)
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Boolean {
        val primitive = json.jsonPrimitive
        return when (primitive.content) {
            "0" -> false
            "0.0" -> false
            "false" -> false
            else -> true
        }
    }

    override val typeName get() = "bool"
    override val primitiveType get() = PrimitiveType.BOOL
    override val typeDescriptor get() = this
}

private object Int32Serializer : PrimitiveSerializer<Int>(), PrimitiveDescriptor {
    override fun isDefault(value: Int): Boolean {
        return value == 0
    }

    override fun encode(
        input: Int,
        buffer: Buffer,
    ) {
        encodeInt32(input, buffer)
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Int {
        return decodeNumber(buffer).toInt()
    }

    override fun toJson(
        input: Int,
        readableFlavor: Boolean,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Int {
        return json.jsonPrimitive.content.toInt()
    }

    override fun appendString(
        input: Int,
        out: StringBuilder,
        eolIndent: String,
    ) {
        out.append(input)
    }

    override val typeName get() = "int32"
    override val primitiveType get() = PrimitiveType.INT_32
    override val typeDescriptor get() = this
}

private const val MIN_SAFE_JAVASCRIPT_INT = -9007199254740991 // -(2 ^ 53 - 1)
private const val MAX_SAFE_JAVASCRIPT_INT = 9007199254740991 // 2 ^ 53 - 1

private object Int64Serializer : PrimitiveSerializer<Long>(), PrimitiveDescriptor {
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

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Long {
        return decodeNumber(buffer).toLong()
    }

    override fun toJson(
        input: Long,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input in MIN_SAFE_JAVASCRIPT_INT..MAX_SAFE_JAVASCRIPT_INT) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive("$input")
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Long {
        return json.jsonPrimitive.content.toLong()
    }

    override fun appendString(
        input: Long,
        out: StringBuilder,
        eolIndent: String,
    ) {
        out.append(input).append('L')
    }

    override val typeName get() = "int64"
    override val primitiveType get() = PrimitiveType.INT_64
    override val typeDescriptor get() = this
}

private object Uint64Serializer : PrimitiveSerializer<ULong>(), PrimitiveDescriptor {
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

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): ULong {
        return decodeNumber(buffer).toLong().toULong()
    }

    override fun toJson(
        input: ULong,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input <= MAX_SAFE_JAVASCRIPT_INT.toULong()) {
            JsonPrimitive(input.toLong())
        } else {
            JsonPrimitive("$input")
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): ULong {
        return json.jsonPrimitive.content.toULong()
    }

    override fun appendString(
        input: ULong,
        out: StringBuilder,
        eolIndent: String,
    ) {
        out.append(input).append("UL")
    }

    override val typeName get() = "uint64"
    override val primitiveType get() = PrimitiveType.UINT_64
    override val typeDescriptor get() = this
}

private object Float32Serializer : PrimitiveSerializer<Float>(), PrimitiveDescriptor {
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

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Float {
        return decodeNumber(buffer).toFloat()
    }

    override fun toJson(
        input: Float,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input.isFinite()) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive(input.toString())
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Float {
        val primitive = json.jsonPrimitive
        return if (primitive.isString) {
            primitive.content.toFloat()
        } else {
            primitive.float
        }
    }

    override fun appendString(
        input: Float,
        out: StringBuilder,
        eolIndent: String,
    ) {
        if (input.isFinite()) {
            out.append(input).append('F')
        } else if (input == Float.NEGATIVE_INFINITY) {
            out.append("Float.NEGATIVE_INFINITY")
        } else if (input == Float.POSITIVE_INFINITY) {
            out.append("Float.POSITIVE_INFINITY")
        } else {
            out.append("Float.NaN")
        }
    }

    override val typeName get() = "float32"
    override val primitiveType get() = PrimitiveType.FLOAT_32
    override val typeDescriptor get() = this
}

private object Float64Serializer : PrimitiveSerializer<Double>(), PrimitiveDescriptor {
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

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Double {
        return decodeNumber(buffer).toDouble()
    }

    override fun toJson(
        input: Double,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input.isFinite()) {
            JsonPrimitive(input)
        } else {
            JsonPrimitive(input.toString())
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Double {
        val primitive = json.jsonPrimitive
        return if (primitive.isString) {
            primitive.content.toDouble()
        } else {
            primitive.double
        }
    }

    override fun appendString(
        input: Double,
        out: StringBuilder,
        eolIndent: String,
    ) {
        if (input.isFinite()) {
            out.append(input)
        } else if (input == Double.NEGATIVE_INFINITY) {
            out.append("Double.NEGATIVE_INFINITY")
        } else if (input == Double.POSITIVE_INFINITY) {
            out.append("Double.POSITIVE_INFINITY")
        } else {
            out.append("Double.NaN")
        }
    }

    override val typeName get() = "float64"
    override val primitiveType get() = PrimitiveType.FLOAT_64
    override val typeDescriptor get() = this
}

private object StringSerializer : PrimitiveSerializer<String>(), PrimitiveDescriptor {
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

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): String {
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 0 || wire == 242) {
            ""
        } else {
            // Should be wire 243
            val length = decodeNumber(buffer)
            val bytes = buffer.readByteArray(length.toLong())
            String(bytes, Charsets.UTF_8)
        }
    }

    override fun toJson(
        input: String,
        readableFlavor: Boolean,
    ): JsonElement {
        return JsonPrimitive(input)
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): String {
        val jsonPrimitive = json.jsonPrimitive
        return if (jsonPrimitive.isString) {
            jsonPrimitive.content
        } else if (jsonPrimitive.intOrNull == 0) {
            ""
        } else {
            throw IllegalArgumentException("Expected: string")
        }
    }

    override fun appendString(
        input: String,
        out: StringBuilder,
        eolIndent: String,
    ) {
        // Appends a quoted string which can be copied to a Kotlin source file.
        out.append('"')
        for (i in 0 until input.length) {
            when (val char = input[i]) {
                '\\' -> out.append("\\\\")
                '"' -> out.append("\\\"")
                '\n' -> {
                    out.append("\\n")
                    if (i < input.length - 1) {
                        out.append("\" +${eolIndent}${INDENT_UNIT}\"")
                    }
                }
                '\r' -> out.append("\\r")
                '\t' -> out.append("\\t")
                '\b' -> out.append("\\b")
                '\u000C' -> out.append("\\f")
                '$' -> out.append("\\$")
                else -> {
                    when {
                        // Handle all control characters (0x00-0x1F and 0x7F-0x9F)
                        char.code < 32 || char.code in 127..159 -> {
                            out.append("\\u${char.code.toString(16).padStart(4, '0')}")
                        }
                        // Handle other non-printable Unicode characters
                        !char.isDefined() || char.isISOControl() -> {
                            out.append("\\u${char.code.toString(16).padStart(4, '0')}")
                        }
                        // Regular printable character
                        else -> out.append(char)
                    }
                }
            }
        }
        out.append('"')
    }

    override val typeName get() = "string"
    override val primitiveType get() = PrimitiveType.STRING
    override val typeDescriptor get() = this
}

private object BytesSerializer : PrimitiveSerializer<ByteString>(), PrimitiveDescriptor {
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

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): ByteString {
        val wire = buffer.readByte().toInt() and 0xFF
        return if (wire == 0 || wire == 244) {
            ByteString.EMPTY
        } else {
            // Should be wire 245
            val length = decodeNumber(buffer)
            buffer.readByteString(length.toLong())
        }
    }

    override fun appendString(
        input: ByteString,
        out: StringBuilder,
        eolIndent: String,
    ) {
        out.append('"').append(input.hex()).append("\".decodeHex()")
    }

    override fun toJson(
        input: ByteString,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (readableFlavor) {
            JsonPrimitive("hex:${input.hex()}")
        } else {
            JsonPrimitive(input.base64())
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): ByteString {
        val jsonPrimitive = json.jsonPrimitive
        return if (jsonPrimitive.isString) {
            val content = jsonPrimitive.content
            if (content.startsWith("hex:")) {
                content.substring(4).decodeHex()
            } else {
                content.decodeBase64()!!
            }
        } else if (jsonPrimitive.intOrNull == 0) {
            ByteString.EMPTY
        } else {
            throw IllegalArgumentException("Expected: base64 or hex string")
        }
    }

    override val typeName get() = "bytes"
    override val primitiveType get() = PrimitiveType.BYTES
    override val typeDescriptor get() = this
}

private object TimestampSerializer : PrimitiveSerializer<Instant>(), PrimitiveDescriptor {
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

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Instant {
        val unixMillis = clampUnixMillis(decodeNumber(buffer).toLong())
        return Instant.ofEpochMilli(unixMillis)
    }

    override fun toJson(
        input: Instant,
        readableFlavor: Boolean,
    ): JsonElement {
        val unixMillis = clampUnixMillis(input.toEpochMilli())
        return if (readableFlavor) {
            val clampedInstant = Instant.ofEpochMilli(unixMillis)
            JsonObject(
                mapOf(
                    "unix_millis" to JsonPrimitive(unixMillis),
                    "formatted" to JsonPrimitive(DateTimeFormatter.ISO_INSTANT.format(clampedInstant)),
                ),
            )
        } else {
            JsonPrimitive(unixMillis)
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Instant {
        val unixMillisElement = if (json is JsonObject) json["unix_millis"]!! else json
        val unixMillis = clampUnixMillis(unixMillisElement.jsonPrimitive.content.toLong())
        return Instant.ofEpochMilli(unixMillis)
    }

    override fun appendString(
        input: Instant,
        out: StringBuilder,
        eolIndent: String,
    ) {
        out
            .append("Instant.ofEpochMillis(")
            .append(eolIndent)
            .append(INDENT_UNIT)
            .append("// ")
            .append(DateTimeFormatter.ISO_INSTANT.format(input))
            .append(eolIndent)
            .append(INDENT_UNIT)
            .append(input.toEpochMilli())
            .append("L")
            .append(eolIndent)
            .append(')')
    }

    fun clampUnixMillis(unixMillis: Long): Long {
        return unixMillis.coerceIn(-8640000000000000, 8640000000000000)
    }

    override val typeName get() = "timestamp"
    override val primitiveType get() = PrimitiveType.TIMESTAMP
    override val typeDescriptor get() = this
}

private class OptionalSerializer<T>(val other: SerializerImpl<T>) : SerializerImpl<T?>(), OptionalDescriptor.Reflective {
    override fun isDefault(value: T?): Boolean {
        return value == null
    }

    override fun encode(
        input: T?,
        buffer: Buffer,
    ) {
        if (input == null) {
            buffer.writeByte(255)
        } else {
            this.other.encode(input, buffer)
        }
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): T? {
        return if (buffer.peek().readByte().toInt() and 0xFF == 255) {
            buffer.skip(1)
            null
        } else {
            this.other.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
        }
    }

    override fun appendString(
        input: T?,
        out: StringBuilder,
        eolIndent: String,
    ) {
        if (input == null) {
            out.append("null")
        } else {
            this.other.appendString(input, out, eolIndent)
        }
    }

    override fun toJson(
        input: T?,
        readableFlavor: Boolean,
    ): JsonElement {
        return if (input == null) {
            JsonNull
        } else {
            this.other.toJson(input, readableFlavor = readableFlavor)
        }
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): T? {
        return if (json is JsonNull) {
            null
        } else {
            this.other.fromJson(json, keepUnrecognizedFields = keepUnrecognizedFields)
        }
    }

    override val otherType: TypeDescriptor.Reflective
        get() = other.typeDescriptor

    override val typeSignature: JsonElement
        get() =
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("optional"),
                    "value" to other.typeSignature,
                ),
            )

    override fun addRecordDefinitionsTo(out: MutableMap<String, JsonElement>) {
        other.addRecordDefinitionsTo(out)
    }

    override val typeDescriptor get() = this
}
