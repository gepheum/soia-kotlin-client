package land.soia.reflection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

interface TypeDescriptorBase

sealed interface TypeDescriptor : TypeDescriptorBase {
    sealed interface Reflective : TypeDescriptorBase
}

enum class PrimitiveType {
    BOOL,
    INT_32,
    INT_64,
    UINT_64,
    FLOAT_32,
    FLOAT_64,
    TIMESTAMP,
    STRING,
    BYTES,
}

interface PrimitiveDescriptor : TypeDescriptor, TypeDescriptor.Reflective {
    val primitiveType: PrimitiveType
}

interface OptionalDescriptorBase<OtherType : TypeDescriptorBase> : TypeDescriptorBase {
    val otherType: OtherType
}

interface OptionalDescriptor : TypeDescriptor, OptionalDescriptorBase<TypeDescriptor> {
    interface Reflective : TypeDescriptor.Reflective, OptionalDescriptorBase<TypeDescriptor.Reflective>
}

/** Base interface of `ListDescriptor` and `ListDescriptor.Reflective`. */
interface ListDescriptorBase<ItemType : TypeDescriptorBase> : TypeDescriptor {
    /** Describes the type of the array items. */
    val itemType: ItemType
    val keyChain: String?
}

/** Describes a list type. */
interface ListDescriptor : ListDescriptorBase<TypeDescriptor>, TypeDescriptor {
    interface Reflective : ListDescriptorBase<TypeDescriptor.Reflective>, TypeDescriptor.Reflective
}

interface FieldBase {
    /** Field name as specified in the `.soia` file, e.g. "user_id". */
    val name: String

    /** Field number. */
    val number: Int
}

interface RecordDescriptorBase<Field : FieldBase> : TypeDescriptorBase {
    /** Name of the struct as specified in the `.soia` file. */
    val name: String

    /**
     * A string containing all the names in the hierarchic sequence above and
     * including the struct. For example: "Foo.Bar" if "Bar" is nested within a
     * type called "Foo", or simply "Bar" if "Bar" is defined at the top-level of
     * the module.
     */
    val qualifiedName: String

    /**
     * Path to the module where the struct is defined, relative to the root of the
     * project.
     */
    val modulePath: String

    /** The field numbers marked as removed. */
    val removedNumbers: Set<Int>

    val fields: List<Field>

    fun getField(name: String): Field?

    fun getField(number: Int): Field?
}

sealed interface RecordDescriptor<Field : FieldBase> : RecordDescriptorBase<Field>, TypeDescriptor {
    interface Reflective<Field : FieldBase> : RecordDescriptorBase<Field>, TypeDescriptor.Reflective
}

interface StructFieldBase<TypeDescriptor : TypeDescriptorBase> : FieldBase {
    /** Describes the field type. */
    val type: TypeDescriptor
}

interface StructField : StructFieldBase<TypeDescriptor> {
    interface Reflective<Frozen, Mutable, Value> : StructFieldBase<TypeDescriptor.Reflective> {
        /** Extracts the value of the field from the given struct. */
        fun get(struct: Frozen): Value

        /** Assigns the given value to the field of the given struct. */
        fun set(
            struct: Mutable,
            value: Value,
        )
    }
}

/** Describes a Soia struct. */
interface StructDescriptorBase<Field : StructFieldBase<*>> : RecordDescriptorBase<Field>

interface StructDescriptor : StructDescriptorBase<StructField>, RecordDescriptor<StructField> {
    interface Reflective<Frozen, Mutable> :
        StructDescriptorBase<StructField.Reflective<Frozen, Mutable, *>>,
        RecordDescriptor.Reflective<StructField.Reflective<Frozen, Mutable, *>> {
        /**
         * Returns a new instance of the generated mutable class for a struct.
         * Performs a shallow copy of `initializer` if `initializer` is specified.
         */
        fun newMutable(initializer: Frozen?): Mutable

        fun toFrozen(mutable: Mutable): Frozen
    }
}

sealed interface EnumField : FieldBase {
    sealed interface Reflective<Enum> : FieldBase
}

interface EnumConstantFieldBase : FieldBase

interface EnumConstantField : EnumConstantFieldBase, EnumField {
    interface Reflective<Enum> : EnumConstantFieldBase, EnumField.Reflective<Enum> {
        val constant: Enum
    }
}

interface EnumValueFieldBase<TypeDescriptor : TypeDescriptorBase> : EnumField {
    val type: TypeDescriptor
}

interface EnumValueField : EnumValueFieldBase<TypeDescriptor>, EnumField {
    interface Reflective<Enum, Value> : EnumValueFieldBase<TypeDescriptor.Reflective>, EnumField.Reflective<Enum> {
        /** Returns whether the given enum instance if it matches this enum field. */
        fun test(e: Enum): Boolean

        /**
         * Extracts the value held by the given enum instance assuming it matches this
         * enum field. The behavior is undefined if `test(e)` is false.
         */
        fun get(e: Enum): Value

        /**
         * Returns a new enum instance matching this enum field and holding the given
         * value.
         */
        fun wrap(value: Value): Enum
    }
}

interface EnumDescriptorBase<Field : FieldBase> : RecordDescriptor<Field>

/** Describes a Soia enum. */
interface EnumDescriptor : EnumDescriptorBase<EnumField>, RecordDescriptor<EnumField> {
    interface Reflective<Enum> : EnumDescriptorBase<EnumField.Reflective<Enum>>, RecordDescriptor.Reflective<EnumField.Reflective<Enum>> {
        /** Looks up the field corresponding to the given instance of Enum. */
        fun getField(e: Enum): EnumField.Reflective<Enum>
    }
}

