package land.soia.reflection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import land.soia.internal.RecordId

fun parseTypeDescriptor(json: JsonElement): TypeDescriptor {
    val jsonObject = json.jsonObject
    val records = jsonObject["records"]?.jsonArray ?: listOf()
    val recordIdToBundle = mutableMapOf<String, RecordBundle>()
    for (record in records) {
        val recordObject = record.jsonObject
        val recordDescriptor = parseRecordDescriptorPartial(recordObject)
        val recordId = "${recordDescriptor.modulePath}:${recordDescriptor.qualifiedName}"
        val fields = recordObject["fields"]?.jsonArray ?: listOf()
        recordIdToBundle[recordId] = RecordBundle(recordDescriptor, fields)
    }
    for (bundle in recordIdToBundle.values) {
        when (val recordDescriptor = bundle.recordDescriptor) {
            is StructDescriptorImpl -> {
                recordDescriptor.fields =
                    bundle.fields.map {
                        val fieldObject = it.jsonObject
                        val name = fieldObject["name"]!!.jsonPrimitive.content
                        val number = fieldObject["number"]!!.jsonPrimitive.int
                        val type = parseTypeDescriptorImpl(fieldObject["type"]!!, recordIdToBundle)
                        FieldImpl(name = name, number = number, type = type)
                    }.toList()
            }
            is EnumDescriptorImpl -> {
                recordDescriptor.fields =
                    bundle.fields.map {
                        val fieldObject = it.jsonObject
                        val name = fieldObject["name"]!!.jsonPrimitive.content
                        val number = fieldObject["number"]!!.jsonPrimitive.int
                        val typeJson = fieldObject["type"]
                        if (typeJson != null) {
                            val type = parseTypeDescriptorImpl(typeJson, recordIdToBundle)
                            FieldImpl(name = name, number = number, type = type)
                        } else {
                            EnumConstantFieldImpl(name = name, number = number)
                        }
                    }.toList()
            }
        }
    }
    val type = jsonObject["type"]!!
    return parseTypeDescriptorImpl(type, recordIdToBundle)
}

fun parseTypeDescriptor(jsonCode: String): TypeDescriptor {
    val jsonElement = Json.Default.decodeFromString(JsonElement.serializer(), jsonCode)
    return parseTypeDescriptor(jsonElement)
}

private class RecordBundle(
    val recordDescriptor: RecordDescriptorImpl<*>,
    val fields: List<JsonElement>,
)

private fun parseRecordDescriptorPartial(json: JsonObject): RecordDescriptorImpl<*> {
    val kind = json["kind"]!!.jsonPrimitive.content
    val recordId = RecordId.parse(json["id"]!!.jsonPrimitive.content)
    val removedNumbers =
        json["removed_fields"]?.jsonArray?.map { it.jsonPrimitive.int }?.toSet() ?: setOf()
    return when (kind) {
        "struct" -> {
            StructDescriptorImpl(recordId, removedNumbers)
        }
        "enum" -> {
            EnumDescriptorImpl(recordId, removedNumbers)
        }
        else -> {
            throw IllegalArgumentException("unknown kind: $kind")
        }
    }
}

private class PrimitiveDescriptorImpl(override val primitiveType: PrimitiveType) : PrimitiveDescriptor

private class OptionalDescriptorImpl(override val otherType: TypeDescriptor) : OptionalDescriptor

private class ListDescriptorImpl(override val itemType: TypeDescriptor, override val keyChain: String?) : ListDescriptor

private sealed class RecordDescriptorImpl<Field : FieldBase>(
    val recordId: RecordId,
    override val removedNumbers: Set<Int>,
) : RecordDescriptor<Field> {
    final override val name: String get() = recordId.name
    final override val qualifiedName: String get() = recordId.qualifiedName
    final override val modulePath: String get() = recordId.modulePath

    override var parentType: RecordDescriptor<*>? = null

    override fun getField(name: String): Field? {
        return nameToField[name]
    }

    override fun getField(number: Int): Field? {
        return numberToField[number]
    }

    private val nameToField by lazy {
        fields.associateBy { it.name }
    }

    private val numberToField by lazy {
        fields.associateBy { it.number }
    }
}

private class StructDescriptorImpl(
    recordId: RecordId,
    removedNumbers: Set<Int>,
) : RecordDescriptorImpl<StructField>(recordId, removedNumbers), StructDescriptor {
    override var fields = listOf<StructField>()
}

private class EnumDescriptorImpl(
    recordId: RecordId,
    removedNumbers: Set<Int>,
) : RecordDescriptorImpl<EnumField>(recordId, removedNumbers), EnumDescriptor {
    override var fields = listOf<EnumField>()
}

private data class FieldImpl(
    override val name: String,
    override val number: Int,
    override val type: TypeDescriptor,
) : StructField, EnumValueField

private data class EnumConstantFieldImpl(
    override val name: String,
    override val number: Int,
) : EnumConstantField

private fun parseTypeDescriptorImpl(
    typeSignature: JsonElement,
    recordIdToBundle: Map<String, RecordBundle>,
): TypeDescriptor {
    val jsonObject = typeSignature.jsonObject
    val kind = jsonObject["kind"]!!.jsonPrimitive.content
    val value = jsonObject["value"]!!
    return when (kind) {
        "primitive" ->
            when (value.jsonPrimitive.content) {
                "bool" -> PrimitiveDescriptorImpl(PrimitiveType.BOOL)
                "int32" -> PrimitiveDescriptorImpl(PrimitiveType.INT_32)
                "int64" -> PrimitiveDescriptorImpl(PrimitiveType.INT_64)
                "uint64" -> PrimitiveDescriptorImpl(PrimitiveType.UINT_64)
                "float32" -> PrimitiveDescriptorImpl(PrimitiveType.FLOAT_32)
                "float64" -> PrimitiveDescriptorImpl(PrimitiveType.FLOAT_64)
                "timestamp" -> PrimitiveDescriptorImpl(PrimitiveType.TIMESTAMP)
                "string" -> PrimitiveDescriptorImpl(PrimitiveType.STRING)
                "bytes" -> PrimitiveDescriptorImpl(PrimitiveType.BYTES)
                else -> throw IllegalArgumentException("unknown primitive: $kind")
            }
        "optional" -> OptionalDescriptorImpl(parseTypeDescriptorImpl(value, recordIdToBundle))
        "array" -> {
            val valueObject = value.jsonObject
            val itemType = parseTypeDescriptorImpl(valueObject["item"]!!, recordIdToBundle)
            val keyChain = valueObject["key_chain"]?.jsonPrimitive?.content
            ListDescriptorImpl(itemType, keyChain)
        }
        "record" -> {
            val recordId = value.jsonPrimitive.content
            val recordBundle = recordIdToBundle[recordId]!!
            recordBundle.recordDescriptor
        }
        else -> throw IllegalArgumentException("unknown type: $kind")
    }
}
