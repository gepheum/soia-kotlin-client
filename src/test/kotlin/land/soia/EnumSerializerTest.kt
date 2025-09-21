package land.soia

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import land.soia.internal.EnumSerializer
import land.soia.internal.UnrecognizedEnum
import land.soia.internal.toStringImpl
import land.soia.reflection.asJson
import land.soia.reflection.asJsonCode
import land.soia.reflection.parseTypeDescriptor
import org.junit.jupiter.api.Test

class EnumSerializerTest {
    // Test enum types
    sealed class Color {
        data class Unknown(val unrecognized: UnrecognizedEnum<Color>?) : Color()

        object RED : Color()

        object GREEN : Color()

        object BLUE : Color()

        data class CustomOption(val rgb: Int) : Color()

        companion object {
            val UNKNOWN = Unknown(null)
        }
    }

    sealed class Status {
        data class Unknown(val unrecognized: UnrecognizedEnum<Status>) : Status()

        object ACTIVE : Status()

        object INACTIVE : Status()

        data class PendingOption(val reason: String) : Status()

        data class ErrorOption(val message: String) : Status()
    }

    // Simple enum with only constants
    private val colorEnumSerializer =
        EnumSerializer.create<Color, Color.Unknown>(
            "foo.bar:Color",
            Color.UNKNOWN,
            { unrecognized -> Color.Unknown(unrecognized) },
            { enum -> enum.unrecognized },
        ).apply {
            addConstantField(1, "red", Color.RED)
            addConstantField(2, "green", Color.GREEN)
            addConstantField(3, "blue", Color.BLUE)
            addValueField(4, "custom", Color.CustomOption::class.java, Serializers.int32, { Color.CustomOption(it) }, { it.rgb })
            finalizeEnum()
        }

    // Complex enum with both constants and value fields
    private val statusEnumSerializer =
        EnumSerializer.create<Status, Status.Unknown>(
            "foo.bar:Color.Status",
            Status.Unknown(UnrecognizedEnum(JsonPrimitive(0))),
            { unrecognized -> Status.Unknown(unrecognized) },
            { enum -> enum.unrecognized },
        ).apply {
            addConstantField(1, "active", Status.ACTIVE)
            addConstantField(2, "inactive", Status.INACTIVE)
            addValueField(3, "pending", Status.PendingOption::class.java, Serializers.string, { Status.PendingOption(it) }, { it.reason })
            addRemovedNumber(4) // Removed field number
            finalizeEnum()
        }

    private val colorSerializer = Serializer(colorEnumSerializer)
    private val statusSerializer = Serializer(statusEnumSerializer)

    @Test
    fun `test enum serializer - constant fields dense JSON`() {
        // Test constant field serialization in dense format
        val redJson = colorEnumSerializer.toJson(Color.RED, readableFlavor = false)
        assertThat(redJson).isInstanceOf(JsonPrimitive::class.java)
        assertThat((redJson as JsonPrimitive).content).isEqualTo("1")

        val greenJson = colorEnumSerializer.toJson(Color.GREEN, readableFlavor = false)
        assertThat((greenJson as JsonPrimitive).content).isEqualTo("2")

        val blueJson = colorEnumSerializer.toJson(Color.BLUE, readableFlavor = false)
        assertThat((blueJson as JsonPrimitive).content).isEqualTo("3")

        // Test deserialization from dense format
        assertThat(colorEnumSerializer.fromJson(JsonPrimitive(1), keepUnrecognizedFields = false)).isEqualTo(Color.RED)
        assertThat(colorEnumSerializer.fromJson(JsonPrimitive(2), keepUnrecognizedFields = false)).isEqualTo(Color.GREEN)
        assertThat(colorEnumSerializer.fromJson(JsonPrimitive(3), keepUnrecognizedFields = false)).isEqualTo(Color.BLUE)
    }

