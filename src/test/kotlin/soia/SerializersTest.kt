package soia

import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.time.Instant

class SerializersTest {
    @Test
    fun `test bool serializer - basic functionality`() {
        // Test true value - should be 1 in dense, true in readable
        val trueJson = Serializers.bool.toJson(true, JsonFlavor.DENSE)
        assertTrue(trueJson is JsonPrimitive)
        assertEquals("1", (trueJson as JsonPrimitive).content)

        val trueReadableJson = Serializers.bool.toJson(true, JsonFlavor.READABLE)
        assertEquals("true", (trueReadableJson as JsonPrimitive).content)

        // Test false value - should be 0 in dense, false in readable
        val falseJson = Serializers.bool.toJson(false, JsonFlavor.DENSE)
        assertEquals("0", (falseJson as JsonPrimitive).content)

        val falseReadableJson = Serializers.bool.toJson(false, JsonFlavor.READABLE)
        assertEquals("false", (falseReadableJson as JsonPrimitive).content)

        // Test round-trip
        assertEquals(true, Serializers.bool.fromJson(trueJson))
        assertEquals(true, Serializers.bool.fromJsonCode("true"))
        assertEquals(true, Serializers.bool.fromJson(trueReadableJson))
        assertEquals(false, Serializers.bool.fromJson(falseJson))
        assertEquals(false, Serializers.bool.fromJsonCode("false"))
        assertEquals(false, Serializers.bool.fromJson(falseReadableJson))

        assertEquals(true, Serializers.bool.fromJson(JsonPrimitive(100)))
        assertEquals(true, Serializers.bool.fromJson(JsonPrimitive(3.14)))
        assertEquals(false, Serializers.bool.fromJson(JsonPrimitive("0")))
        assertEquals(true, Serializers.bool.fromJson(JsonPrimitive("-1")))
    }

    @Test
    fun `test bool serializer - binary serialization`() {
        // From TypeScript tests: true -> "01", false -> "00"
        val trueBytes = Serializers.bool.toBytes(true)
        assertEquals("736f696101", trueBytes.hex())

        val falseBytes = Serializers.bool.toBytes(false)
        assertEquals("736f696100", falseBytes.hex())

        // Test round trip
        val restoredTrue = Serializers.bool.fromBytes(trueBytes.toByteArray())
        assertEquals(true, restoredTrue)

        val restoredFalse = Serializers.bool.fromBytes(falseBytes)
        assertEquals(false, restoredFalse)

        assertEquals(true, Serializers.bool.fromBytes(Serializers.int32.toBytes(100)))
        assertEquals(true, Serializers.bool.fromBytes(Serializers.float32.toBytes(3.14F)))
        assertEquals(true, Serializers.bool.fromBytes(Serializers.uint64.toBytes(10000000000000000UL)))
        assertEquals(true, Serializers.bool.fromBytes(Serializers.int64.toBytes(-1)))
    }

    @Test
    fun `test int32 serializer`() {
        val values = listOf(0, 1, -1, 42, -42, Int.MAX_VALUE, Int.MIN_VALUE)

        for (value in values) {
            // JSON serialization
            val jsonCode = Serializers.int32.toJsonCode(value)
            val restored = Serializers.int32.fromJsonCode(jsonCode)
            assertEquals(value, restored, "Failed for value: $value")

            // Binary serialization
            val bytes = Serializers.int32.toBytes(value)
            val restoredFromBytes = Serializers.int32.fromBytes(bytes.toByteArray())
            assertEquals(value, restoredFromBytes, "Binary failed for value: $value")

            // JsonFlavor shouldn't affect primitive numbers
            assertEquals(
                Serializers.int32.toJsonCode(value, JsonFlavor.DENSE),
                Serializers.int32.toJsonCode(value, JsonFlavor.READABLE),
            )
        }
    }

    @Test
    fun `test int64 serializer`() {
        val values = listOf(0L, 1L, -1L, 42L, -42L, Long.MAX_VALUE, Long.MIN_VALUE)

        for (value in values) {
            val jsonCode = Serializers.int64.toJsonCode(value)
            val restored = Serializers.int64.fromJsonCode(jsonCode)
            assertEquals(value, restored, "Failed for value: $value")

            val bytes = Serializers.int64.toBytes(value)
            val restoredFromBytes = Serializers.int64.fromBytes(bytes.toByteArray())
            assertEquals(value, restoredFromBytes, "Binary failed for value: $value")
        }
    }

