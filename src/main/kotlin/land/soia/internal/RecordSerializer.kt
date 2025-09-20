package land.soia.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import land.soia.reflection.FieldBase
import land.soia.reflection.RecordDescriptor
import land.soia.reflection.RecordDescriptorBase

sealed class RecordSerializer<T, Field : FieldBase> : SerializerImpl<T>(), RecordDescriptorBase<Field> {
    internal abstract val parsedRecordId: RecordId

    internal abstract fun fieldDefinitions(): List<JsonObject>

    internal abstract fun dependencies(): List<SerializerImpl<*>>

    abstract override val typeDescriptor: RecordDescriptor.Reflective<Field>

    final override val name: String
        get() = parsedRecordId.name

    final override val qualifiedName: String
        get() = parsedRecordId.qualifiedName

    final override val modulePath: String
        get() = parsedRecordId.modulePath

    final override val typeSignature: JsonElement
        get() =
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("record"),
                    "value" to JsonPrimitive(parsedRecordId.recordId),
                ),
            )

    final override fun addRecordDefinitionsTo(out: MutableMap<String, JsonElement>) {
        val recordId = parsedRecordId.recordId
        if (out.contains(recordId)) {
            return
        }
        val recordDefinition =
            mutableMapOf<String, JsonElement>(
                "kind" to JsonPrimitive("struct"),
                "id" to JsonPrimitive(recordId),
                "fields" to JsonArray(fieldDefinitions()),
            )
        if (removedNumbers.isNotEmpty()) {
            recordDefinition["removed_fields"] = JsonArray(removedNumbers.map { JsonPrimitive(it) })
        }
        out[recordId] = JsonObject(recordDefinition)
        for (dependency in dependencies()) {
            dependency.addRecordDefinitionsTo(out)
        }
    }
}