    @Test
    fun `test enum serializer - constant fields readable JSON`() {
        // Test constant field serialization in readable format
        val redJson = colorEnumSerializer.toJson(Color.RED, readableFlavor = true)
        assertThat(redJson).isInstanceOf(JsonPrimitive::class.java)
        assertThat((redJson as JsonPrimitive).content).isEqualTo("red")

        val greenJson = colorEnumSerializer.toJson(Color.GREEN, readableFlavor = true)
        assertThat((greenJson as JsonPrimitive).content).isEqualTo("green")

        val blueJson = colorEnumSerializer.toJson(Color.BLUE, readableFlavor = true)
        assertThat((blueJson as JsonPrimitive).content).isEqualTo("blue")

        // Test deserialization from readable format
        assertThat(colorEnumSerializer.fromJson(JsonPrimitive("red"), keepUnrecognizedFields = false)).isEqualTo(Color.RED)
        assertThat(colorEnumSerializer.fromJson(JsonPrimitive("green"), keepUnrecognizedFields = false)).isEqualTo(Color.GREEN)
        assertThat(colorEnumSerializer.fromJson(JsonPrimitive("blue"), keepUnrecognizedFields = false)).isEqualTo(Color.BLUE)
    }

    @Test
    fun `test enum serializer - value fields dense JSON`() {
        val customColor = Color.CustomOption(0xFF0000)

        // Test value field serialization in dense format
        val customJson = colorEnumSerializer.toJson(customColor, readableFlavor = false)
        assertThat(customJson).isInstanceOf(JsonArray::class.java)

        val jsonArray = customJson as JsonArray
        assertThat(jsonArray).hasSize(2)
        assertThat((jsonArray[0] as JsonPrimitive).content).isEqualTo("4")
        assertThat((jsonArray[1] as JsonPrimitive).content).isEqualTo("16711680")

        // Test deserialization from dense format
        val restored = colorEnumSerializer.fromJson(jsonArray, keepUnrecognizedFields = false)
        assertThat(restored).isInstanceOf(Color.CustomOption::class.java)
        assertThat((restored as Color.CustomOption).rgb).isEqualTo(0xFF0000)
    }

    @Test
    fun `test enum serializer - value fields readable JSON`() {
        val customColor = Color.CustomOption(0x00FF00)

        // Test value field serialization in readable format
        val customJson = colorEnumSerializer.toJson(customColor, readableFlavor = true)
        assertThat(customJson).isInstanceOf(JsonObject::class.java)

        val jsonObject = customJson as JsonObject
        assertThat(jsonObject).hasSize(2)
        assertThat(jsonObject).containsKey("kind")
        assertThat(jsonObject).containsKey("value")
        assertThat((jsonObject["kind"] as JsonPrimitive).content).isEqualTo("custom")
        assertThat((jsonObject["value"] as JsonPrimitive).content).isEqualTo("65280")

        // Test deserialization from readable format
        val restored = colorEnumSerializer.fromJson(jsonObject, keepUnrecognizedFields = false)
        assertThat(restored).isInstanceOf(Color.CustomOption::class.java)
        assertThat((restored as Color.CustomOption).rgb).isEqualTo(0x00FF00)
    }

    @Test
    fun `test enum serializer - binary format constants`() {
        // Test binary encoding for constant fields
        val redBytes = colorSerializer.toBytes(Color.RED)
        assertThat(redBytes.hex()).startsWith("736f6961")
        // Red should encode as field number 1

        val greenBytes = colorSerializer.toBytes(Color.GREEN)
        assertThat(greenBytes.hex()).startsWith("736f6961")
        // Green should encode as field number 2

        val blueBytes = colorSerializer.toBytes(Color.BLUE)
        assertThat(blueBytes.hex()).startsWith("736f6961")
        // Blue should encode as field number 3

        // Test binary roundtrips
        assertThat(colorSerializer.fromBytes(redBytes.toByteArray())).isEqualTo(Color.RED)
        assertThat(colorSerializer.fromBytes(greenBytes.toByteArray())).isEqualTo(Color.GREEN)
        assertThat(colorSerializer.fromBytes(blueBytes.toByteArray())).isEqualTo(Color.BLUE)
    }

    @Test
    fun `test enum serializer - binary format value fields`() {
        val customColor = Color.CustomOption(42)

        // Test binary encoding for value fields
        val customBytes = colorSerializer.toBytes(customColor)
        assertThat(customBytes.hex()).startsWith("736f6961")

        // Test binary roundtrip
        val restored = colorSerializer.fromBytes(customBytes.toByteArray())
        assertThat(restored).isInstanceOf(Color.CustomOption::class.java)
        assertThat((restored as Color.CustomOption).rgb).isEqualTo(42)
    }

