package soia.internal

import kotlinx.serialization.json.*
import okio.Buffer
import soia.Serializer
import soia.SerializerImpl

class EnumSerializer<Enum: Any>(
    private val default: Enum,
    private val wrapUnrecognized: (UnrecognizedEnum<Enum>) -> Enum,
) : SerializerImpl<Enum> {
    sealed class Field<Enum> {
        abstract val number: Int
    }

    data class ConstantField<Enum, Instance : Enum>(
        override val number: Int,
        val name: String,
        val instanceType: Class<Instance>,
        val instance: Instance,
    ): Field<Enum>() {
        companion object {
            internal  fun <Enum, Instance : Enum> getInstance(field: ConstantField<Enum, Instance>): Instance {
                return field.instance
            }

            internal  fun <Enum, Instance : Enum> toJson(field: ConstantField<Enum, Instance>, readableFlavor: Boolean): JsonElement {
                return if (readableFlavor) JsonPrimitive(field.name) else JsonPrimitive(field.number)
            }
        }
    }

    data class ValueField<Enum, Instance : Enum, T>(
        override val number: Int,
        val name: String,
        val instanceType: Class<Instance>,
        val valueSerializer: Serializer<T>,
        val wrap: (T) -> Instance,
        val getValue: (Instance) -> T,
    ): Field<Enum>() {
        companion object {
            internal  fun <Enum, Instance : Enum, T> wrapFromJson(field: ValueField<Enum, Instance, T>, json: JsonElement): Instance {
                val value = field.valueSerializer.fromJson(json)
                return field.wrap(value)
            }

            internal fun <Enum, Instance : Enum, T> toJson(field: ValueField<Enum, Instance, T>, input: Enum, readableFlavor: Boolean): JsonElement {
                @Suppress("UNCHECKED_CAST")
                val value = field.getValue(input as Instance)
                val valueToJson = field.valueSerializer.toJson(value)
                return if (readableFlavor) {JsonObject(mapOf(
                    "kind" to JsonPrimitive(field.name),
                    "value" to valueToJson,
                ))} else {JsonArray(listOf(JsonPrimitive(field.number), valueToJson))}
            }
        }
    }

    data class RemovedField<Enum>(
        override val number: Int,
    ): Field<Enum>() {}

    fun addField(field: Field<Enum>) {
        when (field) {
            is ConstantField<Enum, *> -> addFieldImpl(field)
            is ValueField<Enum, *, *> -> addFieldImpl(field)
            is RemovedField<Enum> -> addFieldImpl(field)
        }
    }

    private fun <Instance: Enum> addFieldImpl(field: ConstantField<Enum, Instance>) {
        numberToField[field.number] = field
        constantFields.add(field)
        nameToField[field.name] = field
        instanceTypeToField[field.instanceType] = field
    }

    private fun <Instance: Enum> addFieldImpl(field: ValueField<Enum, Instance, *>) {
        numberToField[field.number] = field
        valueFields.add(field)
        nameToField[field.name] = field
        instanceTypeToField[field.instanceType] = field
    }

    private fun addFieldImpl(field: RemovedField<Enum>) {
        numberToField[field.number] = field
    }

    fun finalizeEnum() {
        checkNotFinalized()
        finalized = true
    }

    private fun checkNotFinalized() {
        if (finalized) {
            throw IllegalStateException("Enum is already finalized")
        }
    }

    private val constantFields = mutableListOf<ConstantField<Enum, *>>()
    private val valueFields = mutableListOf<ValueField<Enum, *, *>>()
    private val numberToField = mutableMapOf<Int, Field<Enum>>()
    private val nameToField = mutableMapOf<String, Field<Enum>>()
    private val instanceTypeToField = mutableMapOf<Class<out Enum>, Field<Enum>>()
    private var finalized = false

    override fun isDefault(value: Enum): Boolean {
        return value === default
    }

    override fun toJson(input: Enum, readableFlavor: Boolean): JsonElement {
        return when (val field = instanceTypeToField[input.javaClass]!!) {
            is ConstantField<Enum, *> -> ConstantField.toJson(field, readableFlavor = readableFlavor)
            is ValueField<Enum, *, *> -> ValueField.toJson(field, input, readableFlavor = readableFlavor)
            is RemovedField<Enum> -> throw AssertionError()
        }
    }

    override fun fromJson(json: JsonElement, keepUnrecognizedFields: Boolean): Enum {
        return when (json) {
            is JsonPrimitive -> {
                val number = json.intOrNull
                val field = if (number != null) {
                    numberToField[number]
                } else {
                    nameToField[json.content]
                }
                when (field) {
                    is ConstantField<Enum, *> -> ConstantField.getInstance(field)
                    is RemovedField<Enum> -> default
                    is ValueField<Enum, *, *> -> throw IllegalArgumentException("${field.number} refers to a value field")
                    null -> if (number != null) {
                        wrapUnrecognized(UnrecognizedEnum(json))
                    } else {
                        default
                    }
                }
            }
            is JsonArray -> {
                val first = json[0].jsonPrimitive
                val number = first.intOrNull
                val field = if (number != null) {
                    numberToField[number]
                } else {
                    nameToField[first.content]
                }
                return when (field) {
                    is ConstantField<Enum, *> -> throw IllegalArgumentException("$number refers to a constant field")
                    is RemovedField<Enum> -> default
                    is ValueField<Enum, *, *> -> {
                        val second = json[1]
                        ValueField.wrapFromJson(field, second)
                    }
                    null -> if (number != null) {
                        wrapUnrecognized(UnrecognizedEnum(json))
                    } else {
                        default
                    }
                }
            }
            is JsonObject -> {
                val name = json["kind"]!!.jsonPrimitive.content
                val value = json["value"]!!
                return when (val field = nameToField[name]) {
                    is ConstantField<Enum, *> -> throw IllegalArgumentException("$name refers to a constant field")
                    is RemovedField<Enum> -> default
                    is ValueField<Enum, *, *> -> ValueField.wrapFromJson(field, value)
                    null -> default
                }
            }
        }
    }

    override fun encode(input: Enum, buffer: Buffer) {
        const unrecognized = //
        (input as AnyRecord)["^"] as UnrecognizedEnum | undefined;
        if (
            unrecognized &&
            unrecognized.bytes &&
            unrecognized.token === this.token
        ) {
            // Unrecognized field.
            stream.putBytes(unrecognized.bytes);
            return;
        }
        const kind = (input as AnyRecord).kind as string;
        if (kind === "?") {
            stream.writeUint8(0);
            return;
        }
        const field = this.fieldMapping[kind]!;
        const { number, serializer } = field;
        if (serializer) {
            // A value field.
            const value = (input as AnyRecord).value;
            if (number < 5) {
                // The number can't be 0 or else kind == "?".
                stream.writeUint8(250 + number);
            } else {
                stream.writeUint8(248);
                encodeUint32(number, stream);
            }
            serializer.encode(value, stream);
        } else {
            // A constant field.
            encodeUint32(number, stream);
        }
    }

    override fun decode(buffer: Buffer, keepUnrecognizedFields: Boolean): Enum {
        const startOffset = stream.offset;
        const wire = stream.dataView.getUint8(startOffset);
        if (wire < 242) {
            // A number
            const number = decodeNumber(stream) as number;
            const field = this.fieldMapping[number];
            if (!field) {
                // Check if the field was removed, in which case we want to return
                // UNKNOWN, or is unrecognized.
                if (!stream.keepUnrecognizedFields || this.removedNumbers.has(number)) {
                    return this.defaultValue;
                } else {
                    const { offset } = stream;
                    const bytes = ByteString.sliceOf(stream.buffer, startOffset, offset);
                    return this.createFn(
                        new UnrecognizedEnum(this.token, undefined, bytes),
                    );
                }
            }
            if (field.serializer) {
                throw new Error(`refers to a value field: ${number}`);
            }
            return field.constant;
        } else {
            ++stream.offset;
            const number =
            wire === 248 ? (decodeNumber(stream) as number) : wire - 250;
            const field = this.fieldMapping[number];
            if (!field) {
                decodeUnused(stream);
                // Check if the field was removed, in which case we want to return
                // UNKNOWN, or is unrecognized.
                if (!stream.keepUnrecognizedFields || this.removedNumbers.has(number)) {
                    return this.defaultValue;
                } else {
                    const { offset } = stream;
                    const bytes = ByteString.sliceOf(stream.buffer, startOffset, offset);
                    return this.createFn(
                        new UnrecognizedEnum(this.token, undefined, bytes),
                    );
                }
            }
            const { serializer } = field;
            if (!serializer) {
                throw new Error(`refers to a constant field: ${number}`);
            }
            return field.wrap(serializer.decode(stream));
    }
}
