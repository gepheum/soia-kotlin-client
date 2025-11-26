package land.soia.reflection

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import land.soia.formatReadableJson
import land.soia.internal.RecordId

interface TypeDescriptorBase {
    /**
     * Returns the stringified JSON representation of this type descriptor.
     *
     * @return A pretty-printed JSON string describing the type
     */
    fun asJsonCode(): String

    /**
     * Returns the JSON representation of this type descriptor.
     * If you just need the stringified JSON, call [asJsonCode] instead.
     *
     * @return A JsonObject describing the type
     */
    fun asJson(): JsonElement
}

/** Describes a Soia type. */
sealed interface TypeDescriptor : TypeDescriptorBase {
    override fun asJsonCode(): String {
        return asJsonCodeImpl(this)
    }

    override fun asJson(): JsonElement {
        return asJsonImpl(this)
    }

    companion object {
        /**
         * Parses a type descriptor from its JSON string representation, as returned by
         * [asJsonCode].
         */
        fun parseFromJsonCode(jsonCode: String): TypeDescriptor {
            val jsonElement = Json.Default.decodeFromString(JsonElement.serializer(), jsonCode)
            return parseTypeDescriptorImpl(jsonElement)
        }

        /**
         * Parses a type descriptor from its JSON representation, as returned by
         * [asJson].
         */
        fun parseFromJson(json: JsonElement): TypeDescriptor {
            return parseTypeDescriptorImpl(json)
        }
    }

    /** Adds runtime introspection capabilities to a [TypeDescriptor]. */
    sealed interface Reflective<T> : TypeDescriptorBase {
        override fun asJsonCode(): String {
            return asJsonCodeImpl(notReflective)
        }

        override fun asJson(): JsonElement {
            return asJsonImpl(notReflective)
        }

        /** A non-descriptive descriptor equivalent to this reflective descriptor. */
        val notReflective: TypeDescriptor get() = notReflectiveImpl(this, mutableMapOf())
    }
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

/**
 * Base interface for primitive type descriptors.
 */
interface PrimitiveDescriptorBase : TypeDescriptorBase {
    /** The specific primitive type being described. */
    val primitiveType: PrimitiveType
}

/** Describes a primitive type such as integers, strings, booleans, etc. */
class PrimitiveDescriptor private constructor(override val primitiveType: PrimitiveType) : PrimitiveDescriptorBase, TypeDescriptor {
    interface Reflective<T> : PrimitiveDescriptorBase, TypeDescriptor.Reflective<T>

    companion object {
        private val instances = PrimitiveType.entries.map { PrimitiveDescriptor(it) }.toList()

        internal fun getInstance(primitiveType: PrimitiveType): PrimitiveDescriptor {
            return instances[primitiveType.ordinal]
        }
    }
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
    /**
     * Adds runtime introspection capabilities to an [OptionalDescriptor].
     * The value type on the JVM side is `T?`.
     * Preferred in Kotlin over [OptionalDescriptor.JavaReflective].
     */
    interface Reflective<T> : OptionalDescriptorBase<TypeDescriptor.Reflective<*>>, TypeDescriptor.Reflective<T?>

    /**
     * Adds runtime introspection capabilities to an [OptionalDescriptor].
     * The value type on the JVM side is `java.util.Optional<T>`.
     * Preferred in Java over [OptionalDescriptor.Reflective].
     */
    interface JavaReflective<T> : OptionalDescriptorBase<TypeDescriptor.Reflective<*>>, TypeDescriptor.Reflective<java.util.Optional<T>>
}

interface ArrayDescriptorBase<ItemType : TypeDescriptorBase> : TypeDescriptorBase {
    /** Describes the type of the array items. */
    val itemType: ItemType

    /** Optional key chain for keyed arrays that support fast lookup by key. */
    val keyProperty: String?
}

/**
 * Describes an array type containing elements of a specific type.
 */
class ArrayDescriptor internal constructor(
    override val itemType: TypeDescriptor,
    override val keyProperty: String?,
) : ArrayDescriptorBase<TypeDescriptor>, TypeDescriptor {
    /** Adds runtime introspection capabilities to a [ArrayDescriptor]. */
    interface Reflective<E, L : List<E>> : ArrayDescriptorBase<TypeDescriptor.Reflective<*>>, TypeDescriptor.Reflective<L>
}

interface FieldBase {
    /** Field name as specified in the `.soia` file, for example "user_id" or "MONDAY". */
    val name: String

