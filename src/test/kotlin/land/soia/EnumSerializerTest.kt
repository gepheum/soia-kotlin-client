package land.soia

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import land.soia.internal.EnumSerializer
import land.soia.internal.UnrecognizedEnum
import land.soia.internal.toStringImpl
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class EnumSerializerTest {
    // Test enum types
    sealed class Color {
        object Red : Color()

        object Green : Color()

        object Blue : Color()

        data class Custom(val rgb: Int) : Color()

        data class Unknown(val unrecognized: UnrecognizedEnum<Color>?) : Color()

        companion object {
            val UNKNOWN = Unknown(null)
        }
    }

    sealed class Status {
        object Active : Status()

        object Inactive : Status()

        data class Pending(val reason: String) : Status()

        data class Error(val code: Int, val message: String) : Status()

        data class Unknown(val unrecognized: UnrecognizedEnum<Status>) : Status()
    }

    // Simple enum with only constants
    private val colorEnumSerializer =
        EnumSerializer.create<Color, Color.Unknown>(
            Color.UNKNOWN,
            { unrecognized -> Color.Unknown(unrecognized) },
            { enum -> enum.unrecognized },
        ).apply {
            addConstantField(1, "red", Color.Red)
            addConstantField(2, "green", Color.Green)
            addConstantField(3, "blue", Color.Blue)
            addValueField(4, "custom", Color.Custom::class.java, Serializers.int32, { Color.Custom(it) }, { it.rgb })
            finalizeEnum()
        }

    // Complex enum with both constants and value fields
    private val statusEnumSerializer =
        EnumSerializer.create<Status, Status.Unknown>(
            Status.Unknown(UnrecognizedEnum(JsonPrimitive(0))),
            { unrecognized -> Status.Unknown(unrecognized) },
            { enum -> enum.unrecognized },
        ).apply {
            addConstantField(1, "active", Status.Active)
            addConstantField(2, "inactive", Status.Inactive)
            addValueField(3, "pending", Status.Pending::class.java, Serializers.string, { Status.Pending(it) }, { it.reason })
            addRemovedNumber(4) // Removed field number
            finalizeEnum()
        }

    private val colorSerializer = Serializer(colorEnumSerializer)
    private val statusSerializer = Serializer(statusEnumSerializer)

    @Test
    fun `test enum serializer - constant fields dense JSON`() {
        // Test constant field serialization in dense format
        val redJson = colorEnumSerializer.toJson(Color.Red, readableFlavor = false)
        assertTrue(redJson is JsonPrimitive, "Dense constant should be JsonPrimitive")
        assertEquals("1", (redJson as JsonPrimitive).content, "Red should serialize to number 1")

        val greenJson = colorEnumSerializer.toJson(Color.Green, readableFlavor = false)
        assertEquals("2", (greenJson as JsonPrimitive).content, "Green should serialize to number 2")

        val blueJson = colorEnumSerializer.toJson(Color.Blue, readableFlavor = false)
        assertEquals("3", (blueJson as JsonPrimitive).content, "Blue should serialize to number 3")

        // Test deserialization from dense format
        assertEquals(Color.Red, colorEnumSerializer.fromJson(JsonPrimitive(1), keepUnrecognizedFields = false))
        assertEquals(Color.Green, colorEnumSerializer.fromJson(JsonPrimitive(2), keepUnrecognizedFields = false))
        assertEquals(Color.Blue, colorEnumSerializer.fromJson(JsonPrimitive(3), keepUnrecognizedFields = false))
    }

    @Test
    fun `test enum serializer - constant fields readable JSON`() {
        // Test constant field serialization in readable format
        val redJson = colorEnumSerializer.toJson(Color.Red, readableFlavor = true)
        assertTrue(redJson is JsonPrimitive, "Readable constant should be JsonPrimitive")
        assertEquals("red", (redJson as JsonPrimitive).content, "Red should serialize to name 'red'")

        val greenJson = colorEnumSerializer.toJson(Color.Green, readableFlavor = true)
        assertEquals("green", (greenJson as JsonPrimitive).content, "Green should serialize to name 'green'")

        val blueJson = colorEnumSerializer.toJson(Color.Blue, readableFlavor = true)
        assertEquals("blue", (blueJson as JsonPrimitive).content, "Blue should serialize to name 'blue'")

        // Test deserialization from readable format
        assertEquals(Color.Red, colorEnumSerializer.fromJson(JsonPrimitive("red"), keepUnrecognizedFields = false))
        assertEquals(Color.Green, colorEnumSerializer.fromJson(JsonPrimitive("green"), keepUnrecognizedFields = false))
        assertEquals(Color.Blue, colorEnumSerializer.fromJson(JsonPrimitive("blue"), keepUnrecognizedFields = false))
    }

    @Test
    fun `test enum serializer - value fields dense JSON`() {
        val customColor = Color.Custom(0xFF0000)

        // Test value field serialization in dense format
        val customJson = colorEnumSerializer.toJson(customColor, readableFlavor = false)
        assertTrue(customJson is JsonArray, "Dense value field should be JsonArray")

        val jsonArray = customJson as JsonArray
        assertEquals(2, jsonArray.size, "Dense value array should have 2 elements")
        assertEquals("4", (jsonArray[0] as JsonPrimitive).content, "First element should be field number")
        assertEquals("16711680", (jsonArray[1] as JsonPrimitive).content, "Second element should be the RGB value")

        // Test deserialization from dense format
        val restored = colorEnumSerializer.fromJson(jsonArray, keepUnrecognizedFields = false)
        assertTrue(restored is Color.Custom, "Should deserialize to Custom color")
        assertEquals(0xFF0000, (restored as Color.Custom).rgb, "RGB value should match")
    }

    @Test
    fun `test enum serializer - value fields readable JSON`() {
        val customColor = Color.Custom(0x00FF00)

        // Test value field serialization in readable format
        val customJson = colorEnumSerializer.toJson(customColor, readableFlavor = true)
        assertTrue(customJson is JsonObject, "Readable value field should be JsonObject")

        val jsonObject = customJson as JsonObject
        assertEquals(2, jsonObject.size, "Readable value object should have 2 fields")
        assertTrue(jsonObject.contains("kind"), "Should contain 'kind' field")
        assertTrue(jsonObject.contains("value"), "Should contain 'value' field")
        assertEquals("custom", (jsonObject["kind"] as JsonPrimitive).content, "Kind should be field name")
        assertEquals("65280", (jsonObject["value"] as JsonPrimitive).content, "Value should be RGB")

        // Test deserialization from readable format
        val restored = colorEnumSerializer.fromJson(jsonObject, keepUnrecognizedFields = false)
        assertTrue(restored is Color.Custom, "Should deserialize to Custom color")
        assertEquals(0x00FF00, (restored as Color.Custom).rgb, "RGB value should match")
    }

    @Test
    fun `test enum serializer - binary format constants`() {
        // Test binary encoding for constant fields
        val redBytes = colorSerializer.toBytes(Color.Red)
        assertTrue(redBytes.hex().startsWith("736f6961"), "Should start with soia prefix")
        // Red should encode as field number 1

        val greenBytes = colorSerializer.toBytes(Color.Green)
        assertTrue(greenBytes.hex().startsWith("736f6961"), "Should start with soia prefix")
        // Green should encode as field number 2

        val blueBytes = colorSerializer.toBytes(Color.Blue)
        assertTrue(blueBytes.hex().startsWith("736f6961"), "Should start with soia prefix")
        // Blue should encode as field number 3

        // Test binary roundtrips
        assertEquals(Color.Red, colorSerializer.fromBytes(redBytes.toByteArray()))
        assertEquals(Color.Green, colorSerializer.fromBytes(greenBytes.toByteArray()))
        assertEquals(Color.Blue, colorSerializer.fromBytes(blueBytes.toByteArray()))
    }

    @Test
    fun `test enum serializer - binary format value fields`() {
        val customColor = Color.Custom(42)

        // Test binary encoding for value fields
        val customBytes = colorSerializer.toBytes(customColor)
        assertTrue(customBytes.hex().startsWith("736f6961"), "Should start with soia prefix")

        // Test binary roundtrip
        val restored = colorSerializer.fromBytes(customBytes.toByteArray())
        assertTrue(restored is Color.Custom, "Should deserialize to Custom color")
        assertEquals(42, (restored as Color.Custom).rgb, "RGB value should match")
    }

    @Test
    fun `test enum serializer - default detection`() {
        // Test isDefault
        assertTrue(colorEnumSerializer.isDefault(Color.UNKNOWN))
        assertFalse(colorEnumSerializer.isDefault(Color.Unknown(UnrecognizedEnum(JsonPrimitive(0)))))
        assertFalse(colorEnumSerializer.isDefault(Color.Red), "Red should not be detected as default")
        assertFalse(colorEnumSerializer.isDefault(Color.Custom(123)), "Custom should not be detected as default")
    }

    @Test
    fun `test enum serializer - unknown values without keeping unrecognized`() {
        // Test unknown constant number
        val unknownConstant = colorEnumSerializer.fromJson(JsonPrimitive(99), keepUnrecognizedFields = false)
        assertTrue(unknownConstant is Color.Unknown, "Unknown constant should return Unknown instance")

        // Test unknown field name
        val unknownName = colorEnumSerializer.fromJson(JsonPrimitive("purple"), keepUnrecognizedFields = false)
        assertTrue(unknownName is Color.Unknown, "Unknown name should return Unknown instance")

        // Test unknown value field
        val unknownValue =
            colorEnumSerializer.fromJson(
                JsonArray(listOf(JsonPrimitive(99), JsonPrimitive(123))),
                keepUnrecognizedFields = false,
            )
        assertTrue(unknownValue is Color.Unknown, "Unknown value field should return Unknown instance")
    }

    @Test
    fun `test enum serializer - unknown values with keeping unrecognized`() {
        // Test unknown constant number with keepUnrecognizedFields = true
        val unknownConstant = colorEnumSerializer.fromJson(JsonPrimitive(99), keepUnrecognizedFields = true)
        assertTrue(unknownConstant is Color.Unknown, "Should return Unknown wrapper")
        val unknownEnum = (unknownConstant as Color.Unknown).unrecognized
        assertEquals(JsonPrimitive(99), unknownEnum?.jsonElement, "Should preserve JSON element")

        // Test unknown value field with keepUnrecognizedFields = true
        val unknownValueJson = JsonArray(listOf(JsonPrimitive(99), JsonPrimitive(123)))
        val unknownValue = colorEnumSerializer.fromJson(unknownValueJson, keepUnrecognizedFields = true)
        assertTrue(unknownValue is Color.Unknown, "Should return Unknown wrapper")
        val unknownValueEnum = (unknownValue as Color.Unknown).unrecognized
        assertEquals(unknownValueJson, unknownValueEnum?.jsonElement, "Should preserve JSON array")
    }

    @Test
    fun `test enum serializer - removed fields`() {
        // Test accessing a removed field (should return unknown)
        val removedField = statusEnumSerializer.fromJson(JsonPrimitive(4), keepUnrecognizedFields = false)
        assertTrue(removedField is Status.Unknown, "Removed field should return Unknown instance")
    }

    @Test
    fun `test enum serializer - complex enum roundtrips`() {
        val pendingStatus = Status.Pending("waiting for approval")

        // Test JSON roundtrips
        val denseJson = statusSerializer.toJsonCode(pendingStatus, readableFlavor = false)
        val readableJson = statusSerializer.toJsonCode(pendingStatus, readableFlavor = true)

        val restoredFromDense = statusSerializer.fromJsonCode(denseJson)
        val restoredFromReadable = statusSerializer.fromJsonCode(readableJson)

        assertTrue(restoredFromDense is Status.Pending, "Dense should deserialize to Pending")
        assertEquals("waiting for approval", (restoredFromDense as Status.Pending).reason, "Dense reason should match")

        assertTrue(restoredFromReadable is Status.Pending, "Readable should deserialize to Pending")
        assertEquals("waiting for approval", (restoredFromReadable as Status.Pending).reason, "Readable reason should match")

        // Test binary roundtrip
        val bytes = statusSerializer.toBytes(pendingStatus)
        val restoredFromBinary = statusSerializer.fromBytes(bytes.toByteArray())

        assertTrue(restoredFromBinary is Status.Pending, "Binary should deserialize to Pending")
        assertEquals("waiting for approval", (restoredFromBinary as Status.Pending).reason, "Binary reason should match")
    }

    @Test
    fun `test enum serializer - all constant types roundtrip`() {
        val constantValues = listOf(Status.Active, Status.Inactive)

        constantValues.forEach { status ->
            // Test JSON roundtrips
            val denseJson = statusSerializer.toJsonCode(status, readableFlavor = false)
            val readableJson = statusSerializer.toJsonCode(status, readableFlavor = true)

            val restoredFromDense = statusSerializer.fromJsonCode(denseJson)
            val restoredFromReadable = statusSerializer.fromJsonCode(readableJson)

            assertEquals(status, restoredFromDense, "Dense JSON roundtrip should match for $status")
            assertEquals(status, restoredFromReadable, "Readable JSON roundtrip should match for $status")

            // Test binary roundtrip
            val bytes = statusSerializer.toBytes(status)
            val restoredFromBinary = statusSerializer.fromBytes(bytes.toByteArray())

            assertEquals(status, restoredFromBinary, "Binary roundtrip should match for $status")
        }
    }

    @Test
    fun `test enum serializer - error cases`() {
        // Test that finalizeEnum() can only be called once
        val testEnumSerializer =
            EnumSerializer.create<Color, Color.Unknown>(
                Color.Unknown(UnrecognizedEnum(JsonPrimitive(0))),
                { unrecognized -> Color.Unknown(unrecognized) },
                { enum -> enum.unrecognized },
            )

        testEnumSerializer.addConstantField(1, "test", Color.Red)
        testEnumSerializer.finalizeEnum()

        // Adding fields after finalization should throw
        var exceptionThrown = false
        try {
            testEnumSerializer.addConstantField(2, "test2", Color.Green)
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown, "Should throw exception when adding fields after finalization")

        // Double finalization should throw
        exceptionThrown = false
        try {
            testEnumSerializer.finalizeEnum()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown, "Should throw exception when finalizing twice")
    }

    @Test
    fun `test enum serializer - edge cases`() {
        // Test with edge case values
        val edgeCases =
            listOf(
                Color.Custom(0),
                Color.Custom(Int.MAX_VALUE),
                Color.Custom(Int.MIN_VALUE),
            )

        edgeCases.forEach { color ->
            // Test JSON roundtrips
            val denseJson = colorSerializer.toJsonCode(color, readableFlavor = false)
            val readableJson = colorSerializer.toJsonCode(color, readableFlavor = true)

            val restoredFromDense = colorSerializer.fromJsonCode(denseJson)
            val restoredFromReadable = colorSerializer.fromJsonCode(readableJson)

            assertTrue(restoredFromDense is Color.Custom, "Dense should deserialize to Custom for $color")
            assertEquals(color.rgb, (restoredFromDense as Color.Custom).rgb, "Dense RGB should match for $color")

            assertTrue(restoredFromReadable is Color.Custom, "Readable should deserialize to Custom for $color")
            assertEquals(color.rgb, (restoredFromReadable as Color.Custom).rgb, "Readable RGB should match for $color")

            // Test binary roundtrip
            val bytes = colorSerializer.toBytes(color)
            val restoredFromBinary = colorSerializer.fromBytes(bytes.toByteArray())

            assertTrue(restoredFromBinary is Color.Custom, "Binary should deserialize to Custom for $color")
            assertEquals(color.rgb, (restoredFromBinary as Color.Custom).rgb, "Binary RGB should match for $color")
        }
    }

    @Test
    fun `test enum serializer - json format consistency`() {
        // Test that dense and readable formats are different for value fields but same for constants
        val redConstant = Color.Red
        val customValue = Color.Custom(0xABCDEF)

        // Constants should be different between dense/readable
        val redDenseJson = colorSerializer.toJsonCode(redConstant, readableFlavor = false)
        val redReadableJson = colorSerializer.toJsonCode(redConstant, readableFlavor = true)
        assertTrue(redDenseJson != redReadableJson, "Constant dense and readable JSON should be different")

        // Value fields should be different between dense/readable
        val customDenseJson = colorSerializer.toJsonCode(customValue, readableFlavor = false)
        val customReadableJson = colorSerializer.toJsonCode(customValue, readableFlavor = true)
        assertTrue(customDenseJson != customReadableJson, "Value field dense and readable JSON should be different")

        // Dense should be array/number, readable should be string/object
        assertTrue(redDenseJson.toLongOrNull() != null, "Dense constant should be a number")
        assertTrue(redReadableJson.startsWith("\"") && redReadableJson.endsWith("\""), "Readable constant should be a string")

        assertTrue(customDenseJson.startsWith("[") && customDenseJson.endsWith("]"), "Dense value should be an array")
        assertTrue(customReadableJson.startsWith("{") && customReadableJson.endsWith("}"), "Readable value should be an object")
    }

    @Test
    fun `test enum serializer - field number ranges`() {
        // Test that field numbers work correctly for different ranges
        // Using the existing colorEnumSerializer which has field numbers 1, 2, 3, 4

        val constantValues = listOf(Color.Red, Color.Green, Color.Blue)
        constantValues.forEach { constant ->
            val bytes = colorSerializer.toBytes(constant)
            val restored = colorSerializer.fromBytes(bytes.toByteArray())
            assertEquals(constant, restored, "Constant field roundtrip should work for $constant")
        }

        // Test value field
        val customColor = Color.Custom(42)
        val bytes = colorSerializer.toBytes(customColor)
        val restored = colorSerializer.fromBytes(bytes.toByteArray())
        assertTrue(restored is Color.Custom, "Should deserialize to Custom color")
        assertEquals(42, (restored as Color.Custom).rgb, "RGB value should match")
    }

    @Test
    fun `test enum serializer - multiple value serializers`() {
        // Test with the existing Status enum that already has multiple serializer types
        val testCases =
            listOf(
                Status.Active,
                Status.Inactive,
                Status.Pending("waiting for approval"),
            )

        testCases.forEach { testCase ->
            // Test JSON roundtrips
            val denseJson = statusSerializer.toJsonCode(testCase, readableFlavor = false)
            val readableJson = statusSerializer.toJsonCode(testCase, readableFlavor = true)

            val restoredFromDense = statusSerializer.fromJsonCode(denseJson)
            val restoredFromReadable = statusSerializer.fromJsonCode(readableJson)

            assertEquals(testCase, restoredFromDense, "Dense JSON roundtrip should match for $testCase")
            assertEquals(testCase, restoredFromReadable, "Readable JSON roundtrip should match for $testCase")

            // Test binary roundtrip
            val bytes = statusSerializer.toBytes(testCase)
            val restoredFromBinary = statusSerializer.fromBytes(bytes.toByteArray())

            assertEquals(testCase, restoredFromBinary, "Binary roundtrip should match for $testCase")
        }
    }

    @Test
    fun `test enum serializer - toString()`() {
        assertEquals(
            "EnumSerializerTest.Status.Active",
            toStringImpl(Status.Active, statusSerializer.impl),
        )
        assertEquals(
            "EnumSerializerTest.Status.Pending(\n" +
                "  \"foo\\n\" +\n    \"bar\"\n" +
                ")",
            toStringImpl(Status.Pending("foo\nbar"), statusSerializer.impl),
        )
    }
}
