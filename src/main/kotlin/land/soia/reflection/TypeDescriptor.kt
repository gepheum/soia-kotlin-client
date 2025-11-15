package land.soia.reflection

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import land.soia.formatReadableJson
import land.soia.internal.RecordId

interface TypeDescriptorBase

/** Describes a Soia type. */
sealed interface TypeDescriptor : TypeDescriptorBase {
    /** Adds runtime introspection capabilities to a [TypeDescriptor]. */
    sealed interface Reflective : TypeDescriptorBase
}

/** Enumeration of all primitive types supported by Soia. */
enum class PrimitiveType {
    /** Boolean true/false values. */
    BOOL,

    /** 32-bit signed integers. */
    INT_32,

    /** 64-bit signed integers. */
    INT_64,

    /** 64-bit unsigned integers. */
    UINT_64,

    /** 32-bit floating-point numbers. */
    FLOAT_32,

    /** 64-bit floating-point numbers. */
    FLOAT_64,

    /** Timestamp values representing instants in time. */
    TIMESTAMP,

    /** UTF-8 encoded text strings. */
    STRING,

    /** Binary data (byte sequences). */
    BYTES,
}

/** Describes a primitive type such as integers, strings, booleans, etc. */
interface PrimitiveDescriptor : TypeDescriptor, TypeDescriptor.Reflective {
    /** The specific primitive type being described. */
    val primitiveType: PrimitiveType
}

/**
 * Base interface for optional type descriptors.
 *
 * @param OtherType The type descriptor for the wrapped non-optional type
 */
interface OptionalDescriptorBase<OtherType : TypeDescriptorBase> : TypeDescriptorBase {
    /** The type descriptor for the wrapped non-optional type. */
    val otherType: OtherType
}

/**
 * Describes an optional type that can hold either a value of the wrapped type or null.
 */
class OptionalDescriptor internal constructor(
    override val otherType: TypeDescriptor,
) : OptionalDescriptorBase<TypeDescriptor>, TypeDescriptor {
    interface Reflective : OptionalDescriptorBase<TypeDescriptor.Reflective>, TypeDescriptor.Reflective
}

interface ListDescriptorBase<ItemType : TypeDescriptorBase> : TypeDescriptorBase {
    /** Describes the type of the array items. */
    val itemType: ItemType

    /** Optional key chain for keyed lists that support fast lookup by key. */
    val keyProperty: String?
}

/**
 * Describes a list type containing elements of a specific type.
 */
class ListDescriptor internal constructor(
    override val itemType: TypeDescriptor,
    override val keyProperty: String?,
) : ListDescriptorBase<TypeDescriptor>, TypeDescriptor {
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

    /** List of all fields in this record. */
    val fields: List<Field>

    /**
     * Looks up a field by name.
     *
     * @param name The field name to search for
     * @return The field with the given name, or null if not found
     */
    fun getField(name: String): Field?

    /**
     * Looks up a field by number.
     *
     * @param number The field number to search for
     * @return The field with the given number, or null if not found
     */
    fun getField(number: Int): Field?
}

private fun <Field : FieldBase> RecordDescriptorBase<Field>.recordId(): String = "${this.modulePath}:${this.qualifiedName}"

/**
 * Describes a record type (struct or enum).
 */
sealed class RecordDescriptor<Field : FieldBase> : RecordDescriptorBase<Field>, TypeDescriptor {
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

    sealed interface Reflective<Field : FieldBase> : RecordDescriptorBase<Field>, TypeDescriptor.Reflective
}

interface StructFieldBase<TypeDescriptor : TypeDescriptorBase> : FieldBase {
    /** Describes the field type. */
    val type: TypeDescriptor
}