    @Test
    fun `test uint64 serializer`() {
        val values = listOf(0UL, 1UL, 42UL, ULong.MAX_VALUE)

        for (value in values) {
            val jsonCode = Serializers.uint64.toJsonCode(value)
            val restored = Serializers.uint64.fromJsonCode(jsonCode)
            assertEquals(value, restored, "Failed for value: $value")

            val bytes = Serializers.uint64.toBytes(value)
            val restoredFromBytes = Serializers.uint64.fromBytes(bytes.toByteArray())
            assertEquals(value, restoredFromBytes, "Binary failed for value: $value")
        }
    }

    @Test
    fun `test float32 serializer`() {
        val values =
            listOf(
                0.0f,
                1.0f,
                -1.0f,
                3.14f,
                Float.MAX_VALUE,
                Float.MIN_VALUE,
                Float.NaN,
                Float.POSITIVE_INFINITY,
                Float.NEGATIVE_INFINITY,
            )

        for (value in values) {
            val jsonCode = Serializers.float32.toJsonCode(value)
            val restored = Serializers.float32.fromJsonCode(jsonCode)

            if (value.isNaN()) {
                assertTrue(restored.isNaN(), "NaN not preserved for value: $value")
            } else {
                assertEquals(value, restored, "Failed for value: $value")
            }

            val bytes = Serializers.float32.toBytes(value)
            val restoredFromBytes = Serializers.float32.fromBytes(bytes.toByteArray())

            if (value.isNaN()) {
                assertTrue(restoredFromBytes.isNaN(), "Binary NaN not preserved for value: $value")
            } else {
                assertEquals(value, restoredFromBytes, "Binary failed for value: $value")
            }
        }
    }

    @Test
    fun `test float64 serializer`() {
        val values =
            listOf(
                0.0,
                1.0,
                -1.0,
                3.14159265359,
                Double.MAX_VALUE,
                Double.MIN_VALUE,
                Double.NaN,
                Double.POSITIVE_INFINITY,
                Double.NEGATIVE_INFINITY,
            )

        for (value in values) {
            val jsonCode = Serializers.float64.toJsonCode(value)
            val restored = Serializers.float64.fromJsonCode(jsonCode)

            if (value.isNaN()) {
                assertTrue(restored.isNaN(), "NaN not preserved for value: $value")
            } else {
                assertEquals(value, restored, "Failed for value: $value")
            }

            val bytes = Serializers.float64.toBytes(value)
            val restoredFromBytes = Serializers.float64.fromBytes(bytes.toByteArray())

            if (value.isNaN()) {
                assertTrue(restoredFromBytes.isNaN(), "Binary NaN not preserved for value: $value")
            } else {
                assertEquals(value, restoredFromBytes, "Binary failed for value: $value")
            }
        }
    }

    @Test
    fun `test string serializer`() {
        val values =
            listOf(
                "",
                "hello",
                "world",
                "Hello, 世界!",
                "🚀",
                "\n\t\r",
                "A very long string that exceeds normal buffer sizes and should test the streaming capabilities of the serializer properly",
            )

        for (value in values) {
            val jsonCode = Serializers.string.toJsonCode(value)
            val restored = Serializers.string.fromJsonCode(jsonCode)
            assertEquals(value, restored, "Failed for value: '$value'")

            val bytes = Serializers.string.toBytes(value)
            val restoredFromBytes = Serializers.string.fromBytes(bytes.toByteArray())
            assertEquals(value, restoredFromBytes, "Binary failed for value: '$value'")

            // JsonFlavor shouldn't affect plain strings
            assertEquals(
                Serializers.string.toJsonCode(value, JsonFlavor.DENSE),
                Serializers.string.toJsonCode(value, JsonFlavor.READABLE),
            )
        }

        assertEquals("", Serializers.string.fromJsonCode("0"))
    }

