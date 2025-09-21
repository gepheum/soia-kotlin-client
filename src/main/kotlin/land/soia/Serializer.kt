package land.soia

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import land.soia.internal.MustNameArguments
import land.soia.internal.SerializerImpl
import land.soia.reflection.TypeDescriptor
import okio.Buffer
import okio.ByteString

/**
 * A serializer for converting objects of type [T] to and from various formats including JSON and binary.
 *
 * This class provides comprehensive serialization capabilities for Soia types, supporting both
 * human-readable JSON and efficient binary encoding formats.
 *
 * @param T The type of objects this serializer can handle
 */
class Serializer<T> internal constructor(
    internal val impl: SerializerImpl<T>,
) {
    /**
     * Converts an object to its JSON representation.
     *
     * @param input The object to serialize
     * @param readableFlavor Whether to produce a more human-readable and less compact
     *     JSON representation. Not suitable for persistencence: renaming fields in
     *     the '.soia' file, which is allowed by design, will break backward
     *     compatibility.
     * @return The JSON representation as a JsonElement
     */
    fun toJson(
        input: T,
        mustNameArguments: MustNameArguments = MustNameArguments,
        readableFlavor: Boolean = false,
    ): JsonElement {
        return this.impl.toJson(input, readableFlavor = readableFlavor)
    }

    /**
     * Converts an object to its JSON string representation.
     *
     * @param input The object to serialize
     * @param readableFlavor Whether to produce a more human-readable and less compact
     *     JSON representation. Not suitable for persistencence: renaming fields in
     *     the '.soia' file, which is allowed by design, will break backward
     *     compatibility.
     * @return The JSON representation as a string
     */
    fun toJsonCode(
        input: T,
        mustNameArguments: MustNameArguments = MustNameArguments,
        readableFlavor: Boolean = false,
    ): String {
        val jsonElement = this.impl.toJson(input, readableFlavor = readableFlavor)
        return if (readableFlavor) {
            readableJson.encodeToString(JsonElement.serializer(), jsonElement)
        } else {
            Json.Default.encodeToString(JsonElement.serializer(), jsonElement)
        }
    }

    /**
     * Deserializes an object from its JSON representation.
     *
     * @param json The JSON element to deserialize
     * @param readableFlavor Whether to produce a more human-readable and less compact
     *     JSON representation. Not suitable for persistencence: renaming fields in
     *     the '.soia' file, which is allowed by design, will break backward
     *     compatibility.
     * @return The deserialized object
     */
    fun fromJson(
        json: JsonElement,
        mustNameArguments: MustNameArguments = MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        return this.impl.fromJson(json, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    /**
     * Deserializes an object from its JSON string representation.
     *
     * @param jsonCode The JSON string to deserialize
     * @param readableFlavor Whether to produce a more human-readable and less compact
     *     JSON representation. Not suitable for persistencence: renaming fields in
     *     the '.soia' file, which is allowed by design, will break backward
     *     compatibility.
     * @return The deserialized object
     */
    fun fromJsonCode(
        jsonCode: String,
        mustNameArguments: MustNameArguments = MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        val jsonElement = Json.Default.decodeFromString(JsonElement.serializer(), jsonCode)
        return this.impl.fromJson(jsonElement, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    /**
     * Converts an object to its binary representation.
     *
     * The binary format includes a "soia" header followed by the encoded data,
     * providing an efficient storage format for Soia objects.
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
     *
     * @param bytes The byte array containing the serialized data
     * @param readableFlavor Whether to produce a more human-readable and less compact
     *     JSON representation. Not suitable for persistencence: renaming fields in
     *     the '.soia' file, which is allowed by design, will break backward
     *     compatibility.
     * @return The deserialized object
     */
    fun fromBytes(
        bytes: ByteArray,
        mustNameArguments: MustNameArguments = MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        val buffer = Buffer()
        buffer.write(bytes)
        return this.fromBytes(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    /**
     * Deserializes an object from its binary representation.
     *
     * @param bytes The ByteString containing the serialized data
     * @param readableFlavor Whether to produce a more human-readable and less compact
     *     JSON representation. Not suitable for persistencence: renaming fields in
     *     the '.soia' file, which is allowed by design, will break backward
     *     compatibility.
     * @return The deserialized object
     */
    fun fromBytes(
        bytes: ByteString,
        mustNameArguments: MustNameArguments = MustNameArguments,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        val buffer = Buffer()
        buffer.write(bytes)
        return this.fromBytes(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
    }

    private fun fromBytes(
        buffer: Buffer,
        keepUnrecognizedFields: Boolean = false,
    ): T {
        return if (buffer.readByte().toInt() == 's'.code &&
            buffer.readByte().toInt() == 'o'.code &&
            buffer.readByte().toInt() == 'i'.code &&
            buffer.readByte().toInt() == 'a'.code
        ) {
            val result = this.impl.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
            if (!buffer.exhausted()) {
                throw IllegalArgumentException("Extra bytes after deserialization")
            }
            result
        } else {
            this.fromJsonCode(buffer.readUtf8(), keepUnrecognizedFields = keepUnrecognizedFields)
        }
    }

    /**
     * Gets the type descriptor that describes the structure of type [T].
     *
     * This provides reflective information about the type, including field names,
     * types, and other metadata useful for introspection and tooling.
     */
    val typeDescriptor: TypeDescriptor.Reflective get() = this.impl.typeDescriptor

    companion object {
        @OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
        private val readableJson =
            Json {
                prettyPrint = true
                prettyPrintIndent = "  "
            }
    }
}