    /** Field number. */
    val number: Int
}

interface RecordDescriptorBase<Field : FieldBase> : TypeDescriptorBase {
    /** Name of the record as specified in the `.soia` file. */
    val name: String

    /**
     * A string containing all the names in the hierarchic sequence above and
     * including the struct. For example: "Foo.Bar" if "Bar" is nested within a
     * type called "Foo", or simply "Bar" if "Bar" is defined at the top-level of
     * the module.
     */
    val qualifiedName: String

    /** Path to the `.soia` file relative to the root of the soia source directory. */
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

    /** Adds runtime introspection capabilities to a [RecordDescriptor]. */
    sealed interface Reflective<T, Field : FieldBase> : RecordDescriptorBase<Field>, TypeDescriptor.Reflective<T>
}

interface StructFieldBase<TypeDescriptor : TypeDescriptorBase> : FieldBase {
    /** Describes the field type. */
    val type: TypeDescriptor
}

/** Describes a field in a struct. */
class StructField internal constructor(
    /** Field name as specified in the `.soia` file, for example "user_id". */
    override val name: String,
    override val number: Int,
    override val type: TypeDescriptor,
) : StructFieldBase<TypeDescriptor> {
    /** Adds runtime introspection capabilities to a [StructField]. */
    interface Reflective<Frozen, Mutable, Value> : StructFieldBase<TypeDescriptor.Reflective<*>> {
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
 *
 * @property name
 */
class StructDescriptor internal constructor(
    private val recordId: RecordId,
    override val removedNumbers: Set<Int>,
    fields: List<StructField> = listOf(),
) : RecordDescriptor<StructField>(), StructDescriptorBase<StructField> {
    override var fields: List<StructField> = fields
        internal set

    /** Name of the struct as specified in the `.soia` file. */
    override val name: String get() = recordId.name

    /** Qualified struct name, for example "Foo" or "Foo.Bar" if `Bar` is nested. */
    override val qualifiedName: String get() = recordId.qualifiedName

    /** Path to the `.soia` file relative to the root of the soia source directory. */
    override val modulePath: String get() = recordId.modulePath

    /** Adds runtime introspection capabilities to a [StructDescriptor]. */
    interface Reflective<Frozen, Mutable> :
        StructDescriptorBase<StructField.Reflective<Frozen, Mutable, *>>,
        RecordDescriptor.Reflective<Frozen, StructField.Reflective<Frozen, Mutable, *>> {
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

/** Describes a field in an enum. Can be either a constant field or a wrapper field. */
sealed interface EnumField : FieldBase {
    /** Adds runtime introspection capabilities to an [EnumField]. */
    sealed interface Reflective<Enum> : FieldBase
}

interface EnumConstantFieldBase : FieldBase

/** Describes an enum constant field. */
class EnumConstantField internal constructor(
    override val name: String,
    override val number: Int,
) : EnumConstantFieldBase, EnumField {
    /** Adds runtime introspection capabilities to an [EnumConstantField]. */
    interface Reflective<Enum> : EnumConstantFieldBase, EnumField.Reflective<Enum> {
        /** The constant value represented by this field. */
        val constant: Enum
    }
}

interface EnumWrapperFieldBase<TypeDescriptor : TypeDescriptorBase> : FieldBase {
    /** The type of the value associated with this enum field. */
    val type: TypeDescriptor
}

/** Describes an enum wrapper field. */
class EnumWrapperField internal constructor(
    override val name: String,
    override val number: Int,
    override val type: TypeDescriptor,
) : EnumWrapperFieldBase<TypeDescriptor>, EnumField {
    /** Adds runtime introspection capabilities to an [EnumWrapperField].
     *
     * @param Enum The enum type
     * @param Value The type of the associated value
     */
    interface Reflective<Enum, Value> : EnumWrapperFieldBase<TypeDescriptor.Reflective<*>>, EnumField.Reflective<Enum> {
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

/** Describes a Soia enum type. */
class EnumDescriptor internal constructor(
    private val recordId: RecordId,
    override val removedNumbers: Set<Int>,
    fields: List<EnumField> = listOf(),
) : RecordDescriptor<EnumField>(), EnumDescriptorBase<EnumField> {
    override var fields: List<EnumField> = fields
        internal set

    /** Name of the enum as specified in the `.soia` file. */
    override val name: String get() = recordId.name

    /** Qualified enum name, for example "Foo" or "Foo.Bar" if `Bar` is nested. */
    override val qualifiedName: String get() = recordId.qualifiedName

    /** Path to the `.soia` file relative to the root of the soia source directory. */
    override val modulePath: String get() = recordId.modulePath

    /**
     * Adds runtime introspection capabilities to an [EnumDescriptor].
     *
     * @param Enum The enum type
     */
    interface Reflective<Enum> :
        EnumDescriptorBase<EnumField.Reflective<Enum>>,
        RecordDescriptor.Reflective<Enum, EnumField.Reflective<Enum>> {
        /** Looks up the field corresponding to the given instance of Enum. */
        fun getField(e: Enum): EnumField.Reflective<Enum>
    }
}

private fun notReflectiveImpl(
    descriptor: TypeDescriptor.Reflective<*>,
    inProgress: MutableMap<TypeDescriptor.Reflective<*>, TypeDescriptor>,
): TypeDescriptor {
    run {
        val inProgressResult = inProgress[descriptor]
        if (inProgressResult != null) {
            return inProgressResult
        }
    }
    return when (descriptor) {
        is PrimitiveDescriptor.Reflective -> PrimitiveDescriptor.getInstance(descriptor.primitiveType)
        is OptionalDescriptor.Reflective<*> ->
            OptionalDescriptor(
                otherType = notReflectiveImpl(descriptor.otherType, inProgress),
            )
        is OptionalDescriptor.JavaReflective<*> ->
            OptionalDescriptor(
                otherType = notReflectiveImpl(descriptor.otherType, inProgress),
            )
        is ArrayDescriptor.Reflective<*, *> ->
            ArrayDescriptor(
                itemType = notReflectiveImpl(descriptor.itemType, inProgress),
                keyProperty = descriptor.keyProperty,
            )
        is StructDescriptor.Reflective<*, *> -> {
            val result =
                StructDescriptor(
                    recordId = RecordId.parse(descriptor.recordId()),
                    removedNumbers = descriptor.removedNumbers,
                )
            inProgress[descriptor] = result
            result.fields =
                descriptor.fields.map {
                    StructField(
                        name = it.name,
                        number = it.number,
                        type = notReflectiveImpl(it.type, inProgress),
                    )
                }
            result
        }
        is EnumDescriptor.Reflective<*> -> {
            val result =
                EnumDescriptor(
                    recordId = RecordId.parse(descriptor.recordId()),
                    removedNumbers = descriptor.removedNumbers,
                )
            inProgress[descriptor] = result
            result.fields =
                descriptor.fields.map {
                    if (it is EnumWrapperField.Reflective<*, *>) {
                        EnumWrapperField(
                            name = it.name,
                            number = it.number,
                            type = notReflectiveImpl(it.type, inProgress),
                        )
                    } else {
                        EnumConstantField(
                            name = it.name,
                            number = it.number,
                        )
                    }
                }
            result
        }
    }
}

private fun asJsonCodeImpl(descriptor: TypeDescriptor): String {
    return formatReadableJson(asJsonImpl(descriptor))
}

private fun asJsonImpl(descriptor: TypeDescriptor): JsonObject {
    val recordIdToDefinition = mutableMapOf<String, JsonObject>()
    addRecordDefinitions(descriptor, recordIdToDefinition)
    return JsonObject(
        mapOf(
            "type" to getTypeSignature(descriptor),
            "records" to JsonArray(recordIdToDefinition.values.toList()),
        ),
    )
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
        is ArrayDescriptor ->
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
        is ArrayDescriptor -> addRecordDefinitions(typeDescriptor.itemType, recordIdToDefinition)
        is StructDescriptor -> {
            val recordId = typeDescriptor.recordId()
            if (recordIdToDefinition.containsKey(recordId)) {
                return
            }
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
            if (recordIdToDefinition.containsKey(recordId)) {
                return
            }
            val fields =
                typeDescriptor.fields.map {
                    when (it) {
                        is EnumWrapperField ->
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
            val dependencies = typeDescriptor.fields.mapNotNull { (it as? EnumWrapperField)?.type }
            for (dependency in dependencies) {
                addRecordDefinitions(dependency, recordIdToDefinition)
            }
        }
    }
}