    @Test
    fun `test bytes serializer - dense flavor`() {
        val values =
            listOf(
                ByteString.EMPTY,
                "hello".encodeUtf8(),
                "world".encodeUtf8(),
                "Hello, 世界!".encodeUtf8(),
            )

        for (value in values) {
            // Test DENSE flavor
            val denseJson = Serializers.bytes.toJsonCode(value, JsonFlavor.DENSE)
            val restoredFromDense = Serializers.bytes.fromJsonCode(denseJson)
            assertEquals(value, restoredFromDense, "Dense failed for value: $value")

            // Dense should produce a simple string
            assertTrue(
                denseJson.startsWith("\"") && denseJson.endsWith("\""),
                "Dense format should be a simple JSON string, got: $denseJson",
            )

            // Binary serialization
            val bytes = Serializers.bytes.toBytes(value)
            val restoredFromBytes = Serializers.bytes.fromBytes(bytes.toByteArray())
            assertEquals(value, restoredFromBytes, "Binary failed for value: $value")
        }

        assertEquals(ByteString.EMPTY, Serializers.bytes.fromJsonCode("0"))
    }

    @Test
    fun `test timestamp serializer - dense flavor`() {
        val timestamps =
            listOf(
                Instant.EPOCH,
                Instant.parse("2025-08-25T10:30:45Z"),
                Instant.parse("2000-01-01T00:00:00Z"),
                Instant.ofEpochMilli(System.currentTimeMillis()),
            )

        for (timestamp in timestamps) {
            val denseJson = Serializers.timestamp.toJsonCode(timestamp, JsonFlavor.DENSE)
            val restored = Serializers.timestamp.fromJsonCode(denseJson)
            assertEquals(timestamp, restored, "Dense failed for timestamp: $timestamp")

            // Dense should produce a unix milliseconds number
            val denseJsonValue = denseJson.toLongOrNull()
            assertNotNull(denseJsonValue, "Dense format should be a number, got: $denseJson")

            // Binary serialization
            val bytes = Serializers.timestamp.toBytes(timestamp)
            val restoredFromBytes = Serializers.timestamp.fromBytes(bytes.toByteArray())
            assertEquals(timestamp, restoredFromBytes, "Binary failed for timestamp: $timestamp")
        }
    }

    @Test
    fun `test timestamp serializer - readable flavor`() {
        val timestamp = Instant.parse("2025-08-25T10:30:45Z")
        val readableJson = Serializers.timestamp.toJsonCode(timestamp, JsonFlavor.READABLE)

        // Readable should produce an object with unix_millis and formatted
        assertEquals(
            listOf(
                "{",
                "  \"unix_millis\": 1756117845000,",
                "  \"formatted\": \"2025-08-25T10:30:45Z\"",
                "}",
            ).joinToString(separator = "\n") { it },
            readableJson,
        )

        val restoredFromReadable = Serializers.timestamp.fromJsonCode(readableJson)
        assertEquals(timestamp, restoredFromReadable, "Readable failed for timestamp: $timestamp")
    }

    @Test
    fun `test edge cases and error handling`() {
        // Test empty string
        assertEquals("", Serializers.string.fromJsonCode("\"\""))

        // Test zero values
        assertEquals(0, Serializers.int32.fromJsonCode("0"))
        assertEquals(0L, Serializers.int64.fromJsonCode("0"))
        assertEquals(0.0f, Serializers.float32.fromJsonCode("0.0"))
        assertEquals(0.0, Serializers.float64.fromJsonCode("0.0"))

        // Test empty bytes
        assertEquals(ByteString.EMPTY, Serializers.bytes.fromJsonCode("\"\""))
    }