    @Test
    fun `test enum serializer - default detection`() {
        // Test isDefault
        assertThat(colorEnumSerializer.isDefault(Color.UNKNOWN)).isTrue()
        assertThat(colorEnumSerializer.isDefault(Color.Unknown(UnrecognizedEnum(JsonPrimitive(0))))).isFalse()
        assertThat(colorEnumSerializer.isDefault(Color.RED)).isFalse()
        assertThat(colorEnumSerializer.isDefault(Color.CustomOption(123))).isFalse()
    }

    @Test
    fun `test enum serializer - unknown values without keeping unrecognized`() {
        // Test unknown constant number
        val unknownConstant = colorEnumSerializer.fromJson(JsonPrimitive(99), keepUnrecognizedFields = false)
        assertThat(unknownConstant).isInstanceOf(Color.Unknown::class.java)

        // Test unknown field name
        val unknownName = colorEnumSerializer.fromJson(JsonPrimitive("purple"), keepUnrecognizedFields = false)
        assertThat(unknownName).isInstanceOf(Color.Unknown::class.java)

        // Test unknown value field
        val unknownValue =
            colorEnumSerializer.fromJson(
                JsonArray(listOf(JsonPrimitive(99), JsonPrimitive(123))),
                keepUnrecognizedFields = false,
            )
        assertThat(unknownValue).isInstanceOf(Color.Unknown::class.java)
    }

    @Test
    fun `test enum serializer - unknown values with keeping unrecognized`() {
        // Test unknown constant number with keepUnrecognizedFields = true
        val unknownConstant = colorEnumSerializer.fromJson(JsonPrimitive(99), keepUnrecognizedFields = true)
        assertThat(unknownConstant).isInstanceOf(Color.Unknown::class.java)
        val unknownEnum = (unknownConstant as Color.Unknown).unrecognized
        assertThat(unknownEnum?.jsonElement).isEqualTo(JsonPrimitive(99))

        // Test unknown value field with keepUnrecognizedFields = true
        val unknownValueJson = JsonArray(listOf(JsonPrimitive(99), JsonPrimitive(123)))
        val unknownValue = colorEnumSerializer.fromJson(unknownValueJson, keepUnrecognizedFields = true)
        assertThat(unknownValue).isInstanceOf(Color.Unknown::class.java)
        val unknownValueEnum = (unknownValue as Color.Unknown).unrecognized
        assertThat(unknownValueEnum?.jsonElement).isEqualTo(unknownValueJson)
    }

    @Test
    fun `test enum serializer - removed fields`() {
        // Test accessing a removed field (should return unknown)
        val removedField = statusEnumSerializer.fromJson(JsonPrimitive(4), keepUnrecognizedFields = false)
        assertThat(removedField).isInstanceOf(Status.Unknown::class.java)
    }

    @Test
    fun `test enum serializer - complex enum roundtrips`() {
        val pendingStatus = Status.PendingOption("waiting for approval")

        // Test JSON roundtrips
        val denseJson = statusSerializer.toJsonCode(pendingStatus, readableFlavor = false)
        val readableJson = statusSerializer.toJsonCode(pendingStatus, readableFlavor = true)

        val restoredFromDense = statusSerializer.fromJsonCode(denseJson)
        val restoredFromReadable = statusSerializer.fromJsonCode(readableJson)

        assertThat(restoredFromDense).isInstanceOf(Status.PendingOption::class.java)
        assertThat((restoredFromDense as Status.PendingOption).reason).isEqualTo("waiting for approval")

        assertThat(restoredFromReadable).isInstanceOf(Status.PendingOption::class.java)
        assertThat((restoredFromReadable as Status.PendingOption).reason).isEqualTo("waiting for approval")

        // Test binary roundtrip
        val bytes = statusSerializer.toBytes(pendingStatus)
        val restoredFromBinary = statusSerializer.fromBytes(bytes.toByteArray())

        assertThat(restoredFromBinary).isInstanceOf(Status.PendingOption::class.java)
        assertThat((restoredFromBinary as Status.PendingOption).reason).isEqualTo("waiting for approval")
    }

