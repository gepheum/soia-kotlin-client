package land.soia.internal

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import land.soia.Serializer
import land.soia.reflection.EnumConstantField
import land.soia.reflection.EnumDescriptor
import land.soia.reflection.EnumField
import land.soia.reflection.EnumWrapperField
import land.soia.reflection.TypeDescriptor
import okio.Buffer
import okio.BufferedSource

class EnumSerializer<Enum : Any> private constructor(
    recordId: String,
    private val getKindOrdinal: (Enum) -> Int,
    kindCount: Int,
    private val unknown: UnknownField<Enum>,
) : RecordSerializer<Enum, EnumField.Reflective<Enum>>(), EnumDescriptor.Reflective<Enum> {
    override val parsedRecordId = RecordId.parse(recordId)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <Enum : Any, Unknown : Enum> create(
            recordId: String,
            getKindOrdinal: (Enum) -> Int,
            kindCount: Int,
            unknownInstance: Unknown,
            wrapUnrecognized: (UnrecognizedEnum<Enum>) -> Unknown,
            getUnrecognized: (Unknown) -> UnrecognizedEnum<Enum>?,
        ) = EnumSerializer(
            recordId,
            getKindOrdinal,
            kindCount,
            unknown =
                UnknownField(
                    unknownInstance,
                    wrapUnrecognized,
                    getUnrecognized as (Enum) -> UnrecognizedEnum<Enum>?,
                ),
        )
    }

    fun addConstantField(
        number: Int,
        name: String,
        kindOrdinal: Int,
        instance: Enum,
    ) {
        checkNotFinalized()
        addFieldImpl(ConstantField(number, name, kindOrdinal, instance))
    }

    fun <Instance : Enum, T> addWrapperField(
        number: Int,
        name: String,
        kindOrdinal: Int,
        valueSerializer: Serializer<T>,
        wrap: (T) -> Instance,
        getValue: (Instance) -> T,
    ) {
        checkNotFinalized()
        @Suppress("UNCHECKED_CAST")
        addFieldImpl(WrapperField(number, name, kindOrdinal, valueSerializer, wrap, getValue as (Enum) -> T, getKindOrdinal))
    }

    fun addRemovedNumber(number: Int) {
        checkNotFinalized()
        mutableRemovedNumbers.add(number)
        numberToField[number] = RemovedNumber(number)
    }

    private sealed class FieldOrRemoved<Enum> {
        abstract val number: Int
    }

    private sealed class Field<Enum : Any> : FieldOrRemoved<Enum>() {
        abstract val name: String
        abstract val kindOrdinal: Int

        abstract fun toJson(
            input: Enum,
            readableFlavor: Boolean,
        ): JsonElement

        abstract fun encode(
            input: Enum,
            buffer: Buffer,
        )

        abstract fun appendString(
            input: Enum,
            out: StringBuilder,
            eolIndent: String,
        )

        fun asDescriptorField(): EnumField.Reflective<Enum> {
            return when (this) {
                is UnknownField<Enum> -> this
                is ConstantField<Enum, *> -> this
                is WrapperField<Enum, *> -> this
            }
        }
    }

    private class UnknownField<Enum : Any>(
        override val constant: Enum,
        val wrapUnrecognized: (UnrecognizedEnum<Enum>) -> Enum,
        private val getUnrecognized: (Enum) -> UnrecognizedEnum<Enum>?,
    ) : Field<Enum>(), EnumConstantField.Reflective<Enum> {
        override val kindOrdinal = 0
        override val number get() = 0
        override val name get() = "?"

        override fun toJson(
            input: Enum,
            readableFlavor: Boolean,
        ): JsonElement {
            return if (readableFlavor) {
                JsonPrimitive("?")
            } else {
                val unrecognized = getUnrecognized(input)?.jsonElement
                unrecognized ?: JsonPrimitive(0)
            }
        }

        override fun encode(
            input: Enum,
            buffer: Buffer,
        ) {
            val unrecognized = getUnrecognized(input)?.bytes
            if (unrecognized != null) {
                buffer.write(unrecognized)
            } else {
                buffer.writeByte(0)
            }
        }

        override fun appendString(
            input: Enum,
            out: StringBuilder,
            eolIndent: String,
        ) {
            val className = getClassNameWithoutPackage(input::class)!!.substringBeforeLast(".")
            out.append(className).append(".UNKNOWN")
        }
    }

    private class ConstantField<Enum : Any, Instance : Enum>(
        override val number: Int,
        override val name: String,
        override val kindOrdinal: Int,
        override val constant: Enum,
    ) : Field<Enum>(), EnumConstantField.Reflective<Enum> {
        override fun toJson(
            input: Enum,
            readableFlavor: Boolean,
        ): JsonElement {
            return if (readableFlavor) JsonPrimitive(name) else JsonPrimitive(number)
        }

        override fun encode(
            input: Enum,
            buffer: Buffer,
        ) {
            encodeInt32(number, buffer)
        }

        override fun appendString(
            input: Enum,
            out: StringBuilder,
            eolIndent: String,
        ) {
            val className = getClassNameWithoutPackage(input::class)
            out.append(className)
        }
    }

    private class WrapperField<Enum : Any, T>(
        override val number: Int,
        override val name: String,
        override val kindOrdinal: Int,
        val valueSerializer: Serializer<T>,
        val wrapFn: (T) -> Enum,
        val getValue: (Enum) -> T,
        val getKindOrdinal: (Enum) -> Int,
    ) : Field<Enum>(), EnumWrapperField.Reflective<Enum, T> {
        override fun toJson(
            input: Enum,
            readableFlavor: Boolean,
        ): JsonElement {
            val value = getValue(input)
            val valueToJson = valueSerializer.impl.toJson(value, readableFlavor)
            return if (readableFlavor) {
                JsonObject(
                    mapOf(
                        "kind" to JsonPrimitive(name),
                        "value" to valueToJson,
                    ),
                )
            } else {
                JsonArray(listOf(JsonPrimitive(number), valueToJson))
            }
        }

        override fun encode(
            input: Enum,
            buffer: Buffer,
        ) {
            val value = getValue(input)
            if (number < 5) {
                buffer.writeByte(250 + number)
            } else {
                buffer.writeByte(248)
                encodeInt32(number, buffer)
            }
            valueSerializer.impl.encode(value, buffer)
        }

        override fun appendString(
            input: Enum,
            out: StringBuilder,
            eolIndent: String,
        ) {
            val newEolIndent = eolIndent + INDENT_UNIT
            out.append(getClassNameWithoutPackage(input::class)).append('(').append(newEolIndent)
            val value = getValue(input)
            valueSerializer.impl.appendString(value, out, newEolIndent)
            out.append(eolIndent).append(')')
        }

        override val type: TypeDescriptor.Reflective
            get() = valueSerializer.impl.typeDescriptor

        override fun test(e: Enum): Boolean {
            return getKindOrdinal(e) == kindOrdinal
        }

        override fun get(e: Enum): T = getValue(e)

        override fun wrap(value: T): Enum = wrapFn(value)

        companion object {
            internal fun <Enum : Any, T> wrapFromJson(
                field: WrapperField<Enum, T>,
                json: JsonElement,
            ): Enum {
                val value = field.valueSerializer.fromJson(json)
                return field.wrap(value)
            }

            internal fun <Enum : Any, T> wrapDecoded(
                field: WrapperField<Enum, T>,
                buffer: BufferedSource,
                keepUnrecognizedFields: Boolean,
            ): Enum {
                val value = field.valueSerializer.impl.decode(buffer, keepUnrecognizedFields = keepUnrecognizedFields)
                return field.wrap(value)
            }
        }
    }

    private class RemovedNumber<Enum>(
        override val number: Int,
    ) : FieldOrRemoved<Enum>()

    private fun addFieldImpl(field: Field<Enum>) {
        mutableFields.add(field.asDescriptorField())
        numberToField[field.number] = field
        nameToField[field.name] = field
        kindOrdinalToField[field.kindOrdinal] = field
    }

    fun finalizeEnum() {
        checkNotFinalized()
        addFieldImpl(unknown)
        finalized = true
    }

    private fun checkNotFinalized() {
        if (finalized) {
            throw IllegalStateException("Enum is already finalized")
        }
    }

    private val mutableFields = mutableListOf<EnumField.Reflective<Enum>>()
    private val mutableRemovedNumbers = mutableSetOf<Int>()
    private val numberToField = mutableMapOf<Int, FieldOrRemoved<Enum>>()
    private val nameToField = mutableMapOf<String, Field<Enum>>()
    private val kindOrdinalToField = MutableList<Field<Enum>?>(kindCount) { null }
    private var finalized = false

    override fun isDefault(value: Enum): Boolean {
        return value === unknown.constant
    }

    override fun toJson(
        input: Enum,
        readableFlavor: Boolean,
    ): JsonElement {
        val field = kindOrdinalToField[getKindOrdinal(input)]!!
        return field.toJson(input, readableFlavor = readableFlavor)
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedFields: Boolean,
    ): Enum {
        return when (json) {
            is JsonPrimitive -> {
                val number = json.intOrNull
                val field =
                    if (number != null) {
                        numberToField[number]
                    } else {
                        nameToField[json.content]
                    }
                when (field) {
                    is UnknownField<Enum> -> unknown.constant
                    is ConstantField<Enum, *> -> field.constant
                    is RemovedNumber<Enum> -> unknown.constant
                    is WrapperField<Enum, *> -> throw IllegalArgumentException("${field.number} refers to a wrapper field")
                    null ->
                        if (keepUnrecognizedFields && number != null) {
                            unknown.wrapUnrecognized(UnrecognizedEnum(json))
                        } else {
                            unknown.constant
                        }
                }
            }
            is JsonArray -> {
                val first = json[0].jsonPrimitive
                val number = first.intOrNull
                val field =
                    if (number != null) {
                        numberToField[number]
                    } else {
                        nameToField[first.content]
                    }
                return when (field) {
                    is UnknownField<Enum>, is ConstantField<Enum, *> -> throw IllegalArgumentException("$number refers to a constant field")
                    is RemovedNumber<Enum> -> unknown.constant
                    is WrapperField<Enum, *> -> {
                        val second = json[1]
                        WrapperField.wrapFromJson(field, second)
                    }
                    null ->
                        if (keepUnrecognizedFields && number != null) {
                            unknown.wrapUnrecognized(UnrecognizedEnum(json))
                        } else {
                            unknown.constant
                        }
                }
            }
            is JsonObject -> {
                val name = json["kind"]!!.jsonPrimitive.content
                val value = json["value"]!!
                return when (val field = nameToField[name]) {
                    is UnknownField<Enum>, is ConstantField<Enum, *> -> throw IllegalArgumentException("$name refers to a constant field")
                    is WrapperField<Enum, *> -> WrapperField.wrapFromJson(field, value)
                    null -> unknown.constant
                }
            }
        }
    }

    override fun encode(
        input: Enum,
        buffer: Buffer,
    ) {
        val field = kindOrdinalToField[getKindOrdinal(input)]!!
        field.encode(input, buffer)
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedFields: Boolean,
    ): Enum {
        val wire = buffer.buffer.readByte().toInt() and 0xFF
        if (wire < 242) {
            // A number: rewind
            val number = decodeNumberPastWire(buffer, wire).toInt()
            return when (val field = numberToField[number]) {
                is RemovedNumber -> unknown.constant
                is UnknownField -> unknown.constant
                is ConstantField<Enum, *> -> field.constant
                is WrapperField<Enum, *> -> throw IllegalArgumentException("${field.number} refers to a wrapper field")
                null -> {
                    if (keepUnrecognizedFields) {
                        val bytes = Buffer()
                        encodeInt32(number, bytes)
                        unknown.wrapUnrecognized(UnrecognizedEnum(bytes.readByteString()))
                    } else {
                        unknown.constant
                    }
                }
            }
        } else {
            val number = if (wire == 248) decodeNumber(buffer).toInt() else wire - 250
            return when (val field = numberToField[number]) {
                is RemovedNumber -> {
                    decodeUnused(buffer)
                    unknown.constant
                }
                is UnknownField, is ConstantField<Enum, *> -> throw IllegalArgumentException("$number refers to a constant field")
                is WrapperField<Enum, *> ->
                    WrapperField.wrapDecoded(
                        field,
                        buffer,
                        keepUnrecognizedFields = keepUnrecognizedFields,
                    )
                null -> {
                    if (keepUnrecognizedFields) {
                        val unrecognizedBytes = okio.Buffer()
                        if (number in 1..4) {
                            unrecognizedBytes.writeByte(wire + 250)
                        } else {
                            unrecognizedBytes.writeByte(248)
                            encodeInt32(number, unrecognizedBytes)
                        }
                        val peekBuffer = buffer.peek()
                        val byteCount = decodeUnused(peekBuffer)
                        unrecognizedBytes.write(buffer.readByteString(byteCount))
                        unknown.wrapUnrecognized(UnrecognizedEnum(unrecognizedBytes.readByteString()))
                    } else {
                        decodeUnused(buffer)
                        unknown.constant
                    }
                }
            }
        }
    }

    override fun appendString(
        input: Enum,
        out: StringBuilder,
        eolIndent: String,
    ) {
        val field = kindOrdinalToField[getKindOrdinal(input)]!!
        field.appendString(input, out, eolIndent)
    }

    // =========================================================================
    // REFLECTION: BEGIN
    // =========================================================================

    override val fields: List<EnumField.Reflective<Enum>>
        get() =
            java.util.Collections.unmodifiableList(
                // Exclude the 'unknown' field which is always at the end.
                mutableFields.subList(0, mutableFields.size - 1),
            )

    override val removedNumbers: Set<Int>
        get() = java.util.Collections.unmodifiableSet(mutableRemovedNumbers)

    override fun fieldDefinitions(): List<JsonObject> {
        return (
            nameToField
                .values
                .map {
                    when (it) {
                        is UnknownField -> null
                        is ConstantField<Enum, *> -> {
                            JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive(it.name),
                                    "number" to JsonPrimitive(it.number),
                                ),
                            )
                        }
                        is WrapperField<Enum, *> -> {
                            JsonObject(
                                mapOf(
                                    "name" to JsonPrimitive(it.name),
                                    "number" to JsonPrimitive(it.number),
                                    "type" to it.valueSerializer.impl.typeSignature,
                                ),
                            )
                        }
                    }
                }
                .filterIsInstance<JsonObject>()
                .toList()
        )
    }

    override fun dependencies(): List<SerializerImpl<*>> {
        return fields.filterIsInstance<WrapperField<*, *>>().map { it.valueSerializer.impl }
    }

    override fun getField(name: String): EnumField.Reflective<Enum>? {
        val field = nameToField[name]
        return field?.asDescriptorField()
    }

    override fun getField(number: Int): EnumField.Reflective<Enum>? {
        return when (val field = numberToField[number]) {
            is Field<Enum> -> field.asDescriptorField()
            null, is RemovedNumber<Enum> -> null
        }
    }

    override fun getField(e: Enum): EnumField.Reflective<Enum> {
        val field = kindOrdinalToField[getKindOrdinal(e)]!!
        return field.asDescriptorField()
    }

    public override val typeDescriptor: EnumDescriptor.Reflective<Enum> get() = this

    // =========================================================================
    // REFLECTION: END
    // =========================================================================
}