    @Test
    fun `test serialization roundtrip for all types`() {
        // Test that all serializers can handle roundtrip JSON serialization
        val testData =
            mapOf(
                Serializers.bool to true,
                Serializers.int32 to -42,
                Serializers.int64 to 123456789L,
                Serializers.uint64 to 987654321UL,
                Serializers.float32 to 3.14f,
                Serializers.float64 to 2.71828,
                Serializers.string to "test string",
                Serializers.bytes to "test bytes".encodeUtf8(),
                Serializers.timestamp to Instant.parse("2025-08-25T12:00:00Z"),
            )

        testData.forEach { (serializer, value) ->
            @Suppress("UNCHECKED_CAST")
            val typedSerializer = serializer as Serializer<Any>

            // Test JSON roundtrip
            val json = typedSerializer.toJsonCode(value)
            val restored = typedSerializer.fromJsonCode(json)
            assertEquals(value, restored, "JSON roundtrip failed for $value")

            // Test binary roundtrip
            val bytes = typedSerializer.toBytes(value)
            val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())
            assertEquals(value, restoredFromBytes, "Binary roundtrip failed for $value")
        }
    }

    // Additional comprehensive tests for specific edge cases and binary format validation

    @Test
    fun `test int32 binary encoding specifics`() {
        // Test specific wire format encoding (includes "soia" prefix)
        val testCases =
            mapOf(
                0 to "736f696100",
                1 to "736f696101",
                231 to "736f6961e7",
                232 to "736f6961e8e800",
                257 to "736f6961e80101",
                65535 to "736f6961e8ffff",
                65536 to "736f6961e900000100",
                -1 to "736f6961ebff",
                -256 to "736f6961eb00",
                -257 to "736f6961ecfffe",
            )

        testCases.forEach { (value, expectedHex) ->
            val bytes = Serializers.int32.toBytes(value)
            assertEquals(expectedHex, bytes.hex(), "Binary encoding failed for value: $value")

            val restored = Serializers.int32.fromBytes(bytes.toByteArray())
            assertEquals(value, restored, "Binary decoding failed for value: $value")
        }
    }

    @Test
    fun `test float32 special values json serialization`() {
        val testCases =
            mapOf(
                Float.NaN to "\"NaN\"",
                Float.POSITIVE_INFINITY to "\"Infinity\"",
                Float.NEGATIVE_INFINITY to "\"-Infinity\"",
                0.0f to "0.0",
                -0.0f to "-0.0",
            )

        testCases.forEach { (value, expectedJson) ->
            val json = Serializers.float32.toJsonCode(value)
            if (value.isNaN()) {
                // For NaN, just check that it deserializes correctly
                val restored = Serializers.float32.fromJsonCode(json)
                assertTrue(restored.isNaN(), "NaN should be preserved")
            } else {
                val restored = Serializers.float32.fromJsonCode(json)
                assertEquals(value, restored, "Failed for value: $value")
            }
        }
    }

    @Test
    fun `test float64 special values json serialization`() {
        val testCases =
            mapOf(
                Double.NaN to "\"NaN\"",
                Double.POSITIVE_INFINITY to "\"Infinity\"",
                Double.NEGATIVE_INFINITY to "\"-Infinity\"",
                0.0 to "0.0",
                -0.0 to "-0.0",
            )

        testCases.forEach { (value, expectedJson) ->
            val json = Serializers.float64.toJsonCode(value)
            if (value.isNaN()) {
                // For NaN, just check that it deserializes correctly
                val restored = Serializers.float64.fromJsonCode(json)
                assertTrue(restored.isNaN(), "NaN should be preserved")
            } else {
                val restored = Serializers.float64.fromJsonCode(json)
                assertEquals(value, restored, "Failed for value: $value")
            }
        }
    }

    @Test
    fun `test int64 large number json serialization`() {
        // Test JavaScript safe integer boundaries
        val safeMax = 9007199254740992L // 2^53
        val safeMin = -9007199254740992L // -(2^53)

        // Values within safe range should be numbers
        val safeValue = 123456789L
        val safeJson = Serializers.int64.toJsonCode(safeValue)
        assertEquals("123456789", safeJson)

        // Values outside safe range should be strings
        val unsafeValue = Long.MAX_VALUE
        val unsafeJson = Serializers.int64.toJsonCode(unsafeValue)
        assertTrue(unsafeJson.startsWith("\"") && unsafeJson.endsWith("\""))

        // Both should roundtrip correctly
        assertEquals(safeValue, Serializers.int64.fromJsonCode(safeJson))
        assertEquals(unsafeValue, Serializers.int64.fromJsonCode(unsafeJson))
    }

    @Test
    fun `test uint64 large number json serialization`() {
        // Test JavaScript safe integer boundaries for unsigned values
        val safeValue = 123456789UL
        val safeJson = Serializers.uint64.toJsonCode(safeValue)
        assertEquals("123456789", safeJson)

        // Values outside safe range should be strings
        val unsafeValue = ULong.MAX_VALUE
        val unsafeJson = Serializers.uint64.toJsonCode(unsafeValue)
        assertTrue(unsafeJson.startsWith("\"") && unsafeJson.endsWith("\""))

        // Both should roundtrip correctly
        assertEquals(safeValue, Serializers.uint64.fromJsonCode(safeJson))
        assertEquals(unsafeValue, Serializers.uint64.fromJsonCode(unsafeJson))
    }

    @Test
    fun `test string encoding edge cases`() {
        val testCases =
            listOf(
                "" to "736f6961f2",
                "0" to "736f6961f30130",
                "A" to "736f6961f30141",
                "🚀" to "736f6961f304f09f9a80",
                "\u0000" to "736f6961f30100",
                "Hello\nWorld" to "736f6961f30b48656c6c6f0a576f726c64",
            )

        testCases.forEach { (value, expectedHex) ->
            val bytes = Serializers.string.toBytes(value)
            assertEquals(expectedHex, bytes.hex(), "Binary encoding failed for string: '$value'")

            val restored = Serializers.string.fromBytes(bytes.toByteArray())
            assertEquals(value, restored, "Binary decoding failed for string: '$value'")
        }
    }

    @Test
    fun `test bytes encoding edge cases`() {
        val testCases =
            listOf(
                ByteString.EMPTY to "736f6961f4",
                "A".encodeUtf8() to "736f6961f50141",
                ByteString.of(0, 255.toByte()) to "736f6961f50200ff",
            )

        testCases.forEach { (value, expectedHex) ->
            val bytes = Serializers.bytes.toBytes(value)
            assertEquals(expectedHex, bytes.hex(), "Binary encoding failed for bytes: $value")

            val restored = Serializers.bytes.fromBytes(bytes.toByteArray())
            assertEquals(value, restored, "Binary decoding failed for bytes: $value")
        }
    }

    @Test
    fun `test timestamp clamping behavior`() {
        // Test that timestamps are clamped to valid JavaScript Date range
        val minValid = Instant.ofEpochMilli(-8640000000000000L)
        val maxValid = Instant.ofEpochMilli(8640000000000000L)

        // Test that these values roundtrip correctly
        val minBytes = Serializers.timestamp.toBytes(minValid)
        val restoredMin = Serializers.timestamp.fromBytes(minBytes.toByteArray())
        assertEquals(minValid, restoredMin)

        val maxBytes = Serializers.timestamp.toBytes(maxValid)
        val restoredMax = Serializers.timestamp.fromBytes(maxBytes.toByteArray())
        assertEquals(maxValid, restoredMax)
    }

    @Test
    fun `test timestamp binary encoding specifics`() {
        val testCases =
            mapOf(
                Instant.EPOCH to "736f696100",
                Instant.ofEpochMilli(1000) to "736f6961efe803000000000000",
                Instant.ofEpochMilli(-1000) to "736f6961ef18fcffffffffffff",
            )

        testCases.forEach { (timestamp, expectedHex) ->
            val bytes = Serializers.timestamp.toBytes(timestamp)
            assertEquals(expectedHex, bytes.hex(), "Binary encoding failed for timestamp: $timestamp")

            val restored = Serializers.timestamp.fromBytes(bytes.toByteArray())
            assertEquals(timestamp, restored, "Binary decoding failed for timestamp: $timestamp")
        }
    }

    @Test
    fun `test all serializers handle defaults correctly`() {
        // Test that default values roundtrip correctly through serialization
        val defaultTests =
            mapOf(
                Serializers.bool to false,
                Serializers.int32 to 0,
                Serializers.int64 to 0L,
                Serializers.uint64 to 0UL,
                Serializers.float32 to 0.0f,
                Serializers.float64 to 0.0,
                Serializers.string to "",
                Serializers.bytes to ByteString.EMPTY,
                Serializers.timestamp to Instant.EPOCH,
            )

        defaultTests.forEach { (serializer, defaultValue) ->
            @Suppress("UNCHECKED_CAST")
            val typedSerializer = serializer as Serializer<Any>

            // Test JSON roundtrip for default values
            val json = typedSerializer.toJsonCode(defaultValue)
            val restoredFromJson = typedSerializer.fromJsonCode(json)
            assertEquals(defaultValue, restoredFromJson, "JSON default roundtrip failed for ${serializer::class.simpleName}")

            // Test binary roundtrip for default values
            val bytes = typedSerializer.toBytes(defaultValue)
            val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())
            assertEquals(defaultValue, restoredFromBytes, "Binary default roundtrip failed for ${serializer::class.simpleName}")
        }
    }
}