fun TypeDescriptor.asJson(): JsonObject {
    return asJsonImpl(this)
}

fun TypeDescriptor.asJsonCode(): String {
    return readableJson.encodeToString(JsonElement.serializer(), asJson())
}

fun TypeDescriptor.Reflective.asJson(): JsonObject {
    return asJsonImpl(this)
}

fun TypeDescriptor.Reflective.asJsonCode(): String {
    return readableJson.encodeToString(JsonElement.serializer(), asJson())
}

@OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)
private val readableJson =
    Json {
        prettyPrint = true
        prettyPrintIndent = "  "
    }

private fun asJsonImpl(typeDescriptor: TypeDescriptorBase): JsonObject {
    val recordIdToDefinition = mutableMapOf<String, JsonObject>()
    addRecordDefinitions(typeDescriptor, recordIdToDefinition)
    return JsonObject(
        mapOf(
            "records" to JsonArray(recordIdToDefinition.values.toList()),
            "type" to getTypeSignature(typeDescriptor),
        ),
    )
}

private fun getTypeSignature(typeDescriptor: TypeDescriptorBase): JsonObject {
    return when (typeDescriptor) {
        is PrimitiveDescriptor ->
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("primitive"),
                    "value" to
                        JsonPrimitive(
                            when (typeDescriptor.primitiveType) {
                                PrimitiveType.BOOL -> "bool"
                                PrimitiveType.INT_32 -> "int32"
                                PrimitiveType.INT_64 -> "int64"
                                PrimitiveType.UINT_64 -> "uint64"
                                PrimitiveType.FLOAT_32 -> "float32"
                                PrimitiveType.FLOAT_64 -> "float64"
                                PrimitiveType.TIMESTAMP -> "timestamp"
                                PrimitiveType.STRING -> "string"
                                PrimitiveType.BYTES -> "bytes"
                            },
                        ),
                ),
            )
        is OptionalDescriptorBase<*> ->
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("optional"),
                    "value" to getTypeSignature(typeDescriptor.otherType),
                ),
            )
        is ListDescriptorBase<*> ->
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("array"),
                    "value" to
                        JsonObject(
                            if (typeDescriptor.keyChain != null) {
                                mapOf(
                                    "item" to getTypeSignature(typeDescriptor.itemType),
                                    "key_chain" to JsonPrimitive(typeDescriptor.keyChain),
                                )
                            } else {
                                mapOf(
                                    "item" to getTypeSignature(typeDescriptor.itemType),
                                )
                            },
                        ),
                ),
            )
        is RecordDescriptorBase<*> ->
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("record"),
                    "value" to JsonPrimitive("${typeDescriptor.modulePath}:${typeDescriptor.qualifiedName}"),
                ),
            )
        else -> throw AssertionError("unreachable")
    }
}

private fun addRecordDefinitions(
    typeDescriptor: TypeDescriptorBase,
    recordIdToDefinition: MutableMap<String, JsonObject>,
) {
    when (typeDescriptor) {
        is PrimitiveDescriptor -> {}
        is OptionalDescriptorBase<*> -> addRecordDefinitions(typeDescriptor.otherType, recordIdToDefinition)
        is ListDescriptorBase<*> -> addRecordDefinitions(typeDescriptor.itemType, recordIdToDefinition)
        is RecordDescriptorBase<*> -> {
            val recordId = "${typeDescriptor.modulePath}:${typeDescriptor.qualifiedName}"
            if (recordId !in recordIdToDefinition) {
                val kind: String
                val fields: List<JsonObject>
                val dependencies: List<TypeDescriptorBase>
                if (typeDescriptor is StructDescriptorBase<*>) {
                    kind = "struct"
                    fields =
                        typeDescriptor.fields.map {
                            JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive(it.name),
                                    "number" to JsonPrimitive(it.number),
                                    "type" to getTypeSignature(it.type),
                                ),
                            )
                        }
                    dependencies = typeDescriptor.fields.map { it.type }
                } else {
                    kind = "enum"
                    fields =
                        typeDescriptor.fields.map {
                            when (it) {
                                is EnumConstantFieldBase ->
                                    JsonObject(
                                        mapOf(
                                            "name" to JsonPrimitive(it.name),
                                            "number" to JsonPrimitive(it.number),
                                        ),
                                    )
                                is EnumValueFieldBase<*> ->
                                    JsonObject(
                                        mapOf(
                                            "name" to JsonPrimitive(it.name),
                                            "number" to JsonPrimitive(it.number),
                                            "type" to getTypeSignature(it.type),
                                        ),
                                    )
                                else -> throw AssertionError("unreachable")
                            }
                        }
                    dependencies = typeDescriptor.fields.mapNotNull { (it as? EnumValueField)?.type }
                }
                recordIdToDefinition[recordId] =
                    JsonObject(
                        mapOf(
                            "kind" to JsonPrimitive(kind),
                            "id" to JsonPrimitive(recordId),
                            "fields" to JsonArray(fields),
                            "removed_fields" to JsonArray(typeDescriptor.removedNumbers.map { JsonPrimitive(it) }),
                        ),
                    )
                for (dependency in dependencies) {
                    addRecordDefinitions(dependency, recordIdToDefinition)
                }
            }
        }
    }
}
