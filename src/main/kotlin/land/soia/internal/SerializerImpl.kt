package land.soia.internal

import kotlinx.serialization.json.JsonElement
import okio.Buffer
import okio.BufferedSource

interface SerializerImpl<T> {
    fun isDefault(value: T): Boolean

    fun toJson(
        input: T,
        readableFlavor: Boolean,
    ): JsonElement

    fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): T

    fun encode(
        input: T,
        buffer: Buffer,
    )

    fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean = false,
    ): T

    fun appendString(
        input: T,
        out: StringBuilder,
        eolIndent: String,
    )
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
