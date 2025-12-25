package build.skir.internal

import build.skir.Serializer
import build.skir.reflection.EnumConstantField
import build.skir.reflection.EnumDescriptor
import build.skir.reflection.EnumVariant
import build.skir.reflection.EnumWrapperVariant
import build.skir.reflection.TypeDescriptor
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import okio.Buffer
import okio.BufferedSource

class EnumSerializer<Enum : Any> private constructor(
    recordId: String,
    override val doc: String,
    private val getKindOrdinal: (Enum) -> Int,
    kindCount: Int,
    private val unknown: UnknownVariant<Enum>,
) : RecordSerializer<Enum, EnumVariant.Reflective<Enum>>(), EnumDescriptor.Reflective<Enum> {
    override val parsedRecordId = RecordId.parse(recordId)

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun <Enum : Any, Unknown : Enum> create(
            recordId: String,
            doc: String,
            getKindOrdinal: (Enum) -> Int,
            kindCount: Int,
            unknownInstance: Unknown,
            wrapUnrecognized: (UnrecognizedEnum<Enum>) -> Unknown,
            getUnrecognized: (Unknown) -> UnrecognizedEnum<Enum>?,
        ) = EnumSerializer(
            recordId,
            doc,
            getKindOrdinal,
            kindCount,
            unknown =
                UnknownVariant(
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
        doc: String,
        instance: Enum,
    ) {
        checkNotFinalized()
        addFieldImpl(ConstantField(number, name, kindOrdinal, doc, instance))
    }

    fun <Instance : Enum, T> addWrapperField(
        number: Int,
        name: String,
        kindOrdinal: Int,
        valueSerializer: Serializer<T>,
        doc: String,
        wrap: (T) -> Instance,
        getValue: (Instance) -> T,
    ) {
        checkNotFinalized()
        @Suppress("UNCHECKED_CAST")
        addFieldImpl(WrapperVariant(number, name, kindOrdinal, valueSerializer, doc, wrap, getValue as (Enum) -> T, getKindOrdinal))
    }

    fun addRemovedNumber(number: Int) {
        checkNotFinalized()
        mutableRemovedNumbers.add(number)
        numberToVariant[number] = RemovedNumber(number)
    }

    private sealed class FieldOrRemoved<Enum> {
        abstract val number: Int
    }

    private sealed class Variant<Enum : Any> : FieldOrRemoved<Enum>() {
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

        fun asDescriptorField(): EnumVariant.Reflective<Enum> {
            return when (this) {
                is UnknownVariant<Enum> -> this
                is ConstantField<Enum> -> this
                is WrapperVariant<Enum, *> -> this
            }
        }
    }

    private class UnknownVariant<Enum : Any>(
        override val constant: Enum,
        val wrapUnrecognized: (UnrecognizedEnum<Enum>) -> Enum,
        private val getUnrecognized: (Enum) -> UnrecognizedEnum<Enum>?,
    ) : Variant<Enum>(), EnumConstantField.Reflective<Enum> {
        override val kindOrdinal = 0
        override val number get() = 0
        override val name get() = "?"
        override val doc get() = ""

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

    private class ConstantField<Enum : Any>(
        override val number: Int,
        override val name: String,
        override val kindOrdinal: Int,
        override val doc: String,
        override val constant: Enum,
    ) : Variant<Enum>(), EnumConstantField.Reflective<Enum> {
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

    private class WrapperVariant<Enum : Any, T>(
        override val number: Int,
        override val name: String,
        override val kindOrdinal: Int,
        val valueSerializer: Serializer<T>,
        override val doc: String,
        val wrapFn: (T) -> Enum,
        val getValue: (Enum) -> T,
        val getKindOrdinal: (Enum) -> Int,
    ) : Variant<Enum>(), EnumWrapperVariant.Reflective<Enum, T> {
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

        override val type: TypeDescriptor.Reflective<T>
            get() = valueSerializer.impl.typeDescriptor

        override fun test(e: Enum): Boolean {
            return getKindOrdinal(e) == kindOrdinal
        }

        override fun get(e: Enum): T = getValue(e)

        override fun wrap(value: T): Enum = wrapFn(value)

        companion object {
            internal fun <Enum : Any, T> wrapFromJson(
                variant: WrapperVariant<Enum, T>,
                json: JsonElement,
            ): Enum {
                val value = variant.valueSerializer.fromJson(json)
                return variant.wrap(value)
            }

            internal fun <Enum : Any, T> wrapDecoded(
                variant: WrapperVariant<Enum, T>,
                buffer: BufferedSource,
                keepUnrecognizedValues: Boolean,
            ): Enum {
                val value = variant.valueSerializer.impl.decode(buffer, keepUnrecognizedValues = keepUnrecognizedValues)
                return variant.wrap(value)
            }
        }
    }

    private class RemovedNumber<Enum>(
        override val number: Int,
    ) : FieldOrRemoved<Enum>()

    private fun addFieldImpl(variant: Variant<Enum>) {
        mutableFields.add(variant.asDescriptorField())
        numberToVariant[variant.number] = variant
        nameToField[variant.name] = variant
        kindOrdinalToVariant[variant.kindOrdinal] = variant
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

    private val mutableFields = mutableListOf<EnumVariant.Reflective<Enum>>()
    private val mutableRemovedNumbers = mutableSetOf<Int>()
    private val numberToVariant = mutableMapOf<Int, FieldOrRemoved<Enum>>()
    private val nameToField = mutableMapOf<String, Variant<Enum>>()
    private val kindOrdinalToVariant = MutableList<Variant<Enum>?>(kindCount) { null }
    private var finalized = false

    override fun isDefault(value: Enum): Boolean {
        return value === unknown.constant
    }

    override fun toJson(
        input: Enum,
        readableFlavor: Boolean,
    ): JsonElement {
        val variant = kindOrdinalToVariant[getKindOrdinal(input)]!!
        return variant.toJson(input, readableFlavor = readableFlavor)
    }

    override fun fromJson(
        json: JsonElement,
        keepUnrecognizedValues: Boolean,
    ): Enum {
        return when (json) {
            is JsonPrimitive -> {
                val number = json.intOrNull
                val variant =
                    if (number != null) {
                        numberToVariant[number]
                    } else {
                        nameToField[json.content]
                    }
                when (variant) {
                    is UnknownVariant<Enum> -> unknown.constant
                    is ConstantField<Enum> -> variant.constant
                    is RemovedNumber<Enum> -> unknown.constant
                    is WrapperVariant<Enum, *> -> throw IllegalArgumentException("${variant.number} refers to a wrapper variant")
                    null ->
                        if (keepUnrecognizedValues && number != null) {
                            unknown.wrapUnrecognized(UnrecognizedEnum(json))
                        } else {
                            unknown.constant
                        }
                }
            }
            is JsonArray -> {
                val first = json[0].jsonPrimitive
                val number = first.intOrNull
                val variant =
                    if (number != null) {
                        numberToVariant[number]
                    } else {
                        nameToField[first.content]
                    }
                return when (variant) {
                    is UnknownVariant<Enum>, is ConstantField<Enum> -> throw IllegalArgumentException(
                        "$number refers to a constant variant",
                    )
                    is RemovedNumber<Enum> -> unknown.constant
                    is WrapperVariant<Enum, *> -> {
                        val second = json[1]
                        WrapperVariant.wrapFromJson(variant, second)
                    }
                    null ->
                        if (keepUnrecognizedValues && number != null) {
                            unknown.wrapUnrecognized(UnrecognizedEnum(json))
                        } else {
                            unknown.constant
                        }
                }
            }
            is JsonObject -> {
                val name = json["kind"]!!.jsonPrimitive.content
                val value = json["value"]!!
                return when (val variant = nameToField[name]) {
                    is UnknownVariant<Enum>, is ConstantField<Enum> -> throw IllegalArgumentException("$name refers to a constant variant")
                    is WrapperVariant<Enum, *> -> WrapperVariant.wrapFromJson(variant, value)
                    null -> unknown.constant
                }
            }
        }
    }

    override fun encode(
        input: Enum,
        buffer: Buffer,
    ) {
        val variant = kindOrdinalToVariant[getKindOrdinal(input)]!!
        variant.encode(input, buffer)
    }

    override fun decode(
        buffer: BufferedSource,
        keepUnrecognizedValues: Boolean,
    ): Enum {
        val wire = buffer.buffer.readByte().toInt() and 0xFF
        if (wire < 242) {
            // A number: rewind
            val number = decodeNumberPastWire(buffer, wire).toInt()
            return when (val variant = numberToVariant[number]) {
                is RemovedNumber -> unknown.constant
                is UnknownVariant -> unknown.constant
                is ConstantField<Enum> -> variant.constant
                is WrapperVariant<Enum, *> -> throw IllegalArgumentException("${variant.number} refers to a wrapper variant")
                null -> {
                    if (keepUnrecognizedValues) {
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
            return when (val variant = numberToVariant[number]) {
                is RemovedNumber -> {
                    decodeUnused(buffer)
                    unknown.constant
                }
                is UnknownVariant, is ConstantField<Enum> -> throw IllegalArgumentException("$number refers to a constant variant")
                is WrapperVariant<Enum, *> ->
                    WrapperVariant.wrapDecoded(
                        variant,
                        buffer,
                        keepUnrecognizedValues = keepUnrecognizedValues,
                    )
                null -> {
                    if (keepUnrecognizedValues) {
                        val unrecognizedBytes = Buffer()
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
        val variant = kindOrdinalToVariant[getKindOrdinal(input)]!!
        variant.appendString(input, out, eolIndent)
    }

    // =========================================================================
    // REFLECTION: BEGIN
    // =========================================================================

    override val variants: List<EnumVariant.Reflective<Enum>>
        get() =
            java.util.Collections.unmodifiableList(
                // Exclude the 'unknown' variant which is always at the end.
                mutableFields.subList(0, mutableFields.size - 1),
            )

    override val removedNumbers: Set<Int>
        get() = java.util.Collections.unmodifiableSet(mutableRemovedNumbers)

    override fun getVariant(name: String): EnumVariant.Reflective<Enum>? {
        val variant = nameToField[name]
        return variant?.asDescriptorField()
    }

    override fun getVariant(number: Int): EnumVariant.Reflective<Enum>? {
        return when (val variant = numberToVariant[number]) {
            is Variant<Enum> -> variant.asDescriptorField()
            null, is RemovedNumber<Enum> -> null
        }
    }

    override fun getField(e: Enum): EnumVariant.Reflective<Enum> {
        val field = kindOrdinalToVariant[getKindOrdinal(e)]!!
        return field.asDescriptorField()
    }

    public override val typeDescriptor: EnumDescriptor.Reflective<Enum> get() = this

    // =========================================================================
    // REFLECTION: END
    // =========================================================================
}