class StructField internal constructor(
    override val name: String,
    override val number: Int,
    override val type: TypeDescriptor,
) : StructFieldBase<TypeDescriptor> {
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

interface StructDescriptorBase<Field : StructFieldBase<*>> : RecordDescriptorBase<Field>

/**
 * Describes a Soia struct type with its fields and structure.
 */
class StructDescriptor internal constructor(
    private val recordId: RecordId,
    override val removedNumbers: Set<Int>,
    fields: List<StructField> = listOf(),
) : RecordDescriptor<StructField>(), StructDescriptorBase<StructField> {
    override var fields: List<StructField> = fields
        internal set

    override val name: String get() = recordId.name
    override val qualifiedName: String get() = recordId.qualifiedName
    override val modulePath: String get() = recordId.modulePath

    interface Reflective<Frozen, Mutable> :
        StructDescriptorBase<StructField.Reflective<Frozen, Mutable, *>>,
        RecordDescriptor.Reflective<StructField.Reflective<Frozen, Mutable, *>> {
        /**
         * Returns a new instance of the generated mutable class for a struct.
         * Performs a shallow copy of `initializer` if `initializer` is specified.
         */
        fun newMutable(initializer: Frozen? = null): Mutable

        /**
         * Converts a mutable struct instance to its frozen (immutable) form.
         */
        fun toFrozen(mutable: Mutable): Frozen
    }
}

sealed interface EnumField : FieldBase {
    sealed interface Reflective<Enum> : FieldBase
}

interface EnumConstantFieldBase : FieldBase

/**
 * Describes an enum constant field (a field that represents a simple named value).
 */
class EnumConstantField internal constructor(
    override val name: String,
    override val number: Int,
) : EnumConstantFieldBase, EnumField {
    interface Reflective<Enum> : EnumConstantFieldBase, EnumField.Reflective<Enum> {
        /** The constant value represented by this field. */
        val constant: Enum
    }
}

interface EnumValueFieldBase<TypeDescriptor : TypeDescriptorBase> : FieldBase {
    /** The type of the value associated with this enum field. */
    val type: TypeDescriptor
}

/**
 * Describes an enum value field (a field that can hold additional data).
 */
class EnumValueField internal constructor(
    override val name: String,
    override val number: Int,
    override val type: TypeDescriptor,
) : EnumValueFieldBase<TypeDescriptor>, EnumField {
    /**
     * Reflective interface for enum value fields.
     *
     * @param Enum The enum type
     * @param Value The type of the associated value
     */
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

interface EnumDescriptorBase<Field : FieldBase> : RecordDescriptorBase<Field>

/**
 * Describes a Soia enum type with its possible values and associated data.
 */
class EnumDescriptor internal constructor(
    private val recordId: RecordId,
    override val removedNumbers: Set<Int>,
    fields: List<EnumField> = listOf(),
) : RecordDescriptor<EnumField>(), EnumDescriptorBase<EnumField> {
    override var fields: List<EnumField> = fields
        internal set

    override val name: String get() = recordId.name
    override val qualifiedName: String get() = recordId.qualifiedName
    override val modulePath: String get() = recordId.modulePath

    /**
     * Reflective interface for enum descriptors.
     *
     * @param Enum The enum type
     */
    interface Reflective<Enum> : EnumDescriptorBase<EnumField.Reflective<Enum>>, RecordDescriptor.Reflective<EnumField.Reflective<Enum>> {
        /** Looks up the field corresponding to the given instance of Enum. */
        fun getField(e: Enum): EnumField.Reflective<Enum>
    }
}

fun TypeDescriptor.Reflective.notReflective(): TypeDescriptor {
    return when (this) {
        is PrimitiveDescriptor -> this
        is OptionalDescriptor.Reflective ->
            OptionalDescriptor(
                otherType = this.otherType.notReflective(),
            )
        is ListDescriptor.Reflective ->
            ListDescriptor(
                itemType = this.itemType.notReflective(),
                keyProperty = this.keyProperty,
            )
        is StructDescriptor.Reflective<*, *> ->
            StructDescriptor(
                recordId = RecordId.parse(this.recordId()),
                fields =
                    this.fields.map {
                        StructField(
                            name = it.name,
                            number = it.number,
                            type = it.type.notReflective(),
                        )
                    },
                removedNumbers = this.removedNumbers,
            )
        is EnumDescriptor.Reflective<*> ->
            EnumDescriptor(
                recordId = RecordId.parse(this.recordId()),
                fields =
                    this.fields.map {
                        if (it is EnumValueField.Reflective<*, *>) {
                            EnumValueField(
                                name = it.name,
                                number = it.number,
                                type = it.type.notReflective(),
                            )
                        } else {
                            EnumConstantField(
                                name = it.name,
                                number = it.number,
                            )
                        }
                    },
                removedNumbers = this.removedNumbers,
            )
    }
}

/**
 * Converts this type descriptor to its JSON representation.
 *
 * @return A JsonObject containing the complete type information
 */
fun TypeDescriptor.asJson(): JsonObject {
    val recordIdToDefinition = mutableMapOf<String, JsonObject>()
    addRecordDefinitions(this, recordIdToDefinition)
    return JsonObject(
        mapOf(
            "type" to getTypeSignature(this),
            "records" to JsonArray(recordIdToDefinition.values.toList()),
        ),
    )
}

/**
 * Converts this type descriptor to a JSON string representation.
 *
 * @return A pretty-printed JSON string describing the type
 */
fun TypeDescriptor.asJsonCode(): String {
    return formatReadableJson(asJson())
}

/**
 * Converts this reflective type descriptor to its JSON representation.
 *
 * @return A JsonObject containing the complete type information
 */
fun TypeDescriptor.Reflective.asJson(): JsonObject {
    return this.notReflective().asJson()
}

/**
 * Converts this reflective type descriptor to a JSON string representation.
 *
 * @return A pretty-printed JSON string describing the type
 */
fun TypeDescriptor.Reflective.asJsonCode(): String {
    return this.notReflective().asJsonCode()
}

private fun getTypeSignature(typeDescriptor: TypeDescriptor): JsonObject {
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
        is OptionalDescriptor ->
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("optional"),
                    "value" to getTypeSignature(typeDescriptor.otherType),
                ),
            )
        is ListDescriptor ->
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("array"),
                    "value" to
                        JsonObject(
                            if (typeDescriptor.keyProperty != null) {
                                mapOf(
                                    "item" to getTypeSignature(typeDescriptor.itemType),
                                    "key_extractor" to JsonPrimitive(typeDescriptor.keyProperty),
                                )
                            } else {
                                mapOf(
                                    "item" to getTypeSignature(typeDescriptor.itemType),
                                )
                            },
                        ),
                ),
            )
        is RecordDescriptor<*> ->
            JsonObject(
                mapOf(
                    "kind" to JsonPrimitive("record"),
                    "value" to JsonPrimitive("${typeDescriptor.modulePath}:${typeDescriptor.qualifiedName}"),
                ),
            )
    }
}

