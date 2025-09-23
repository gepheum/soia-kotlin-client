package land.soia.reflection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import land.soia.internal.RecordId

/**
 * Parses a type descriptor from its JSON representation.
 *
 * This function takes a JSON element containing a serialized type descriptor
 * and reconstructs the corresponding TypeDescriptor object.
 *
 * @param json The JSON element containing the type descriptor data
 * @return The parsed TypeDescriptor
 */
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
            is StructDescriptor -> {
                recordDescriptor.fields =
                    bundle.fields.map {
                        val fieldObject = it.jsonObject
                        val name = fieldObject["name"]!!.jsonPrimitive.content
                        val number = fieldObject["number"]!!.jsonPrimitive.int
                        val type = parseTypeDescriptorImpl(fieldObject["type"]!!, recordIdToBundle)
                        StructField(name = name, number = number, type = type)
                    }.toList()
            }
            is EnumDescriptor -> {
                recordDescriptor.fields =
                    bundle.fields.map {
                        val fieldObject = it.jsonObject
                        val name = fieldObject["name"]!!.jsonPrimitive.content
                        val number = fieldObject["number"]!!.jsonPrimitive.int
                        val typeJson = fieldObject["type"]
                        if (typeJson != null) {
                            val type = parseTypeDescriptorImpl(typeJson, recordIdToBundle)
                            EnumValueField(name = name, number = number, type = type)
                        } else {
                            EnumConstantField(name = name, number = number)
                        }
                    }.toList()
            }
        }
    }
    val type = jsonObject["type"]!!
    return parseTypeDescriptorImpl(type, recordIdToBundle)
}

/**
 * Parses a type descriptor from its JSON string representation.
 *
 * @param jsonCode The JSON string containing the type descriptor data
 * @return The parsed TypeDescriptor
 */
fun parseTypeDescriptor(jsonCode: String): TypeDescriptor {
    val jsonElement = Json.Default.decodeFromString(JsonElement.serializer(), jsonCode)
    return parseTypeDescriptor(jsonElement)
}

private class RecordBundle(
    val recordDescriptor: RecordDescriptor<*>,
    val fields: List<JsonElement>,
)

private fun parseRecordDescriptorPartial(json: JsonObject): RecordDescriptor<*> {
    val kind = json["kind"]!!.jsonPrimitive.content
    val recordId = RecordId.parse(json["id"]!!.jsonPrimitive.content)
    val removedNumbers =
        json["removed_fields"]?.jsonArray?.map { it.jsonPrimitive.int }?.toSet() ?: setOf()
    return when (kind) {
        "struct" -> {
            StructDescriptor(recordId, removedNumbers)
        }
        "enum" -> {
            EnumDescriptor(recordId, removedNumbers)
        }
        else -> {
            throw IllegalArgumentException("unknown kind: $kind")
        }
    }
}

private data class PrimitiveDescriptorImpl(
    override val primitiveType: PrimitiveType,
) : PrimitiveDescriptor

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
        "optional" -> OptionalDescriptor(parseTypeDescriptorImpl(value, recordIdToBundle))
        "array" -> {
            val valueObject = value.jsonObject
            val itemType = parseTypeDescriptorImpl(valueObject["item"]!!, recordIdToBundle)
            val keyChain = valueObject["key_chain"]?.jsonPrimitive?.content
            ListDescriptor(itemType, keyChain)
        }
        "record" -> {
            val recordId = value.jsonPrimitive.content
            val recordBundle = recordIdToBundle[recordId]!!
            recordBundle.recordDescriptor
        }
        else -> throw IllegalArgumentException("unknown type: $kind")
    }
}
