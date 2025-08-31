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
        val trueJson = Serializers.bool.toJson(true, readableFlavor = false)
        assertTrue(trueJson is JsonPrimitive)
        assertEquals("1", (trueJson as JsonPrimitive).content)

        val trueReadableJson = Serializers.bool.toJson(true, readableFlavor = true)
        assertEquals("true", (trueReadableJson as JsonPrimitive).content)

        // Test false value - should be 0 in dense, false in readable
        val falseJson = Serializers.bool.toJson(false, readableFlavor = false)
        assertEquals("0", (falseJson as JsonPrimitive).content)

        val falseReadableJson = Serializers.bool.toJson(false, readableFlavor = true)
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
                Serializers.int32.toJsonCode(value, readableFlavor = false),
                Serializers.int32.toJsonCode(value, readableFlavor = true),
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
                Serializers.string.toJsonCode(value, readableFlavor = false),
                Serializers.string.toJsonCode(value, readableFlavor = true),
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
            val denseJson = Serializers.bytes.toJsonCode(value, readableFlavor = false)
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
    fun `test instant serializer - dense flavor`() {
        val instants =
            listOf(
                Instant.EPOCH,
                Instant.parse("2025-08-25T10:30:45Z"),
                Instant.parse("2000-01-01T00:00:00Z"),
                Instant.ofEpochMilli(System.currentTimeMillis()),
            )

        for (instant in instants) {
            val denseJson = Serializers.instant.toJsonCode(instant, readableFlavor = false)
            val restored = Serializers.instant.fromJsonCode(denseJson)
            assertEquals(instant, restored, "Dense failed for instant: $instant")

            // Dense should produce a unix milliseconds number
            val denseJsonValue = denseJson.toLongOrNull()
            assertNotNull(denseJsonValue, "Dense format should be a number, got: $denseJson")

            // Binary serialization
            val bytes = Serializers.instant.toBytes(instant)
            val restoredFromBytes = Serializers.instant.fromBytes(bytes.toByteArray())
            assertEquals(instant, restoredFromBytes, "Binary failed for instant: $instant")
        }
    }

    @Test
    fun `test instant serializer - readable flavor`() {
        val instant = Instant.parse("2025-08-25T10:30:45Z")
        val readableJson = Serializers.instant.toJsonCode(instant, readableFlavor = true)

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

        val restoredFromReadable = Serializers.instant.fromJsonCode(readableJson)
        assertEquals(instant, restoredFromReadable, "Readable failed for instant: $instant")
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
                Serializers.instant to Instant.parse("2025-08-25T12:00:00Z"),
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
    fun `test instant clamping behavior`() {
        // Test that instants are clamped to valid JavaScript Date range
        val minValid = Instant.ofEpochMilli(-8640000000000000L)
        val maxValid = Instant.ofEpochMilli(8640000000000000L)

        // Test that these values roundtrip correctly
        val minBytes = Serializers.instant.toBytes(minValid)
        val restoredMin = Serializers.instant.fromBytes(minBytes.toByteArray())
        assertEquals(minValid, restoredMin)

        val maxBytes = Serializers.instant.toBytes(maxValid)
        val restoredMax = Serializers.instant.fromBytes(maxBytes.toByteArray())
        assertEquals(maxValid, restoredMax)
    }

    @Test
    fun `test instant binary encoding specifics`() {
        val testCases =
            mapOf(
                Instant.EPOCH to "736f696100",
                Instant.ofEpochMilli(1000) to "736f6961efe803000000000000",
                Instant.ofEpochMilli(-1000) to "736f6961ef18fcffffffffffff",
            )

        testCases.forEach { (instant, expectedHex) ->
            val bytes = Serializers.instant.toBytes(instant)
            assertEquals(expectedHex, bytes.hex(), "Binary encoding failed for instant: $instant")

            val restored = Serializers.instant.fromBytes(bytes.toByteArray())
            assertEquals(instant, restored, "Binary decoding failed for instant: $instant")
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
                Serializers.instant to Instant.EPOCH,
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

    @Test
    fun `test optional serializer with non-null values`() {
        // Test optional serializer with various non-null values
        val intOptional = Serializers.optional(Serializers.int32)
        val stringOptional = Serializers.optional(Serializers.string)
        val boolOptional = Serializers.optional(Serializers.bool)
        val bytesOptional = Serializers.optional(Serializers.bytes)
        val instantOptional = Serializers.optional(Serializers.instant)

        // Test non-null values
        val testValue = 42
        val jsonCode = intOptional.toJsonCode(testValue)
        val restored = intOptional.fromJsonCode(jsonCode)
        assertEquals(testValue, restored)

        val bytes = intOptional.toBytes(testValue)
        val restoredFromBytes = intOptional.fromBytes(bytes.toByteArray())
        assertEquals(testValue, restoredFromBytes)

        // Test string
        val testString = "hello world"
        val stringJson = stringOptional.toJsonCode(testString)
        val restoredString = stringOptional.fromJsonCode(stringJson)
        assertEquals(testString, restoredString)

        val stringBytes = stringOptional.toBytes(testString)
        val restoredStringFromBytes = stringOptional.fromBytes(stringBytes.toByteArray())
        assertEquals(testString, restoredStringFromBytes)

        // Test bool
        val testBool = true
        val boolJson = boolOptional.toJsonCode(testBool)
        val restoredBool = boolOptional.fromJsonCode(boolJson)
        assertEquals(testBool, restoredBool)

        val boolBytes = boolOptional.toBytes(testBool)
        val restoredBoolFromBytes = boolOptional.fromBytes(boolBytes.toByteArray())
        assertEquals(testBool, restoredBoolFromBytes)

        // Test bytes
        val testBytes = "test data".encodeUtf8()
        val bytesJson = bytesOptional.toJsonCode(testBytes)
        val restoredBytes = bytesOptional.fromJsonCode(bytesJson)
        assertEquals(testBytes, restoredBytes)

        val bytesBinary = bytesOptional.toBytes(testBytes)
        val restoredBytesFromBinary = bytesOptional.fromBytes(bytesBinary.toByteArray())
        assertEquals(testBytes, restoredBytesFromBinary)

        // Test instant
        val testTimestamp = Instant.parse("2025-08-25T12:00:00Z")
        val instantJson = instantOptional.toJsonCode(testTimestamp)
        val restoredTimestamp = instantOptional.fromJsonCode(instantJson)
        assertEquals(testTimestamp, restoredTimestamp)

        val instantBytes = instantOptional.toBytes(testTimestamp)
        val restoredTimestampFromBytes = instantOptional.fromBytes(instantBytes.toByteArray())
        assertEquals(testTimestamp, restoredTimestampFromBytes)
    }

    @Test
    fun `test optional serializer with null values`() {
        // Test optional serializer with null values
        val intOptional = Serializers.optional(Serializers.int32)
        val stringOptional = Serializers.optional(Serializers.string)
        val boolOptional = Serializers.optional(Serializers.bool)
        val bytesOptional = Serializers.optional(Serializers.bytes)
        val instantOptional = Serializers.optional(Serializers.instant)

        // Test null values in JSON
        val nullJson = intOptional.toJsonCode(null)
        assertEquals("null", nullJson)
        val restoredNull = intOptional.fromJsonCode(nullJson)
        assertEquals(null, restoredNull)

        // Test null values in binary format
        val nullBytes = intOptional.toBytes(null)
        assertEquals("736f6961ff", nullBytes.hex()) // Should end with 0xFF for null
        val restoredNullFromBytes = intOptional.fromBytes(nullBytes.toByteArray())
        assertEquals(null, restoredNullFromBytes)

        // Test all types with null
        listOf(intOptional, stringOptional, boolOptional, bytesOptional, instantOptional).forEach { serializer ->
            @Suppress("UNCHECKED_CAST")
            val typedSerializer = serializer as Serializer<Any?>

            val json = typedSerializer.toJsonCode(null)
            assertEquals("null", json)
            assertEquals(null, typedSerializer.fromJsonCode(json))

            val bytes = typedSerializer.toBytes(null)
            assertTrue(bytes.hex().endsWith("ff"), "Null binary encoding should end with 0xFF")
            assertEquals(null, typedSerializer.fromBytes(bytes.toByteArray()))
        }
    }

    @Test
    fun `test optional serializer json flavors`() {
        val intOptional = Serializers.optional(Serializers.int32)
        val instantOptional = Serializers.optional(Serializers.instant)
        val boolOptional = Serializers.optional(Serializers.bool)

        // Test non-null values with different flavors
        val testInt = 42
        val denseIntJson = intOptional.toJsonCode(testInt, readableFlavor = false)
        val readableIntJson = intOptional.toJsonCode(testInt, readableFlavor = true)
        assertEquals(denseIntJson, readableIntJson) // Should be the same for int32

        // Test instant with different flavors
        val testTimestamp = Instant.parse("2025-08-25T12:00:00Z")
        val denseTimestampJson = instantOptional.toJsonCode(testTimestamp, readableFlavor = false)
        val readableTimestampJson = instantOptional.toJsonCode(testTimestamp, readableFlavor = true)
        // Dense should be a number, readable should be an object
        assertTrue(denseTimestampJson.toLongOrNull() != null, "Dense instant should be a number")
        assertTrue(readableTimestampJson.contains("unix_millis"), "Readable instant should contain unix_millis")

        // Test bool with different flavors
        val testBool = true
        val denseBoolJson = boolOptional.toJsonCode(testBool, readableFlavor = false)
        val readableBoolJson = boolOptional.toJsonCode(testBool, readableFlavor = true)
        assertEquals("1", denseBoolJson) // Dense should be "1"
        assertEquals("true", readableBoolJson) // Readable should be "true"

        // Test null with different flavors (should always be "null")
        val nullDense = intOptional.toJsonCode(null, readableFlavor = false)
        val nullReadable = intOptional.toJsonCode(null, readableFlavor = true)
        assertEquals("null", nullDense)
        assertEquals("null", nullReadable)
    }

    @Test
    fun `test optional serializer idempotency`() {
        // Test that calling optional on an already optional serializer returns the same instance
        val intOptional = Serializers.optional(Serializers.int32)
        val doubleOptional = Serializers.optional(intOptional)

        // They should be the same instance (idempotent)
        assertTrue(intOptional === doubleOptional, "Calling optional on an optional should return the same instance")

        // Test functionality is preserved
        val testValue = 123
        assertEquals(testValue, intOptional.fromJsonCode(intOptional.toJsonCode(testValue)))
        assertEquals(testValue, doubleOptional.fromJsonCode(doubleOptional.toJsonCode(testValue)))
        assertEquals(null, intOptional.fromJsonCode(intOptional.toJsonCode(null)))
        assertEquals(null, doubleOptional.fromJsonCode(doubleOptional.toJsonCode(null)))

        // Binary serialization should also work the same
        val testBytes = intOptional.toBytes(testValue)
        val doubleBytes = doubleOptional.toBytes(testValue)
        assertEquals(testBytes, doubleBytes)

        assertEquals(testValue, intOptional.fromBytes(testBytes.toByteArray()))
        assertEquals(testValue, doubleOptional.fromBytes(doubleBytes.toByteArray()))
    }

    @Test
    fun `test optional serializer edge cases`() {
        val intOptional = Serializers.optional(Serializers.int32)
        val stringOptional = Serializers.optional(Serializers.string)
        val bytesOptional = Serializers.optional(Serializers.bytes)

        // Test edge case values
        val testCases =
            listOf(
                intOptional to listOf(0, Int.MAX_VALUE, Int.MIN_VALUE, null),
                stringOptional to listOf("", "null", "undefined", "false", "0", null),
                bytesOptional to listOf(ByteString.EMPTY, "null".encodeUtf8(), null),
            )

        testCases.forEach { (serializer, values) ->
            values.forEach { value ->
                @Suppress("UNCHECKED_CAST")
                val typedSerializer = serializer as Serializer<Any?>

                // Test JSON roundtrip
                val json = typedSerializer.toJsonCode(value)
                val restoredFromJson = typedSerializer.fromJsonCode(json)
                assertEquals(value, restoredFromJson, "JSON roundtrip failed for value: $value")

                // Test binary roundtrip
                val bytes = typedSerializer.toBytes(value)
                val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())
                assertEquals(value, restoredFromBytes, "Binary roundtrip failed for value: $value")
            }
        }
    }

    @Test
    fun `test optional serializer binary format specifics`() {
        val intOptional = Serializers.optional(Serializers.int32)

        // Test specific binary encodings
        val testCases =
            mapOf(
                null to "736f6961ff",
                0 to "736f696100",
                42 to "736f69612a",
                -1 to "736f6961ebff",
            )

        testCases.forEach { (value, expectedHex) ->
            val bytes = intOptional.toBytes(value)
            assertEquals(expectedHex, bytes.hex(), "Binary encoding failed for optional value: $value")

            val restored = intOptional.fromBytes(bytes.toByteArray())
            assertEquals(value, restored, "Binary decoding failed for optional value: $value")
        }
    }

    @Test
    fun `test nested optional serializers`() {
        // While the API prevents double-optional, we can test complex scenarios
        val intOptional = Serializers.optional(Serializers.int32)
        val stringOptional = Serializers.optional(Serializers.string)
        val instantOptional = Serializers.optional(Serializers.instant)

        // Test combinations of optional serializers with different types
        val testScenarios =
            listOf(
                Triple(intOptional, 42, null),
                Triple(intOptional, null, 42),
                Triple(stringOptional, "test", null),
                Triple(stringOptional, null, "test"),
                Triple(instantOptional, Instant.parse("2025-08-25T12:00:00Z"), null),
                Triple(instantOptional, null, Instant.parse("2025-08-25T12:00:00Z")),
            )

        testScenarios.forEach { (serializer, value1, value2) ->
            @Suppress("UNCHECKED_CAST")
            val typedSerializer = serializer as Serializer<Any?>

            // Test that different values produce different results
            val json1 = typedSerializer.toJsonCode(value1)
            val json2 = typedSerializer.toJsonCode(value2)

            if (value1 != value2) {
                assertTrue(json1 != json2, "Different values should produce different JSON: $value1 vs $value2")
            }

            // Test roundtrip for both values
            assertEquals(value1, typedSerializer.fromJsonCode(json1))
            assertEquals(value2, typedSerializer.fromJsonCode(json2))

            // Test binary serialization
            val bytes1 = typedSerializer.toBytes(value1)
            val bytes2 = typedSerializer.toBytes(value2)

            if (value1 != value2) {
                assertTrue(bytes1 != bytes2, "Different values should produce different binary: $value1 vs $value2")
            }

            assertEquals(value1, typedSerializer.fromBytes(bytes1.toByteArray()))
            assertEquals(value2, typedSerializer.fromBytes(bytes2.toByteArray()))
        }
    }

    @Test
    fun `test optional serializer with all primitive types`() {
        // Test that optional works correctly with all primitive serializers
        val optionalSerializers =
            mapOf(
                "bool" to Serializers.optional(Serializers.bool),
                "int32" to Serializers.optional(Serializers.int32),
                "int64" to Serializers.optional(Serializers.int64),
                "uint64" to Serializers.optional(Serializers.uint64),
                "float32" to Serializers.optional(Serializers.float32),
                "float64" to Serializers.optional(Serializers.float64),
                "string" to Serializers.optional(Serializers.string),
                "bytes" to Serializers.optional(Serializers.bytes),
                "instant" to Serializers.optional(Serializers.instant),
            )

        val testValues =
            mapOf(
                "bool" to listOf(true, false, null),
                "int32" to listOf(0, 42, -1, Int.MAX_VALUE, Int.MIN_VALUE, null),
                "int64" to listOf(0L, 42L, -1L, Long.MAX_VALUE, Long.MIN_VALUE, null),
                "uint64" to listOf(0UL, 42UL, ULong.MAX_VALUE, null),
                "float32" to listOf(0.0f, 3.14f, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY, null),
                "float64" to listOf(0.0, 3.14159, Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, null),
                "string" to listOf("", "hello", "world", "🚀", null),
                "bytes" to listOf(ByteString.EMPTY, "test".encodeUtf8(), null),
                "instant" to listOf(Instant.EPOCH, Instant.parse("2025-08-25T12:00:00Z"), null),
            )

        optionalSerializers.forEach { (typeName, serializer) ->
            val values = testValues[typeName] ?: error("No test values for $typeName")

            values.forEach { value ->
                @Suppress("UNCHECKED_CAST")
                val typedSerializer = serializer as Serializer<Any?>

                // Test JSON roundtrip
                val json = typedSerializer.toJsonCode(value)
                val restoredFromJson = typedSerializer.fromJsonCode(json)

                if (typeName == "float32" && value is Float && value.isNaN()) {
                    assertTrue((restoredFromJson as Float).isNaN(), "NaN should be preserved for float32")
                } else if (typeName == "float64" && value is Double && value.isNaN()) {
                    assertTrue((restoredFromJson as Double).isNaN(), "NaN should be preserved for float64")
                } else {
                    assertEquals(value, restoredFromJson, "JSON roundtrip failed for $typeName value: $value")
                }

                // Test binary roundtrip
                val bytes = typedSerializer.toBytes(value)
                val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())

                if (typeName == "float32" && value is Float && value.isNaN()) {
                    assertTrue((restoredFromBytes as Float).isNaN(), "NaN should be preserved for float32 in binary")
                } else if (typeName == "float64" && value is Double && value.isNaN()) {
                    assertTrue((restoredFromBytes as Double).isNaN(), "NaN should be preserved for float64 in binary")
                } else {
                    assertEquals(value, restoredFromBytes, "Binary roundtrip failed for $typeName value: $value")
                }
            }
        }
    }

    @Test
    fun `test list serializer with empty arrays`() {
        // Test list serializers with empty arrays
        val intArraySerializer = Serializers.list(Serializers.int32)
        val stringArraySerializer = Serializers.list(Serializers.string)
        val boolArraySerializer = Serializers.list(Serializers.bool)

        // Test empty arrays
        val emptyIntArray = emptyList<Int>()
        val emptyStringArray = emptyList<String>()
        val emptyBoolArray = emptyList<Boolean>()

        // JSON tests
        val emptyIntJson = intArraySerializer.toJsonCode(emptyIntArray)
        assertEquals("[]", emptyIntJson)
        assertEquals(emptyIntArray, intArraySerializer.fromJsonCode(emptyIntJson))

        val emptyStringJson = stringArraySerializer.toJsonCode(emptyStringArray)
        assertEquals("[]", emptyStringJson)
        assertEquals(emptyStringArray, stringArraySerializer.fromJsonCode(emptyStringJson))

        val emptyBoolJson = boolArraySerializer.toJsonCode(emptyBoolArray)
        assertEquals("[]", emptyBoolJson)
        assertEquals(emptyBoolArray, boolArraySerializer.fromJsonCode(emptyBoolJson))

        // Binary tests - empty arrays should have specific wire format
        val emptyIntBytes = intArraySerializer.toBytes(emptyIntArray)
        assertEquals("736f6961f6", emptyIntBytes.hex()) // Should be soia prefix + 0xF6 (246)
        assertEquals(emptyIntArray, intArraySerializer.fromBytes(emptyIntBytes.toByteArray()))

        val emptyStringBytes = stringArraySerializer.toBytes(emptyStringArray)
        assertEquals("736f6961f6", emptyStringBytes.hex())
        assertEquals(emptyStringArray, stringArraySerializer.fromBytes(emptyStringBytes.toByteArray()))

        val emptyBoolBytes = boolArraySerializer.toBytes(emptyBoolArray)
        assertEquals("736f6961f6", emptyBoolBytes.hex())
        assertEquals(emptyBoolArray, boolArraySerializer.fromBytes(emptyBoolBytes.toByteArray()))

        // Test that fromJsonCode("0") also works for empty arrays
        assertEquals(emptyIntArray, intArraySerializer.fromJsonCode("0"))
        assertEquals(emptyStringArray, stringArraySerializer.fromJsonCode("0"))
        assertEquals(emptyBoolArray, boolArraySerializer.fromJsonCode("0"))
    }

    @Test
    fun `test list serializer with small arrays`() {
        val intArraySerializer = Serializers.list(Serializers.int32)
        val stringArraySerializer = Serializers.list(Serializers.string)

        // Test arrays with 1-3 elements (should use wire bytes 247-249)
        val singleIntArray = listOf(42)
        val doubleIntArray = listOf(1, 2)
        val tripleIntArray = listOf(10, 20, 30)

        // JSON tests
        assertEquals("[42]", intArraySerializer.toJsonCode(singleIntArray))
        assertEquals("[1,2]", intArraySerializer.toJsonCode(doubleIntArray))
        assertEquals("[10,20,30]", intArraySerializer.toJsonCode(tripleIntArray))

        assertEquals(singleIntArray, intArraySerializer.fromJsonCode("[42]"))
        assertEquals(doubleIntArray, intArraySerializer.fromJsonCode("[1,2]"))
        assertEquals(tripleIntArray, intArraySerializer.fromJsonCode("[10,20,30]"))

        // Binary tests
        val singleBytes = intArraySerializer.toBytes(singleIntArray)
        assertTrue(singleBytes.hex().startsWith("736f6961f7")) // Should start with soia + 0xF7 (247)
        assertEquals(singleIntArray, intArraySerializer.fromBytes(singleBytes.toByteArray()))

        val doubleBytes = intArraySerializer.toBytes(doubleIntArray)
        assertTrue(doubleBytes.hex().startsWith("736f6961f8")) // Should start with soia + 0xF8 (248)
        assertEquals(doubleIntArray, intArraySerializer.fromBytes(doubleBytes.toByteArray()))

        val tripleBytes = intArraySerializer.toBytes(tripleIntArray)
        assertTrue(tripleBytes.hex().startsWith("736f6961f9")) // Should start with soia + 0xF9 (249)
        assertEquals(tripleIntArray, intArraySerializer.fromBytes(tripleBytes.toByteArray()))

        // Test with strings
        val stringArray = listOf("hello", "world")
        val stringJson = stringArraySerializer.toJsonCode(stringArray)
        assertEquals(listOf("\"hello\"", "\"world\"").joinToString(",", "[", "]"), stringJson)
        assertEquals(stringArray, stringArraySerializer.fromJsonCode(stringJson))

        val stringBytes = stringArraySerializer.toBytes(stringArray)
        assertTrue(stringBytes.hex().startsWith("736f6961f8")) // 2 elements = 0xF8
        assertEquals(stringArray, stringArraySerializer.fromBytes(stringBytes.toByteArray()))
    }

    @Test
    fun `test list serializer with large arrays`() {
        val intArraySerializer = Serializers.list(Serializers.int32)

        // Test arrays with more than 3 elements (should use wire byte 250 + length prefix)
        val largeArray = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val veryLargeArray = (1..1000).toList()

        // JSON tests
        val largeJson = intArraySerializer.toJsonCode(largeArray)
        assertTrue(largeJson.startsWith("[") && largeJson.endsWith("]"))
        assertEquals(largeArray, intArraySerializer.fromJsonCode(largeJson))

        val veryLargeJson = intArraySerializer.toJsonCode(veryLargeArray)
        assertTrue(veryLargeJson.startsWith("[") && veryLargeJson.endsWith("]"))
        assertEquals(veryLargeArray, intArraySerializer.fromJsonCode(veryLargeJson))

        // Binary tests
        val largeBytes = intArraySerializer.toBytes(largeArray)
        assertTrue(largeBytes.hex().startsWith("736f6961fa")) // Should start with soia + 0xFA (250)
        assertEquals(largeArray, intArraySerializer.fromBytes(largeBytes.toByteArray()))

        val veryLargeBytes = intArraySerializer.toBytes(veryLargeArray)
        assertTrue(veryLargeBytes.hex().startsWith("736f6961fa")) // Should start with soia + 0xFA (250)
        assertEquals(veryLargeArray, intArraySerializer.fromBytes(veryLargeBytes.toByteArray()))
    }

    @Test
    fun `test list serializer with different element types`() {
        // Test list serializers with all primitive types
        val boolArraySerializer = Serializers.list(Serializers.bool)
        val int32ArraySerializer = Serializers.list(Serializers.int32)
        val int64ArraySerializer = Serializers.list(Serializers.int64)
        val uint64ArraySerializer = Serializers.list(Serializers.uint64)
        val float32ArraySerializer = Serializers.list(Serializers.float32)
        val float64ArraySerializer = Serializers.list(Serializers.float64)
        val stringArraySerializer = Serializers.list(Serializers.string)
        val bytesArraySerializer = Serializers.list(Serializers.bytes)
        val instantArraySerializer = Serializers.list(Serializers.instant)

        // Test data for each type
        val boolArray = listOf(true, false, true)
        val int32Array = listOf(0, -1, 42, Int.MAX_VALUE, Int.MIN_VALUE)
        val int64Array = listOf(0L, -1L, 42L, Long.MAX_VALUE, Long.MIN_VALUE)
        val uint64Array = listOf(0UL, 1UL, 42UL, ULong.MAX_VALUE)
        val float32Array = listOf(0.0f, 1.0f, -1.0f, 3.14f, Float.NaN, Float.POSITIVE_INFINITY)
        val float64Array = listOf(0.0, 1.0, -1.0, 3.14159, Double.NaN, Double.NEGATIVE_INFINITY)
        val stringArray = listOf("", "hello", "world", "🚀", "Hello, 世界!")
        val bytesArray = listOf(ByteString.EMPTY, "test".encodeUtf8(), "hello world".encodeUtf8())
        val instantArray = listOf(Instant.EPOCH, Instant.parse("2025-08-25T12:00:00Z"))

        val testCases =
            listOf(
                Triple(boolArraySerializer, boolArray, "bool"),
                Triple(int32ArraySerializer, int32Array, "int32"),
                Triple(int64ArraySerializer, int64Array, "int64"),
                Triple(uint64ArraySerializer, uint64Array, "uint64"),
                Triple(float32ArraySerializer, float32Array, "float32"),
                Triple(float64ArraySerializer, float64Array, "float64"),
                Triple(stringArraySerializer, stringArray, "string"),
                Triple(bytesArraySerializer, bytesArray, "bytes"),
                Triple(instantArraySerializer, instantArray, "instant"),
            )

        testCases.forEach { (serializer, list, typeName) ->
            @Suppress("UNCHECKED_CAST")
            val typedSerializer = serializer as Serializer<List<Any>>

            val typedArray = list as List<Any>

            // JSON roundtrip
            val json = typedSerializer.toJsonCode(typedArray)
            val restoredFromJson = typedSerializer.fromJsonCode(json)

            // Special handling for NaN values in float arrays
            if (typeName == "float32" || typeName == "float64") {
                assertEquals(typedArray.size, restoredFromJson.size, "$typeName list size should match")
                typedArray.zip(restoredFromJson).forEach { (original, restored) ->
                    if (original is Float && original.isNaN()) {
                        assertTrue((restored as Float).isNaN(), "NaN should be preserved in float32 list")
                    } else if (original is Double && original.isNaN()) {
                        assertTrue((restored as Double).isNaN(), "NaN should be preserved in float64 list")
                    } else {
                        assertEquals(original, restored, "List element should match for $typeName")
                    }
                }
            } else {
                assertEquals(typedArray, restoredFromJson, "JSON roundtrip failed for $typeName list")
            }

            // Binary roundtrip
            val bytes = typedSerializer.toBytes(typedArray)
            val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())

            if (typeName == "float32" || typeName == "float64") {
                assertEquals(typedArray.size, restoredFromBytes.size, "$typeName list size should match in binary")
                typedArray.zip(restoredFromBytes).forEach { (original, restored) ->
                    if (original is Float && original.isNaN()) {
                        assertTrue((restored as Float).isNaN(), "NaN should be preserved in float32 list binary")
                    } else if (original is Double && original.isNaN()) {
                        assertTrue((restored as Double).isNaN(), "NaN should be preserved in float64 list binary")
                    } else {
                        assertEquals(original, restored, "List element should match for $typeName in binary")
                    }
                }
            } else {
                assertEquals(typedArray, restoredFromBytes, "Binary roundtrip failed for $typeName list")
            }
        }
    }

    @Test
    fun `test nested list serializers`() {
        val intArraySerializer = Serializers.list(Serializers.int32)
        val nestedIntArraySerializer = Serializers.list(intArraySerializer)

        // Test list of arrays
        val nestedArray =
            listOf(
                listOf(1, 2, 3),
                listOf(4, 5),
                emptyList(),
                listOf(6),
            )

        // JSON roundtrip
        val json = nestedIntArraySerializer.toJsonCode(nestedArray)
        assertTrue(json.startsWith("[[") || json.startsWith("[ ["), "Should be nested list structure")
        val restoredFromJson = nestedIntArraySerializer.fromJsonCode(json)
        assertEquals(nestedArray, restoredFromJson)

        // Binary roundtrip
        val bytes = nestedIntArraySerializer.toBytes(nestedArray)
        assertTrue(bytes.hex().startsWith("736f6961"), "Should start with soia prefix")
        val restoredFromBytes = nestedIntArraySerializer.fromBytes(bytes.toByteArray())
        assertEquals(nestedArray, restoredFromBytes)

        // Test deeply nested arrays
        val deeplyNestedArraySerializer = Serializers.list(nestedIntArraySerializer)
        val deeplyNestedArray =
            listOf(
                listOf(listOf(1, 2), listOf(3, 4)),
                listOf(listOf(5), emptyList()),
            )

        val deepJson = deeplyNestedArraySerializer.toJsonCode(deeplyNestedArray)
        val restoredDeepFromJson = deeplyNestedArraySerializer.fromJsonCode(deepJson)
        assertEquals(deeplyNestedArray, restoredDeepFromJson)

        val deepBytes = deeplyNestedArraySerializer.toBytes(deeplyNestedArray)
        val restoredDeepFromBytes = deeplyNestedArraySerializer.fromBytes(deepBytes.toByteArray())
        assertEquals(deeplyNestedArray, restoredDeepFromBytes)
    }

    @Test
    fun `test list serializer with optional elements`() {
        val optionalIntSerializer = Serializers.optional(Serializers.int32)
        val optionalIntArraySerializer = Serializers.list(optionalIntSerializer)

        // Test list with optional elements (some null, some not)
        val mixedArray = listOf(1, null, 42, null, 0)

        // JSON tests
        val json = optionalIntArraySerializer.toJsonCode(mixedArray)
        assertTrue(json.contains("null"), "JSON should contain null values")
        val restoredFromJson = optionalIntArraySerializer.fromJsonCode(json)
        assertEquals(mixedArray, restoredFromJson)

        // Binary tests
        val bytes = optionalIntArraySerializer.toBytes(mixedArray)
        val restoredFromBytes = optionalIntArraySerializer.fromBytes(bytes.toByteArray())
        assertEquals(mixedArray, restoredFromBytes)

        // Test all null list
        val allNullArray = listOf<Int?>(null, null, null)
        val allNullJson = optionalIntArraySerializer.toJsonCode(allNullArray)
        assertEquals("[null,null,null]", allNullJson)
        assertEquals(allNullArray, optionalIntArraySerializer.fromJsonCode(allNullJson))

        val allNullBytes = optionalIntArraySerializer.toBytes(allNullArray)
        assertEquals(allNullArray, optionalIntArraySerializer.fromBytes(allNullBytes.toByteArray()))

        // Test no null list
        val noNullArray = listOf<Int?>(1, 2, 3)
        val noNullJson = optionalIntArraySerializer.toJsonCode(noNullArray)
        assertEquals("[1,2,3]", noNullJson)
        assertEquals(noNullArray, optionalIntArraySerializer.fromJsonCode(noNullJson))

        val noNullBytes = optionalIntArraySerializer.toBytes(noNullArray)
        assertEquals(noNullArray, optionalIntArraySerializer.fromBytes(noNullBytes.toByteArray()))
    }

    @Test
    fun `test list serializer edge cases`() {
        val intArraySerializer = Serializers.list(Serializers.int32)
        val stringArraySerializer = Serializers.list(Serializers.string)

        // Test arrays with edge case values
        val edgeCaseIntArray = listOf(0, -1, 1, Int.MAX_VALUE, Int.MIN_VALUE, 232, 65536)
        val edgeCaseStringArray = listOf("", "0", "null", "false", "true", "[]", "{}")

        // Test roundtrips
        val intJson = intArraySerializer.toJsonCode(edgeCaseIntArray)
        assertEquals(edgeCaseIntArray, intArraySerializer.fromJsonCode(intJson))
        val intBytes = intArraySerializer.toBytes(edgeCaseIntArray)
        assertEquals(edgeCaseIntArray, intArraySerializer.fromBytes(intBytes.toByteArray()))

        val stringJson = stringArraySerializer.toJsonCode(edgeCaseStringArray)
        assertEquals(edgeCaseStringArray, stringArraySerializer.fromJsonCode(stringJson))
        val stringBytes = stringArraySerializer.toBytes(edgeCaseStringArray)
        assertEquals(edgeCaseStringArray, stringArraySerializer.fromBytes(stringBytes.toByteArray()))

        // Test very large list (boundary testing)
        val boundaryArray = (1..100).toList()
        val boundaryJson = intArraySerializer.toJsonCode(boundaryArray)
        assertEquals(boundaryArray, intArraySerializer.fromJsonCode(boundaryJson))
        val boundaryBytes = intArraySerializer.toBytes(boundaryArray)
        assertEquals(boundaryArray, intArraySerializer.fromBytes(boundaryBytes.toByteArray()))

        // Test single element arrays with special values
        val singleZeroArray = listOf(0)
        val singleZeroJson = intArraySerializer.toJsonCode(singleZeroArray)
        assertEquals("[0]", singleZeroJson)
        assertEquals(singleZeroArray, intArraySerializer.fromJsonCode(singleZeroJson))

        val singleEmptyStringArray = listOf("")
        val singleEmptyStringJson = stringArraySerializer.toJsonCode(singleEmptyStringArray)
        assertEquals("[\"\"]", singleEmptyStringJson)
        assertEquals(singleEmptyStringArray, stringArraySerializer.fromJsonCode(singleEmptyStringJson))
    }

    @Test
    fun `test list serializer binary format specifics`() {
        val intArraySerializer = Serializers.list(Serializers.int32)

        // Test specific binary format expectations based on list size
        val testCases =
            mapOf(
                emptyList<Int>() to "736f6961f6",
                listOf(1) to "736f6961f701",
                listOf(1, 2) to "736f6961f80102",
                listOf(1, 2, 3) to "736f6961f9010203",
            )

        testCases.forEach { (list, expectedHexPrefix) ->
            val bytes = intArraySerializer.toBytes(list)
            if (list.size <= 3) {
                assertEquals(expectedHexPrefix, bytes.hex(), "Binary encoding failed for list: $list")
            } else {
                assertTrue(bytes.hex().startsWith("736f6961fa"), "Large list should start with soia + 0xFA")
            }
            assertEquals(list, intArraySerializer.fromBytes(bytes.toByteArray()))
        }

        // Test large list format
        val largeArray = (1..10).toList()
        val largeBytes = intArraySerializer.toBytes(largeArray)
        assertTrue(largeBytes.hex().startsWith("736f6961fa0a"), "10 elements should be soia + 0xFA + 0x0A")
        assertEquals(largeArray, intArraySerializer.fromBytes(largeBytes.toByteArray()))
    }

    @Test
    fun `test list serializer performance with large arrays`() {
        val intArraySerializer = Serializers.list(Serializers.int32)

        // Test with reasonably large arrays to ensure no stack overflow or performance issues
        val mediumArray = (1..1000).toList()
        val largeArray = (1..10000).toList()

        // JSON roundtrip
        val mediumJson = intArraySerializer.toJsonCode(mediumArray)
        assertTrue(mediumJson.length > 1000, "JSON should be substantial for 1000 elements")
        assertEquals(mediumArray, intArraySerializer.fromJsonCode(mediumJson))

        val largeJson = intArraySerializer.toJsonCode(largeArray)
        assertTrue(largeJson.length > 10000, "JSON should be substantial for 10000 elements")
        assertEquals(largeArray, intArraySerializer.fromJsonCode(largeJson))

        // Binary roundtrip
        val mediumBytes = intArraySerializer.toBytes(mediumArray)
        assertTrue(mediumBytes.size > 1000, "Binary should be substantial for 1000 elements")
        assertEquals(mediumArray, intArraySerializer.fromBytes(mediumBytes.toByteArray()))

        val largeBytes = intArraySerializer.toBytes(largeArray)
        assertTrue(largeBytes.size > 10000, "Binary should be substantial for 10000 elements")
        assertEquals(largeArray, intArraySerializer.fromBytes(largeBytes.toByteArray()))
    }
}
