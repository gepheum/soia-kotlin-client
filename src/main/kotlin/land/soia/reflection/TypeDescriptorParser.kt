package land.soia.reflection

import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import land.soia.Serializers
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
internal fun parseTypeDescriptorImpl(json: JsonElement): TypeDescriptor {
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
                            EnumWrapperField(name = name, number = number, type = type)
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

private class RecordBundle(
    val recordDescriptor: RecordDescriptor<*>,
    val fields: List<JsonElement>,
)

private fun parseRecordDescriptorPartial(json: JsonObject): RecordDescriptor<*> {
    val kind = json["kind"]!!.jsonPrimitive.content
    val recordId = RecordId.parse(json["id"]!!.jsonPrimitive.content)
    val removedNumbers =
        json["removed_numbers"]?.jsonArray?.map { it.jsonPrimitive.int }?.toSet() ?: setOf()
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
                "bool" -> Serializers.bool.typeDescriptor.notReflective
                "int32" -> Serializers.int32.typeDescriptor.notReflective
                "int64" -> Serializers.int64.typeDescriptor.notReflective
                "uint64" -> Serializers.uint64.typeDescriptor.notReflective
                "float32" -> Serializers.float32.typeDescriptor.notReflective
                "float64" -> Serializers.float64.typeDescriptor.notReflective
                "timestamp" -> Serializers.timestamp.typeDescriptor.notReflective
                "string" -> Serializers.string.typeDescriptor.notReflective
                "bytes" -> Serializers.bytes.typeDescriptor.notReflective
                else -> throw IllegalArgumentException("unknown primitive: $kind")
            }
        "optional" -> OptionalDescriptor(parseTypeDescriptorImpl(value, recordIdToBundle))
        "array" -> {
            val valueObject = value.jsonObject
            val itemType = parseTypeDescriptorImpl(valueObject["item"]!!, recordIdToBundle)
            val keyExtractor = valueObject["key_extractor"]?.jsonPrimitive?.content
            ArrayDescriptor(itemType, keyExtractor)
        }
        "record" -> {
            val recordId = value.jsonPrimitive.content
            val recordBundle = recordIdToBundle[recordId]!!
            recordBundle.recordDescriptor
        }
        else -> throw IllegalArgumentException("unknown type: $kind")
    }
}