    @Test
    fun `test enum serializer - all constant types roundtrip`() {
        val constantValues = listOf(Status.ACTIVE, Status.INACTIVE)

        constantValues.forEach { status ->
            // Test JSON roundtrips
            val denseJson = statusSerializer.toJsonCode(status, readableFlavor = false)
            val readableJson = statusSerializer.toJsonCode(status, readableFlavor = true)

            val restoredFromDense = statusSerializer.fromJsonCode(denseJson)
            val restoredFromReadable = statusSerializer.fromJsonCode(readableJson)

            assertThat(restoredFromDense).isEqualTo(status)
            assertThat(restoredFromReadable).isEqualTo(status)

            // Test binary roundtrip
            val bytes = statusSerializer.toBytes(status)
            val restoredFromBinary = statusSerializer.fromBytes(bytes.toByteArray())

            assertThat(restoredFromBinary).isEqualTo(status)
        }
    }

    @Test
    fun `test enum serializer - error cases`() {
        // Test that finalizeEnum() can only be called once
        val testEnumSerializer =
            EnumSerializer.create<Color, Color.Unknown>(
                "foo.bar:Color",
                Color.Unknown(UnrecognizedEnum(JsonPrimitive(0))),
                { unrecognized -> Color.Unknown(unrecognized) },
                { enum -> enum.unrecognized },
            )

        testEnumSerializer.addConstantField(1, "test", Color.RED)
        testEnumSerializer.finalizeEnum()

        // Adding fields after finalization should throw
        var exceptionThrown = false
        try {
            testEnumSerializer.addConstantField(2, "test2", Color.GREEN)
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertThat(exceptionThrown).isTrue()

        // Double finalization should throw
        exceptionThrown = false
        try {
            testEnumSerializer.finalizeEnum()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertThat(exceptionThrown).isTrue()
    }

    @Test
    fun `test enum serializer - edge cases`() {
        // Test with edge case values
        val edgeCases =
            listOf(
                Color.CustomOption(0),
                Color.CustomOption(Int.MAX_VALUE),
                Color.CustomOption(Int.MIN_VALUE),
            )

        edgeCases.forEach { color ->
            // Test JSON roundtrips
            val denseJson = colorSerializer.toJsonCode(color, readableFlavor = false)
            val readableJson = colorSerializer.toJsonCode(color, readableFlavor = true)

            val restoredFromDense = colorSerializer.fromJsonCode(denseJson)
            val restoredFromReadable = colorSerializer.fromJsonCode(readableJson)

            assertThat(restoredFromDense).isInstanceOf(Color.CustomOption::class.java)
            assertThat((restoredFromDense as Color.CustomOption).rgb).isEqualTo(color.rgb)

            assertThat(restoredFromReadable).isInstanceOf(Color.CustomOption::class.java)
            assertThat((restoredFromReadable as Color.CustomOption).rgb).isEqualTo(color.rgb)

            // Test binary roundtrip
            val bytes = colorSerializer.toBytes(color)
            val restoredFromBinary = colorSerializer.fromBytes(bytes.toByteArray())

            assertThat(restoredFromBinary).isInstanceOf(Color.CustomOption::class.java)
            assertThat((restoredFromBinary as Color.CustomOption).rgb).isEqualTo(color.rgb)
        }
    }

    @Test
    fun `test enum serializer - json format consistency`() {
        // Test that dense and readable formats are different for value fields but same for constants
        val redConstant = Color.RED
        val customValue = Color.CustomOption(0xABCDEF)

        // Constants should be different between dense/readable
        val redDenseJson = colorSerializer.toJsonCode(redConstant, readableFlavor = false)
        val redReadableJson = colorSerializer.toJsonCode(redConstant, readableFlavor = true)
        assertThat(redDenseJson).isNotEqualTo(redReadableJson)

        // Value fields should be different between dense/readable
        val customDenseJson = colorSerializer.toJsonCode(customValue, readableFlavor = false)
        val customReadableJson = colorSerializer.toJsonCode(customValue, readableFlavor = true)
        assertThat(customDenseJson).isNotEqualTo(customReadableJson)

        // Dense should be array/number, readable should be string/object
        assertThat(redDenseJson.toLongOrNull()).isNotNull()
        assertThat(redReadableJson).startsWith("\"")
        assertThat(redReadableJson).endsWith("\"")

        assertThat(customDenseJson).startsWith("[")
        assertThat(customDenseJson).endsWith("]")
        assertThat(customReadableJson).startsWith("{")
        assertThat(customReadableJson).endsWith("}")
    }

    @Test
    fun `test enum serializer - field number ranges`() {
        // Test that field numbers work correctly for different ranges
        // Using the existing colorEnumSerializer which has field numbers 1, 2, 3, 4

        val constantValues = listOf(Color.RED, Color.GREEN, Color.BLUE)
        constantValues.forEach { constant ->
            val bytes = colorSerializer.toBytes(constant)
            val restored = colorSerializer.fromBytes(bytes.toByteArray())
            assertThat(restored).isEqualTo(constant)
        }

        // Test value field
        val customColor = Color.CustomOption(42)
        val bytes = colorSerializer.toBytes(customColor)
        val restored = colorSerializer.fromBytes(bytes.toByteArray())
        assertThat(restored).isInstanceOf(Color.CustomOption::class.java)
        assertThat((restored as Color.CustomOption).rgb).isEqualTo(42)
    }

    @Test
    fun `test enum serializer - multiple value serializers`() {
        // Test with the existing Status enum that already has multiple serializer types
        val testCases =
            listOf(
                Status.ACTIVE,
                Status.INACTIVE,
                Status.PendingOption("waiting for approval"),
            )

        testCases.forEach { testCase ->
            // Test JSON roundtrips
            val denseJson = statusSerializer.toJsonCode(testCase, readableFlavor = false)
            val readableJson = statusSerializer.toJsonCode(testCase, readableFlavor = true)

            val restoredFromDense = statusSerializer.fromJsonCode(denseJson)
            val restoredFromReadable = statusSerializer.fromJsonCode(readableJson)

            assertThat(restoredFromDense).isEqualTo(testCase)
            assertThat(restoredFromReadable).isEqualTo(testCase)

            // Test binary roundtrip
            val bytes = statusSerializer.toBytes(testCase)
            val restoredFromBinary = statusSerializer.fromBytes(bytes.toByteArray())

            assertThat(restoredFromBinary).isEqualTo(testCase)
        }
    }

    @Test
    fun `test enum serializer - toString()`() {
        assertThat(
            toStringImpl(Status.ACTIVE, statusSerializer.impl),
        ).isEqualTo("EnumSerializerTest.Status.ACTIVE")

        assertThat(
            toStringImpl(Status.PendingOption("foo\nbar"), statusSerializer.impl),
        ).isEqualTo(
            "EnumSerializerTest.Status.PendingOption(\n" +
                "  \"foo\\n\" +\n    \"bar\"\n" +
                ")",
        )
    }

    @Test
    fun `test enum serializer - type descriptor`() {
        val expectedJson =
            "{\n" +
                "  \"records\": [\n" +
                "    {\n" +
                "      \"kind\": \"enum\",\n" +
                "      \"id\": \"foo.bar:Color.Status\",\n" +
                "      \"fields\": [\n" +
                "        {\n" +
                "          \"name\": \"active\",\n" +
                "          \"number\": 1\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"inactive\",\n" +
                "          \"number\": 2\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"pending\",\n" +
                "          \"number\": 3,\n" +
                "          \"type\": {\n" +
                "            \"kind\": \"primitive\",\n" +
                "            \"value\": \"string\"\n" +
                "          }\n" +
                "        },\n" +
                "        {\n" +
                "          \"name\": \"?\",\n" +
                "          \"number\": 0\n" +
                "        }\n" +
                "      ],\n" +
                "      \"removed_fields\": [\n" +
                "        4\n" +
                "      ]\n" +
                "    }\n" +
                "  ],\n" +
                "  \"type\": {\n" +
                "    \"kind\": \"record\",\n" +
                "    \"value\": \"foo.bar:Color.Status\"\n" +
                "  }\n" +
                "}"
        assertThat(
            statusSerializer.typeDescriptor.asJsonCode(),
        ).isEqualTo(
            expectedJson,
        )
        assertThat(
            parseTypeDescriptor(statusSerializer.typeDescriptor.asJson()).asJsonCode(),
        ).isEqualTo(
            expectedJson,
        )
    }
}