private fun addRecordDefinitions(
    typeDescriptor: TypeDescriptorBase,
    recordIdToDefinition: MutableMap<String, JsonObject>,
) {
    when (typeDescriptor) {
        is PrimitiveDescriptor -> {}
        is OptionalDescriptor -> addRecordDefinitions(typeDescriptor.otherType, recordIdToDefinition)
        is ListDescriptor -> addRecordDefinitions(typeDescriptor.itemType, recordIdToDefinition)
        is StructDescriptor -> {
            val recordId = typeDescriptor.recordId()
            val fields =
                typeDescriptor.fields.map {
                    JsonObject(
                        mapOf(
                            "name" to JsonPrimitive(it.name),
                            "number" to JsonPrimitive(it.number),
                            "type" to getTypeSignature(it.type),
                        ),
                    )
                }
            val recordDefinition =
                mutableMapOf(
                    "kind" to JsonPrimitive("struct"),
                    "id" to JsonPrimitive(recordId),
                    "fields" to JsonArray(fields),
                )
            if (typeDescriptor.removedNumbers.isNotEmpty()) {
                recordDefinition["removed_numbers"] = JsonArray(typeDescriptor.removedNumbers.map { JsonPrimitive(it) })
            }
            recordIdToDefinition[recordId] = JsonObject(recordDefinition)
            val dependencies = typeDescriptor.fields.map { it.type }
            for (dependency in dependencies) {
                addRecordDefinitions(dependency, recordIdToDefinition)
            }
        }
        is EnumDescriptor -> {
            val recordId = typeDescriptor.recordId()
            val fields =
                typeDescriptor.fields.map {
                    when (it) {
                        is EnumValueField ->
                            JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive(it.name),
                                    "number" to JsonPrimitive(it.number),
                                    "type" to getTypeSignature(it.type),
                                ),
                            )
                        is EnumConstantField ->
                            JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive(it.name),
                                    "number" to JsonPrimitive(it.number),
                                ),
                            )
                    }
                }
            val recordDefinition =
                mutableMapOf(
                    "kind" to JsonPrimitive("enum"),
                    "id" to JsonPrimitive(recordId),
                    "fields" to JsonArray(fields),
                )
            if (typeDescriptor.removedNumbers.isNotEmpty()) {
                recordDefinition["removed_numbers"] = JsonArray(typeDescriptor.removedNumbers.map { JsonPrimitive(it) })
            }
            recordIdToDefinition[recordId] = JsonObject(recordDefinition)
            val dependencies = typeDescriptor.fields.mapNotNull { (it as? EnumValueField)?.type }
            for (dependency in dependencies) {
                addRecordDefinitions(dependency, recordIdToDefinition)
            }
        }
    }
}
