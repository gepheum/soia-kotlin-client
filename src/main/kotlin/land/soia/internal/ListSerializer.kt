package land.soia.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonArray
import land.soia.KeyedList
import land.soia.Serializer
import land.soia.reflection.ListDescriptor
import land.soia.reflection.TypeDescriptor
import okio.Buffer
import okio.BufferedSource

fun <E> listSerializer(item: Serializer<E>): Serializer<List<E>> {
    return Serializer(ListSerializer(item.impl))
}

fun <E, K> keyedListSerializer(
    item: Serializer<E>,
    keyExtractor: String,
    getKey: (E) -> K,
): Serializer<KeyedList<E, K>> {
    return Serializer(KeyedListSerializer(item.impl, keyExtractor, getKey))
}

private abstract class AbstractListSerializer<E, L : List<E>>(
    val item: SerializerImpl<E>,
) : SerializerImpl<L>(), ListDescriptor.Reflective {
    final override fun isDefault(value: L): Boolean {
        return value.isEmpty()
    }

    final override fun encode(
        input: L,
        buffer: Buffer,
    ) {
        val size = input.size
        if (size <= 3) {
            buffer.writeByte(246 + size)
        } else {
            buffer.writeByte(250)
            encodeLengthPrefix(size, buffer)
        }
        var numItems = 0
        for (item in input) {
            this.item.encode(item, buffer)
            numItems++
        }
        if (numItems != size) {
            throw IllegalArgumentException("Expected: $size items; got: $numItems")
        }
    }

    final override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): L {
        val wire = buffer.readByte().toInt() and 0xFF
        if (wire == 0 || wire == 246) {
            return emptyList
        }
        val size =
            if (wire == 250) {
                decodeNumber(buffer).toInt()
            } else if (wire in 247..249) {
                wire - 246
            } else {
                throw IllegalArgumentException("Expected: list; wire: $wire")
            }
        val items = mutableListOf<E>()
        for (i in 0 until size) {
            items.add(item.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields))
        }
        return toList(items)
    }

    final override fun toJson(
        input: L,
        readableFlavor: Boolean,
    ): JsonElement {
        return JsonArray(
            input.map { item.toJson(it, readableFlavor = readableFlavor) },
        )
    }

    final override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): L {
        return if (json is JsonPrimitive && 0 == json.intOrNull) {
            emptyList
        } else {
            toList(json.jsonArray.map { item.fromJson(it, keepUnrecognizedFields = keepUnrecognizedFields) })
        }
    }

    final override fun appendString(
        input: L,
        out: StringBuilder,
        eolIndent: String,
    ) {
        if (out.isEmpty()) {
            out.append("listOf()")
        } else {
            val newEolIndent = eolIndent + INDENT_UNIT
            out.append("listOf(")
            for (item in input) {
                out.append(newEolIndent)
                this.item.appendString(item, out, newEolIndent)
                out.append(',')
            }
            out.append(eolIndent).append(')')
        }
    }

    abstract val emptyList: L

    abstract fun toList(list: List<E>): L

    final override val itemType: TypeDescriptor.Reflective
        get() = item.typeDescriptor

    final override val typeSignature: JsonElement
        get() =
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("array"),
                    "item" to item.typeSignature,
                ),
            )

    final override fun addRecordDefinitionsTo(out: MutableMap<String, JsonElement>) {
        item.addRecordDefinitionsTo(out)
    }

    final override val typeDescriptor get() = this
}

private class ListSerializer<E>(item: SerializerImpl<E>) : AbstractListSerializer<E, List<E>>(item) {
    override val emptyList: List<E> = emptyList()

    override fun toList(list: List<E>): List<E> {
        return toFrozenList(list)
    }

    override val keyProperty: String?
        get() = null
}

private class KeyedListSerializer<E, K>(
    item: SerializerImpl<E>,
    override val keyProperty: String,
    val getKey: (E) -> K,
) : AbstractListSerializer<E, KeyedList<E, K>>(item) {
    override val emptyList: KeyedList<E, K> = emptyKeyedList()

    override fun toList(list: List<E>): KeyedList<E, K> {
        return toKeyedList(list, keyProperty, getKey)
    }
}
