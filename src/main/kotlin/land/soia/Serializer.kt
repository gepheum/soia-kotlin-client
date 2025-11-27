package land.soia

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import land.soia.internal.SerializerImpl
import land.soia.internal.formatDenseJson
import land.soia.internal.formatReadableJson
import land.soia.reflection.TypeDescriptor
import okio.Buffer
import okio.ByteString

/** When serializing a value to JSON, you can choose one of two flavors. */
enum class JsonFlavor {
    /**
     * Structs are serialized as JSON arrays, where the field numbers in the index
     * definition match the indexes in the array. Enum constants are serialized as
     * numbers.
     * This is the serialization format you should choose in most cases.
     * It is also the default.
     */
    DENSE,

    /**
     * Structs are serialized as JSON objects, and enum constants are serialized as
     * strings.
     * This format is more verbose and readable, but it should not be used if you need
     * persistence, because soia allows fields to be renamed in record definitions. In
     * other words, never store a readable JSON on disk or in a database.
     */
    READABLE,
}

/**
 * What to do with unrecognized fields when deserializing a value from dense JSON or
 * binary data.
 * Pick [KEEP] if the input JSON or binary string comes from a trusted program which
 * might have been built from more recent source files.
 * Always pick [DROP] if the input JSON or binary string might come from a malicious user.
 */
enum class UnrecognizedFieldsPolicy {
    /**
     * Unrecognized fields found when deserializing a value are dropped.
     * Pick this option if the input JSON or binary string might come from a malicious
     * user.
     */
    DROP,

    /**
     * Unrecognized fields found when deserializing a value from dense JSON or binary
     * data are saved.
     * If the value is later re-serialized in the same format (dense JSON or binary),
     * the unrecognized fields will be present in the serialized form.
     */
    KEEP,
}

/**
 * Converts objects of type [T] to and from JSON or binary format.
 *
 * @param T The type of objects this serializer can handle
 * @property typeDescriptor
 */
