package land.soia.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import land.soia.TypeDescriptor
import land.soia.TypeDescriptorBase
import okio.Buffer
import okio.BufferedSource

abstract class SerializerImpl<T> : TypeDescriptorBase {
    abstract fun isDefault(value: T): Boolean

    abstract fun toJson(
        input: T,
        readableFlavor: Boolean,
    ): JsonElement

    abstract fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): T

    abstract fun encode(
        input: T,
        buffer: Buffer,
    )

    abstract fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean = false,
    ): T

    abstract fun appendString(
        input: T,
        out: StringBuilder,
        eolIndent: String,
    )

    internal abstract val typeDescriptor: TypeDescriptor

    internal abstract val typeSignature: JsonElement

    abstract fun addRecordDefinitionsTo(out: MutableMap<String, JsonElement>)

    override fun asJson(): JsonObject {
        val recordDefinitions = mutableMapOf<String, JsonElement>()
        addRecordDefinitionsTo(recordDefinitions)
        return JsonObject(
            mapOf(
                "type" to typeSignature,
                "records" to JsonArray(recordDefinitions.values.toList()),
            ),
        )
    }
}

internal const val INDENT_UNIT: String = "  "

fun <T> toStringImpl(
    input: T,
    serializer: SerializerImpl<T>,
): String {
    val stringBuilder = StringBuilder()
    serializer.appendString(input, stringBuilder, "\n")
    return stringBuilder.toString()
}