class Serializer<T> internal constructor(
    internal val impl: SerializerImpl<T>,
) {
    /**
     * Converts an object to its stringified JSON representation.
     * Uses dense JSON flavor.
     *
     * @param input The object to serialize
     * @return The stringified dense JSON representation
     */
    fun toJsonCode(input: T): String {
        return toJsonCode(input, JsonFlavor.DENSE)
    }

    /**
     * Converts an object to its stringified JSON representation.
     *
     * @param input The object to serialize
     * @param flavor Whether to use dense (default) or readable flavor
     * @return The stringified JSON representation
     */
    fun toJsonCode(
        input: T,
        flavor: JsonFlavor,
    ): String {
        val readableFlavor = flavor == JsonFlavor.READABLE
        val jsonElement = this.impl.toJson(input, readableFlavor = readableFlavor)
        return if (readableFlavor) {
            formatReadableJson(jsonElement)
        } else {
            formatDenseJson(jsonElement)
        }
    }

    /**
     * Converts an object to its JSON representation.
     * Uses dense JSON flavor.
     * If you just need the stringified JSON, call [toJsonCode] instead.
     *
     * @param input The object to serialize
     * @return The JSON representation as a JsonElement
     */
    fun toJson(input: T): JsonElement {
        return toJson(input, JsonFlavor.DENSE)
    }

    /**
     * Converts an object to its JSON representation.
     * If you just need the stringified JSON, call [toJsonCode] instead.
     *
     * @param input The object to serialize
     * @param flavor Whether to use dense (default) or readable flavor
     * @return The JSON representation as a JsonElement
     */
    fun toJson(
        input: T,
        flavor: JsonFlavor,
    ): JsonElement {
        return this.impl.toJson(input, readableFlavor = flavor == JsonFlavor.READABLE)
    }

    /**
     * Deserializes an object from its JSON representation.
     * Works with both dense and readable JSON flavors.
     * Unrecognized fields are dropped.
     *
     * @param jsonCode The stringified JSON to deserialize
     * @return The deserialized object
     */
    fun fromJsonCode(jsonCode: String): T {
        return fromJsonCode(jsonCode, UnrecognizedFieldsPolicy.DROP)
    }

    /**
     * Deserializes an object from its JSON representation.
     * Works with both dense and readable JSON flavors.
     *
     * @param jsonCode The stringified JSON to deserialize
     * @param unrecognizedFields Whether to keep or drop unrecognized fields
     * @return The deserialized object
     */
    fun fromJsonCode(
        jsonCode: String,
        unrecognizedFields: UnrecognizedFieldsPolicy,
    ): T {
        val keepUnrecognizedFields = unrecognizedFields == UnrecognizedFieldsPolicy.KEEP
        val jsonElement = Json.Default.decodeFromString(JsonElement.serializer(), jsonCode)
        return this.impl.fromJson(jsonElement, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    /**
     * Deserializes an object from its JSON representation.
     * Works with both dense and readable JSON flavors.
     * Unrecognized fields are dropped.
     *
     * @param json The JSON element to deserialize
     * @return The deserialized object
     */
    fun fromJson(json: JsonElement): T {
        return fromJson(json, UnrecognizedFieldsPolicy.DROP)
    }

    /**
     * Deserializes an object from its JSON representation.
     * Works with both dense and readable JSON flavors.
     *
     * @param json The JSON element to deserialize
     * @param unrecognizedFields Whether to keep or drop unrecognized fields
     * @return The deserialized object
     */
    fun fromJson(
        json: JsonElement,
        unrecognizedFields: UnrecognizedFieldsPolicy,
    ): T {
        val keepUnrecognizedFields = unrecognizedFields == UnrecognizedFieldsPolicy.KEEP
        return this.impl.fromJson(json, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    /**
     * Converts an object to its binary representation.
     *
     * @param input The object to serialize
     * @return The binary representation as a ByteString
     */
    fun toBytes(input: T): ByteString {
        val buffer = Buffer()
        buffer.writeUtf8("soia")
        this.impl.encode(input, buffer)
        return buffer.readByteString()
    }

    /**
     * Deserializes an object from its binary representation.
     * Unrecognized fields are dropped.
     *
     * @param bytes The byte array containing the serialized data
     * @return The deserialized object
     */
    fun fromBytes(bytes: ByteArray): T {
        return fromBytes(bytes, UnrecognizedFieldsPolicy.DROP)
    }

    /**
     * Deserializes an object from its binary representation.
     *
     * @param bytes The byte array containing the serialized data
     * @param unrecognizedFields Whether to keep or drop unrecognized fields
     * @return The deserialized object
     */
    fun fromBytes(
        bytes: ByteArray,
        unrecognizedFields: UnrecognizedFieldsPolicy,
    ): T {
        val buffer = Buffer()
        buffer.write(bytes)
        return this.fromBytes(buffer, unrecognizedFields)
    }

    /**
     * Deserializes an object from its binary representation.
     * Unrecognized fields are dropped.
     *
     * @param bytes The byte string containing the serialized data
     * @return The deserialized object
     */
    fun fromBytes(bytes: ByteString): T {
        return fromBytes(bytes, UnrecognizedFieldsPolicy.DROP)
    }

    /**
     * Deserializes an object from its binary representation.
     *
     * @param bytes The byte string containing the serialized data
     * @param unrecognizedFields Whether to keep or drop unrecognized fields
     * @return The deserialized object
     */
    fun fromBytes(
        bytes: ByteString,
        unrecognizedFields: UnrecognizedFieldsPolicy,
    ): T {
        val buffer = Buffer()
        buffer.write(bytes)
        return this.fromBytes(buffer, unrecognizedFields)
    }

    private fun fromBytes(
        buffer: Buffer,
        unrecognizedFields: UnrecognizedFieldsPolicy,
    ): T {
        return if (buffer.readByte().toInt() == 's'.code &&
            buffer.readByte().toInt() == 'o'.code &&
            buffer.readByte().toInt() == 'i'.code &&
            buffer.readByte().toInt() == 'a'.code
        ) {
            val keepUnrecognizedFields = unrecognizedFields == UnrecognizedFieldsPolicy.KEEP
            val result = this.impl.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
            if (!buffer.exhausted()) {
                throw IllegalArgumentException("Extra bytes after deserialization")
            }
            result
        } else {
            this.fromJsonCode(buffer.readUtf8(), unrecognizedFields)
        }
    }

    /**
     * The type descriptor that describes [T].
     * Provides reflective information about the type. For structs and enums, it
     * includes field names types, and other metadata useful for introspection and
     * tooling.
     */
    @get:JvmName("typeDescriptor")
    val typeDescriptor: TypeDescriptor.Reflective<T> = impl.typeDescriptor
}
