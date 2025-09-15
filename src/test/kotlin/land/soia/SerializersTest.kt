package land.soia

import com.google.common.truth.Truth.assertThat
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonPrimitive
import land.soia.internal.toStringImpl
import okio.ByteString
import okio.ByteString.Companion.decodeHex
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Test
import java.time.Instant

class SerializersTest {
    @Test
    fun `test bool serializer - basic functionality`() {
        // Test true value - should be 1 in dense, true in readable
        assertThat((Serializers.bool.toJson(false, readableFlavor = true).jsonPrimitive.content)).isEqualTo("false")
        assertThat((Serializers.bool.toJson(false, readableFlavor = false).jsonPrimitive.content)).isEqualTo("0")
        assertThat((Serializers.bool.toJson(true, readableFlavor = true).jsonPrimitive.content)).isEqualTo("true")
        assertThat((Serializers.bool.toJson(true, readableFlavor = false).jsonPrimitive.content)).isEqualTo("1")

        // Test round-trip
        assertThat(Serializers.bool.fromJson(JsonPrimitive(true))).isEqualTo(true)
        assertThat(Serializers.bool.fromJsonCode("true")).isEqualTo(true)
        assertThat(Serializers.bool.fromJsonCode("1")).isEqualTo(true)
        assertThat(Serializers.bool.fromJsonCode("100")).isEqualTo(true)
        assertThat(Serializers.bool.fromJsonCode("3.14")).isEqualTo(true)
        assertThat(Serializers.bool.fromJsonCode("\"1\"")).isEqualTo(true)
        assertThat(Serializers.bool.fromJsonCode("false")).isEqualTo(false)
        assertThat(Serializers.bool.fromJsonCode("0")).isEqualTo(false)
        assertThat(Serializers.bool.fromJsonCode("\"0\"")).isEqualTo(false)
    }

    @Test
    fun `test bool serializer - binary serialization`() {
        // From TypeScript tests: true -> "01", false -> "00"
        val trueBytes = Serializers.bool.toBytes(true)
        assertThat(trueBytes.hex()).isEqualTo("736f696101")

        val falseBytes = Serializers.bool.toBytes(false)
        assertThat(falseBytes.hex()).isEqualTo("736f696100")

        // Test round trip
        val restoredTrue = Serializers.bool.fromBytes(trueBytes.toByteArray())
        assertThat(restoredTrue).isEqualTo(true)

        val restoredFalse = Serializers.bool.fromBytes(falseBytes)
        assertThat(restoredFalse).isEqualTo(false)

        assertThat(Serializers.bool.fromBytes(Serializers.int32.toBytes(100))).isEqualTo(true)
        assertThat(Serializers.bool.fromBytes(Serializers.float32.toBytes(3.14F))).isEqualTo(true)
        assertThat(Serializers.bool.fromBytes(Serializers.uint64.toBytes(10000000000000000UL))).isEqualTo(true)
        assertThat(Serializers.bool.fromBytes(Serializers.uint64.toBytes(0UL))).isEqualTo(false)
        assertThat(Serializers.bool.fromBytes(Serializers.int64.toBytes(-1))).isEqualTo(true)
    }

    @Test
    fun `test int32 serializer`() {
        val values = listOf(0, 1, -1, 42, -42, Int.MAX_VALUE, Int.MIN_VALUE)

        for (value in values) {
            // JSON serialization
            val jsonCode = Serializers.int32.toJsonCode(value)
            val restored = Serializers.int32.fromJsonCode(jsonCode)
            assertThat(restored).isEqualTo(value)

            // Binary serialization
            val bytes = Serializers.int32.toBytes(value)
            val restoredFromBytes = Serializers.int32.fromBytes(bytes.toByteArray())
            assertThat(restoredFromBytes).isEqualTo(value)

            // JsonFlavor shouldn't affect primitive numbers
            assertThat(
                Serializers.int32.toJsonCode(value, readableFlavor = false),
            ).isEqualTo(
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
            assertThat(restored).isEqualTo(value)

            val bytes = Serializers.int64.toBytes(value)
            val restoredFromBytes = Serializers.int64.fromBytes(bytes.toByteArray())
            assertThat(restoredFromBytes).isEqualTo(value)
        }
    }

    @Test
    fun `test uint64 serializer`() {
        val values = listOf(0UL, 1UL, 42UL, ULong.MAX_VALUE)

        for (value in values) {
            val jsonCode = Serializers.uint64.toJsonCode(value)
            val restored = Serializers.uint64.fromJsonCode(jsonCode)
            assertThat(restored).isEqualTo(value)

            val bytes = Serializers.uint64.toBytes(value)
            val restoredFromBytes = Serializers.uint64.fromBytes(bytes.toByteArray())
            assertThat(restoredFromBytes).isEqualTo(value)
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
                assertThat(restored.isNaN()).isTrue()
            } else {
                assertThat(restored).isEqualTo(value)
            }

            val bytes = Serializers.float32.toBytes(value)
            val restoredFromBytes = Serializers.float32.fromBytes(bytes.toByteArray())

            if (value.isNaN()) {
                assertThat(restoredFromBytes.isNaN()).isTrue()
            } else {
                assertThat(restoredFromBytes).isEqualTo(value)
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
                assertThat(restored.isNaN()).isTrue()
            } else {
                assertThat(restored).isEqualTo(value)
            }

            val bytes = Serializers.float64.toBytes(value)
            val restoredFromBytes = Serializers.float64.fromBytes(bytes.toByteArray())

            if (value.isNaN()) {
                assertThat(restoredFromBytes.isNaN()).isTrue()
            } else {
                assertThat(restoredFromBytes).isEqualTo(value)
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
            assertThat(restored).isEqualTo(value)

            val bytes = Serializers.string.toBytes(value)
            val restoredFromBytes = Serializers.string.fromBytes(bytes.toByteArray())
            assertThat(restoredFromBytes).isEqualTo(value)

            // JsonFlavor shouldn't affect plain strings
            assertThat(
                Serializers.string.toJsonCode(value, readableFlavor = true),
            ).isEqualTo(
                Serializers.string.toJsonCode(value, readableFlavor = false),
            )
        }

        assertThat(Serializers.string.fromJsonCode("0")).isEqualTo("")
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
            assertThat(restoredFromDense).isEqualTo(value)

            // Dense should produce a simple string
            assertThat(denseJson).startsWith("\"")
            assertThat(denseJson).endsWith("\"")

            // Binary serialization
            val bytes = Serializers.bytes.toBytes(value)
            val restoredFromBytes = Serializers.bytes.fromBytes(bytes.toByteArray())
            assertThat(restoredFromBytes).isEqualTo(value)
        }

        assertThat(Serializers.bytes.fromJsonCode("0")).isEqualTo(ByteString.EMPTY)
    }

    @Test
    fun `test timestamp serializer - dense flavor`() {
        val instants =
            listOf(
                Instant.EPOCH,
                Instant.parse("2025-08-25T10:30:45Z"),
                Instant.parse("2000-01-01T00:00:00Z"),
                Instant.ofEpochMilli(System.currentTimeMillis()),
            )

        for (instant in instants) {
            val denseJson = Serializers.timestamp.toJsonCode(instant, readableFlavor = false)
            val restored = Serializers.timestamp.fromJsonCode(denseJson)
            assertThat(restored).isEqualTo(instant)

            // Dense should produce a unix milliseconds number
            val denseJsonValue = denseJson.toLongOrNull()
            assertThat(denseJsonValue).isNotNull()

            // Binary serialization
            val bytes = Serializers.timestamp.toBytes(instant)
            val restoredFromBytes = Serializers.timestamp.fromBytes(bytes.toByteArray())
            assertThat(restoredFromBytes).isEqualTo(instant)
        }
    }

    @Test
    fun `test timestamp serializer - readable flavor`() {
        val instant = Instant.parse("2025-08-25T10:30:45Z")
        val readableJson = Serializers.timestamp.toJsonCode(instant, readableFlavor = true)

        // Readable should produce an object with unix_millis and formatted
        assertThat(
            readableJson,
        ).isEqualTo(
            listOf(
                "{",
                "  \"unix_millis\": 1756117845000,",
                "  \"formatted\": \"2025-08-25T10:30:45Z\"",
                "}",
            ).joinToString(separator = "\n") { it },
        )

        val restoredFromReadable = Serializers.timestamp.fromJsonCode(readableJson)
        assertThat(restoredFromReadable).isEqualTo(instant)
    }

    @Test
    fun `test edge cases and error handling`() {
        // Test empty string
        assertThat(Serializers.string.fromJsonCode("\"\"")).isEqualTo("")

        // Test zero values
        assertThat(Serializers.int32.fromJsonCode("0")).isEqualTo(0)
        assertThat(Serializers.int64.fromJsonCode("0")).isEqualTo(0L)
        assertThat(Serializers.float32.fromJsonCode("0.0")).isEqualTo(0.0f)
        assertThat(Serializers.float64.fromJsonCode("0.0")).isEqualTo(0.0)

        // Test empty bytes
        assertThat(Serializers.bytes.fromJsonCode("\"\"")).isEqualTo(ByteString.EMPTY)
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
            assertThat(restored).isEqualTo(value)

            // Test binary roundtrip
            val bytes = typedSerializer.toBytes(value)
            val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())
            assertThat(restoredFromBytes).isEqualTo(value)
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
            assertThat(bytes.hex()).isEqualTo(expectedHex)

            val restored = Serializers.int32.fromBytes(bytes.toByteArray())
            assertThat(restored).isEqualTo(value)
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
                assertThat(restored.isNaN()).isTrue()
            } else {
                val restored = Serializers.float32.fromJsonCode(json)
                assertThat(restored).isEqualTo(value)
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
                assertThat(restored.isNaN()).isTrue()
            } else {
                val restored = Serializers.float64.fromJsonCode(json)
                assertThat(restored).isEqualTo(value)
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
        assertThat(safeJson).isEqualTo("123456789")

        // Values outside safe range should be strings
        val unsafeValue = Long.MAX_VALUE
        val unsafeJson = Serializers.int64.toJsonCode(unsafeValue)
        assertThat(unsafeJson).startsWith("\"")
        assertThat(unsafeJson).endsWith("\"")

        // Both should roundtrip correctly
        assertThat(Serializers.int64.fromJsonCode(safeJson)).isEqualTo(safeValue)
        assertThat(Serializers.int64.fromJsonCode(unsafeJson)).isEqualTo(unsafeValue)
    }

    @Test
    fun `test uint64 large number json serialization`() {
        // Test JavaScript safe integer boundaries for unsigned values
        val safeValue = 123456789UL
        val safeJson = Serializers.uint64.toJsonCode(safeValue)
        assertThat(safeJson).isEqualTo("123456789")

        // Values outside safe range should be strings
        val unsafeValue = ULong.MAX_VALUE
        val unsafeJson = Serializers.uint64.toJsonCode(unsafeValue)
        assertThat(unsafeJson).startsWith("\"")
        assertThat(unsafeJson).endsWith("\"")

        // Both should roundtrip correctly
        assertThat(Serializers.uint64.fromJsonCode(safeJson)).isEqualTo(safeValue)
        assertThat(Serializers.uint64.fromJsonCode(unsafeJson)).isEqualTo(unsafeValue)
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
            assertThat(bytes.hex()).isEqualTo(expectedHex)

            val restored = Serializers.string.fromBytes(bytes.toByteArray())
            assertThat(restored).isEqualTo(value)
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
            assertThat(bytes.hex()).isEqualTo(expectedHex)

            val restored = Serializers.bytes.fromBytes(bytes.toByteArray())
            assertThat(restored).isEqualTo(value)
        }
    }

    @Test
    fun `test instant clamping behavior`() {
        // Test that instants are clamped to valid JavaScript Date range
        val minValid = Instant.ofEpochMilli(-8640000000000000L)
        val maxValid = Instant.ofEpochMilli(8640000000000000L)

        // Test that these values roundtrip correctly
        val minBytes = Serializers.timestamp.toBytes(minValid)
        val restoredMin = Serializers.timestamp.fromBytes(minBytes.toByteArray())
        assertThat(restoredMin).isEqualTo(minValid)

        val maxBytes = Serializers.timestamp.toBytes(maxValid)
        val restoredMax = Serializers.timestamp.fromBytes(maxBytes.toByteArray())
        assertThat(restoredMax).isEqualTo(maxValid)
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
            val bytes = Serializers.timestamp.toBytes(instant)
            assertThat(bytes.hex()).isEqualTo(expectedHex)

            val restored = Serializers.timestamp.fromBytes(bytes.toByteArray())
            assertThat(restored).isEqualTo(instant)
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
            assertThat(restoredFromJson).isEqualTo(defaultValue)

            // Test binary roundtrip for default values
            val bytes = typedSerializer.toBytes(defaultValue)
            val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())
            assertThat(restoredFromBytes).isEqualTo(defaultValue)
        }
    }

    @Test
    fun `test optional serializer with non-null values`() {
        // Test optional serializer with various non-null values
        val intOptional = Serializers.optional(Serializers.int32)
        val stringOptional = Serializers.optional(Serializers.string)
        val boolOptional = Serializers.optional(Serializers.bool)
        val bytesOptional = Serializers.optional(Serializers.bytes)
        val instantOptional = Serializers.optional(Serializers.timestamp)

        // Test non-null values
        val testValue = 42
        val jsonCode = intOptional.toJsonCode(testValue)
        val restored = intOptional.fromJsonCode(jsonCode)
        assertThat(restored).isEqualTo(testValue)

        val bytes = intOptional.toBytes(testValue)
        val restoredFromBytes = intOptional.fromBytes(bytes.toByteArray())
        assertThat(restoredFromBytes).isEqualTo(testValue)

        // Test string
        val testString = "hello world"
        val stringJson = stringOptional.toJsonCode(testString)
        val restoredString = stringOptional.fromJsonCode(stringJson)
        assertThat(restoredString).isEqualTo(testString)

        val stringBytes = stringOptional.toBytes(testString)
        val restoredStringFromBytes = stringOptional.fromBytes(stringBytes.toByteArray())
        assertThat(restoredStringFromBytes).isEqualTo(testString)

        // Test bool
        val testBool = true
        val boolJson = boolOptional.toJsonCode(testBool)
        val restoredBool = boolOptional.fromJsonCode(boolJson)
        assertThat(restoredBool).isEqualTo(testBool)

        val boolBytes = boolOptional.toBytes(testBool)
        val restoredBoolFromBytes = boolOptional.fromBytes(boolBytes.toByteArray())
        assertThat(restoredBoolFromBytes).isEqualTo(testBool)

        // Test bytes
        val testBytes = "test data".encodeUtf8()
        val bytesJson = bytesOptional.toJsonCode(testBytes)
        val restoredBytes = bytesOptional.fromJsonCode(bytesJson)
        assertThat(restoredBytes).isEqualTo(testBytes)

        val bytesBinary = bytesOptional.toBytes(testBytes)
        val restoredBytesFromBinary = bytesOptional.fromBytes(bytesBinary.toByteArray())
        assertThat(restoredBytesFromBinary).isEqualTo(testBytes)

        // Test instant
        val testTimestamp = Instant.parse("2025-08-25T12:00:00Z")
        val instantJson = instantOptional.toJsonCode(testTimestamp)
        val restoredTimestamp = instantOptional.fromJsonCode(instantJson)
        assertThat(restoredTimestamp).isEqualTo(testTimestamp)

        val instantBytes = instantOptional.toBytes(testTimestamp)
        val restoredTimestampFromBytes = instantOptional.fromBytes(instantBytes.toByteArray())
        assertThat(restoredTimestampFromBytes).isEqualTo(testTimestamp)
    }

    @Test
    fun `test optional serializer with null values`() {
        // Test optional serializer with null values
        val intOptional = Serializers.optional(Serializers.int32)
        val stringOptional = Serializers.optional(Serializers.string)
        val boolOptional = Serializers.optional(Serializers.bool)
        val bytesOptional = Serializers.optional(Serializers.bytes)
        val instantOptional = Serializers.optional(Serializers.timestamp)

        // Test null values in JSON
        val nullJson = intOptional.toJsonCode(null)
        assertThat(nullJson).isEqualTo("null")
        val restoredNull = intOptional.fromJsonCode(nullJson)
        assertThat(restoredNull).isEqualTo(null)

        // Test null values in binary format
        val nullBytes = intOptional.toBytes(null)
        assertThat(nullBytes.hex()).isEqualTo("736f6961ff") // Should end with 0xFF for null
        val restoredNullFromBytes = intOptional.fromBytes(nullBytes.toByteArray())
        assertThat(restoredNullFromBytes).isEqualTo(null)

        // Test all types with null
        listOf(intOptional, stringOptional, boolOptional, bytesOptional, instantOptional).forEach { serializer ->
            @Suppress("UNCHECKED_CAST")
            val typedSerializer = serializer as Serializer<Any?>

            val json = typedSerializer.toJsonCode(null)
            assertThat(json).isEqualTo("null")
            assertThat(typedSerializer.fromJsonCode(json)).isEqualTo(null)

            val bytes = typedSerializer.toBytes(null)
            assertThat(bytes.hex().endsWith("ff")).isTrue()
            assertThat(typedSerializer.fromBytes(bytes.toByteArray())).isEqualTo(null)
        }
    }

    @Test
    fun `test optional serializer json flavors`() {
        val intOptional = Serializers.optional(Serializers.int32)
        val instantOptional = Serializers.optional(Serializers.timestamp)
        val boolOptional = Serializers.optional(Serializers.bool)

        // Test non-null values with different flavors
        val testInt = 42
        val denseIntJson = intOptional.toJsonCode(testInt, readableFlavor = false)
        val readableIntJson = intOptional.toJsonCode(testInt, readableFlavor = true)
        assertThat(readableIntJson).isEqualTo(denseIntJson) // Should be the same for int32

        // Test instant with different flavors
        val testTimestamp = Instant.parse("2025-08-25T12:00:00Z")
        val denseTimestampJson = instantOptional.toJsonCode(testTimestamp, readableFlavor = false)
        val readableTimestampJson = instantOptional.toJsonCode(testTimestamp, readableFlavor = true)
        // Dense should be a number, readable should be an object
        assertThat(denseTimestampJson.toLongOrNull() != null).isTrue()
        assertThat(readableTimestampJson.contains("unix_millis")).isTrue()

        // Test bool with different flavors
        val testBool = true
        val denseBoolJson = boolOptional.toJsonCode(testBool, readableFlavor = false)
        val readableBoolJson = boolOptional.toJsonCode(testBool, readableFlavor = true)
        assertThat(denseBoolJson).isEqualTo("1") // Dense should be "1"
        assertThat(readableBoolJson).isEqualTo("true") // Readable should be "true"

        // Test null with different flavors (should always be "null")
        val nullDense = intOptional.toJsonCode(null, readableFlavor = false)
        val nullReadable = intOptional.toJsonCode(null, readableFlavor = true)
        assertThat(nullDense).isEqualTo("null")
        assertThat(nullReadable).isEqualTo("null")
    }

    @Test
    fun `test optional serializer idempotency`() {
        // Test that calling optional on an already optional serializer returns the same instance
        val intOptional = Serializers.optional(Serializers.int32)
        val doubleOptional = Serializers.optional(intOptional)

        // They should be the same instance (idempotent)
        assertThat(intOptional === doubleOptional).isTrue()

        // Test functionality is preserved
        val testValue = 123
        assertThat(intOptional.fromJsonCode(intOptional.toJsonCode(testValue))).isEqualTo(testValue)
        assertThat(doubleOptional.fromJsonCode(doubleOptional.toJsonCode(testValue))).isEqualTo(testValue)
        assertThat(intOptional.fromJsonCode(intOptional.toJsonCode(null))).isEqualTo(null)
        assertThat(doubleOptional.fromJsonCode(doubleOptional.toJsonCode(null))).isEqualTo(null)

        // Binary serialization should also work the same
        val testBytes = intOptional.toBytes(testValue)
        val doubleBytes = doubleOptional.toBytes(testValue)
        assertThat(doubleBytes).isEqualTo(testBytes)

        assertThat(intOptional.fromBytes(testBytes.toByteArray())).isEqualTo(testValue)
        assertThat(doubleOptional.fromBytes(doubleBytes.toByteArray())).isEqualTo(testValue)
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
                assertThat(restoredFromJson).isEqualTo(value)

                // Test binary roundtrip
                val bytes = typedSerializer.toBytes(value)
                val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())
                assertThat(restoredFromBytes).isEqualTo(value)
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
            assertThat(bytes.hex()).isEqualTo(expectedHex)

            val restored = intOptional.fromBytes(bytes.toByteArray())
            assertThat(restored).isEqualTo(value)
        }
    }

    @Test
    fun `test nested optional serializers`() {
        // While the API prevents double-optional, we can test complex scenarios
        val intOptional = Serializers.optional(Serializers.int32)
        val stringOptional = Serializers.optional(Serializers.string)
        val instantOptional = Serializers.optional(Serializers.timestamp)

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
                assertThat(json1 != json2).isTrue()
            }

            // Test roundtrip for both values
            assertThat(typedSerializer.fromJsonCode(json1)).isEqualTo(value1)
            assertThat(typedSerializer.fromJsonCode(json2)).isEqualTo(value2)

            // Test binary serialization
            val bytes1 = typedSerializer.toBytes(value1)
            val bytes2 = typedSerializer.toBytes(value2)

            if (value1 != value2) {
                assertThat(bytes1 != bytes2).isTrue()
            }

            assertThat(typedSerializer.fromBytes(bytes1.toByteArray())).isEqualTo(value1)
            assertThat(typedSerializer.fromBytes(bytes2.toByteArray())).isEqualTo(value2)
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
                "instant" to Serializers.optional(Serializers.timestamp),
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
                    assertThat((restoredFromJson as Float).isNaN()).isTrue()
                } else if (typeName == "float64" && value is Double && value.isNaN()) {
                    assertThat((restoredFromJson as Double).isNaN()).isTrue()
                } else {
                    assertThat(restoredFromJson).isEqualTo(value)
                }

                // Test binary roundtrip
                val bytes = typedSerializer.toBytes(value)
                val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())

                if (typeName == "float32" && value is Float && value.isNaN()) {
                    assertThat((restoredFromBytes as Float).isNaN()).isTrue()
                } else if (typeName == "float64" && value is Double && value.isNaN()) {
                    assertThat((restoredFromBytes as Double).isNaN()).isTrue()
                } else {
                    assertThat(restoredFromBytes).isEqualTo(value)
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
        assertThat(emptyIntJson).isEqualTo("[]")
        assertThat(intArraySerializer.fromJsonCode(emptyIntJson)).isEqualTo(emptyIntArray)

        val emptyStringJson = stringArraySerializer.toJsonCode(emptyStringArray)
        assertThat(emptyStringJson).isEqualTo("[]")
        assertThat(stringArraySerializer.fromJsonCode(emptyStringJson)).isEqualTo(emptyStringArray)

        val emptyBoolJson = boolArraySerializer.toJsonCode(emptyBoolArray)
        assertThat(emptyBoolJson).isEqualTo("[]")
        assertThat(boolArraySerializer.fromJsonCode(emptyBoolJson)).isEqualTo(emptyBoolArray)

        // Binary tests - empty arrays should have specific wire format
        val emptyIntBytes = intArraySerializer.toBytes(emptyIntArray)
        assertThat(emptyIntBytes.hex()) // Should be soia prefix + 0xF6 (246).isEqualTo("736f6961f6")
        assertThat(intArraySerializer.fromBytes(emptyIntBytes.toByteArray())).isEqualTo(emptyIntArray)

        val emptyStringBytes = stringArraySerializer.toBytes(emptyStringArray)
        assertThat(emptyStringBytes.hex()).isEqualTo("736f6961f6")
        assertThat(stringArraySerializer.fromBytes(emptyStringBytes.toByteArray())).isEqualTo(emptyStringArray)

        val emptyBoolBytes = boolArraySerializer.toBytes(emptyBoolArray)
        assertThat(emptyBoolBytes.hex()).isEqualTo("736f6961f6")
        assertThat(boolArraySerializer.fromBytes(emptyBoolBytes.toByteArray())).isEqualTo(emptyBoolArray)

        // Test that fromJsonCode("0") also works for empty arrays
        assertThat(intArraySerializer.fromJsonCode("0")).isEqualTo(emptyIntArray)
        assertThat(stringArraySerializer.fromJsonCode("0")).isEqualTo(emptyStringArray)
        assertThat(boolArraySerializer.fromJsonCode("0")).isEqualTo(emptyBoolArray)
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
        assertThat(intArraySerializer.toJsonCode(singleIntArray)).isEqualTo("[42]")
        assertThat(intArraySerializer.toJsonCode(doubleIntArray)).isEqualTo("[1,2]")
        assertThat(intArraySerializer.toJsonCode(tripleIntArray)).isEqualTo("[10,20,30]")

        assertThat(intArraySerializer.fromJsonCode("[42]")).isEqualTo(singleIntArray)
        assertThat(intArraySerializer.fromJsonCode("[1,2]")).isEqualTo(doubleIntArray)
        assertThat(intArraySerializer.fromJsonCode("[10,20,30]")).isEqualTo(tripleIntArray)

        // Binary tests
        val singleBytes = intArraySerializer.toBytes(singleIntArray)
        assertThat(singleBytes.hex()).startsWith("736f6961f7")
        assertThat(intArraySerializer.fromBytes(singleBytes.toByteArray())).isEqualTo(singleIntArray)

        val doubleBytes = intArraySerializer.toBytes(doubleIntArray)
        assertThat(doubleBytes.hex()).startsWith("736f6961f8")
        assertThat(intArraySerializer.fromBytes(doubleBytes.toByteArray())).isEqualTo(doubleIntArray)

        val tripleBytes = intArraySerializer.toBytes(tripleIntArray)
        assertThat(tripleBytes.hex()).startsWith("736f6961f9")
        assertThat(intArraySerializer.fromBytes(tripleBytes.toByteArray())).isEqualTo(tripleIntArray)

        // Test with strings
        val stringArray = listOf("hello", "world")
        val stringJson = stringArraySerializer.toJsonCode(stringArray)
        assertThat(stringJson).isEqualTo(listOf("\"hello\"", "\"world\"").joinToString(",", "[", "]"))
        assertThat(stringArraySerializer.fromJsonCode(stringJson)).isEqualTo(stringArray)

        val stringBytes = stringArraySerializer.toBytes(stringArray)
        assertThat(stringBytes.hex()).startsWith("736f6961f8")
        assertThat(stringArraySerializer.fromBytes(stringBytes.toByteArray())).isEqualTo(stringArray)
    }

    @Test
    fun `test list serializer with large arrays`() {
        val intArraySerializer = Serializers.list(Serializers.int32)

        // Test arrays with more than 3 elements (should use wire byte 250 + length prefix)
        val largeArray = listOf(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)
        val veryLargeArray = (1..1000).toList()

        // JSON tests
        val largeJson = intArraySerializer.toJsonCode(largeArray)
        assertThat(largeJson).startsWith("[")
        assertThat(largeJson.endsWith("]"))
        assertThat(intArraySerializer.fromJsonCode(largeJson)).isEqualTo(largeArray)

        val veryLargeJson = intArraySerializer.toJsonCode(veryLargeArray)
        assertThat(veryLargeJson).startsWith("[")
        assertThat(veryLargeJson).endsWith("]")
        assertThat(intArraySerializer.fromJsonCode(veryLargeJson)).isEqualTo(veryLargeArray)

        // Binary tests
        val largeBytes = intArraySerializer.toBytes(largeArray)
        assertThat(largeBytes.hex()).startsWith("736f6961fa")
        assertThat(intArraySerializer.fromBytes(largeBytes.toByteArray())).isEqualTo(largeArray)

        val veryLargeBytes = intArraySerializer.toBytes(veryLargeArray)
        assertThat(veryLargeBytes.hex()).startsWith("736f6961fa")
        assertThat(intArraySerializer.fromBytes(veryLargeBytes.toByteArray())).isEqualTo(veryLargeArray)
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
        val instantArraySerializer = Serializers.list(Serializers.timestamp)

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
                assertThat(restoredFromJson.size).isEqualTo(typedArray.size)
                typedArray.zip(restoredFromJson).forEach { (original, restored) ->
                    if (original is Float && original.isNaN()) {
                        assertThat((restored as Float).isNaN()).isTrue()
                    } else if (original is Double && original.isNaN()) {
                        assertThat((restored as Double).isNaN()).isTrue()
                    } else {
                        assertThat(restored).isEqualTo(original)
                    }
                }
            } else {
                assertThat(restoredFromJson).isEqualTo(typedArray)
            }

            // Binary roundtrip
            val bytes = typedSerializer.toBytes(typedArray)
            val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray())

            if (typeName == "float32" || typeName == "float64") {
                assertThat(restoredFromBytes.size).isEqualTo(typedArray.size)
                typedArray.zip(restoredFromBytes).forEach { (original, restored) ->
                    if (original is Float && original.isNaN()) {
                        assertThat((restored as Float).isNaN()).isTrue()
                    } else if (original is Double && original.isNaN()) {
                        assertThat((restored as Double).isNaN()).isTrue()
                    } else {
                        assertThat(restored).isEqualTo(original)
                    }
                }
            } else {
                assertThat(restoredFromBytes).isEqualTo(typedArray)
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
        assertThat(json.startsWith("[[") || json.startsWith("[ [")).isTrue()
        val restoredFromJson = nestedIntArraySerializer.fromJsonCode(json)
        assertThat(restoredFromJson).isEqualTo(nestedArray)

        // Binary roundtrip
        val bytes = nestedIntArraySerializer.toBytes(nestedArray)
        assertThat(bytes.hex().startsWith("736f6961")).isTrue()
        val restoredFromBytes = nestedIntArraySerializer.fromBytes(bytes.toByteArray())
        assertThat(restoredFromBytes).isEqualTo(nestedArray)

        // Test deeply nested arrays
        val deeplyNestedArraySerializer = Serializers.list(nestedIntArraySerializer)
        val deeplyNestedArray =
            listOf(
                listOf(listOf(1, 2), listOf(3, 4)),
                listOf(listOf(5), emptyList()),
            )

        val deepJson = deeplyNestedArraySerializer.toJsonCode(deeplyNestedArray)
        val restoredDeepFromJson = deeplyNestedArraySerializer.fromJsonCode(deepJson)
        assertThat(restoredDeepFromJson).isEqualTo(deeplyNestedArray)

        val deepBytes = deeplyNestedArraySerializer.toBytes(deeplyNestedArray)
        val restoredDeepFromBytes = deeplyNestedArraySerializer.fromBytes(deepBytes.toByteArray())
        assertThat(restoredDeepFromBytes).isEqualTo(deeplyNestedArray)
    }

    @Test
    fun `test list serializer with optional elements`() {
        val optionalIntSerializer = Serializers.optional(Serializers.int32)
        val optionalIntArraySerializer = Serializers.list(optionalIntSerializer)

        // Test list with optional elements (some null, some not)
        val mixedArray = listOf(1, null, 42, null, 0)

        // JSON tests
        val json = optionalIntArraySerializer.toJsonCode(mixedArray)
        assertThat(json.contains("null")).isTrue()
        val restoredFromJson = optionalIntArraySerializer.fromJsonCode(json)
        assertThat(restoredFromJson).isEqualTo(mixedArray)

        // Binary tests
        val bytes = optionalIntArraySerializer.toBytes(mixedArray)
        val restoredFromBytes = optionalIntArraySerializer.fromBytes(bytes.toByteArray())
        assertThat(restoredFromBytes).isEqualTo(mixedArray)

        // Test all null list
        val allNullArray = listOf<Int?>(null, null, null)
        val allNullJson = optionalIntArraySerializer.toJsonCode(allNullArray)
        assertThat(allNullJson).isEqualTo("[null,null,null]")
        assertThat(optionalIntArraySerializer.fromJsonCode(allNullJson)).isEqualTo(allNullArray)

        val allNullBytes = optionalIntArraySerializer.toBytes(allNullArray)
        assertThat(optionalIntArraySerializer.fromBytes(allNullBytes.toByteArray())).isEqualTo(allNullArray)

        // Test no null list
        val noNullArray = listOf<Int?>(1, 2, 3)
        val noNullJson = optionalIntArraySerializer.toJsonCode(noNullArray)
        assertThat(noNullJson).isEqualTo("[1,2,3]")
        assertThat(optionalIntArraySerializer.fromJsonCode(noNullJson)).isEqualTo(noNullArray)

        val noNullBytes = optionalIntArraySerializer.toBytes(noNullArray)
        assertThat(optionalIntArraySerializer.fromBytes(noNullBytes.toByteArray())).isEqualTo(noNullArray)
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
        assertThat(intArraySerializer.fromJsonCode(intJson)).isEqualTo(edgeCaseIntArray)
        val intBytes = intArraySerializer.toBytes(edgeCaseIntArray)
        assertThat(intArraySerializer.fromBytes(intBytes.toByteArray())).isEqualTo(edgeCaseIntArray)

        val stringJson = stringArraySerializer.toJsonCode(edgeCaseStringArray)
        assertThat(stringArraySerializer.fromJsonCode(stringJson)).isEqualTo(edgeCaseStringArray)
        val stringBytes = stringArraySerializer.toBytes(edgeCaseStringArray)
        assertThat(stringArraySerializer.fromBytes(stringBytes.toByteArray())).isEqualTo(edgeCaseStringArray)

        // Test very large list (boundary testing)
        val boundaryArray = (1..100).toList()
        val boundaryJson = intArraySerializer.toJsonCode(boundaryArray)
        assertThat(intArraySerializer.fromJsonCode(boundaryJson)).isEqualTo(boundaryArray)
        val boundaryBytes = intArraySerializer.toBytes(boundaryArray)
        assertThat(intArraySerializer.fromBytes(boundaryBytes.toByteArray())).isEqualTo(boundaryArray)

        // Test single element arrays with special values
        val singleZeroArray = listOf(0)
        val singleZeroJson = intArraySerializer.toJsonCode(singleZeroArray)
        assertThat(singleZeroJson).isEqualTo("[0]")
        assertThat(intArraySerializer.fromJsonCode(singleZeroJson)).isEqualTo(singleZeroArray)

        val singleEmptyStringArray = listOf("")
        val singleEmptyStringJson = stringArraySerializer.toJsonCode(singleEmptyStringArray)
        assertThat(singleEmptyStringJson).isEqualTo("[\"\"]")
        assertThat(stringArraySerializer.fromJsonCode(singleEmptyStringJson)).isEqualTo(singleEmptyStringArray)
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
                assertThat(bytes.hex()).isEqualTo(expectedHexPrefix)
            } else {
                assertThat(bytes.hex().startsWith("736f6961fa")).isTrue()
            }
            assertThat(intArraySerializer.fromBytes(bytes.toByteArray())).isEqualTo(list)
        }

        // Test large list format
        val largeArray = (1..10).toList()
        val largeBytes = intArraySerializer.toBytes(largeArray)
        assertThat(largeBytes.hex().startsWith("736f6961fa0a")).isTrue()
        assertThat(intArraySerializer.fromBytes(largeBytes.toByteArray())).isEqualTo(largeArray)
    }

    @Test
    fun `test list serializer performance with large arrays`() {
        val intArraySerializer = Serializers.list(Serializers.int32)

        // Test with reasonably large arrays to ensure no stack overflow or performance issues
        val mediumArray = (1..1000).toList()
        val largeArray = (1..10000).toList()

        // JSON roundtrip
        val mediumJson = intArraySerializer.toJsonCode(mediumArray)
        assertThat(mediumJson.length > 1000).isTrue()
        assertThat(intArraySerializer.fromJsonCode(mediumJson)).isEqualTo(mediumArray)

        val largeJson = intArraySerializer.toJsonCode(largeArray)
        assertThat(largeJson.length > 10000).isTrue()
        assertThat(intArraySerializer.fromJsonCode(largeJson)).isEqualTo(largeArray)

        // Binary roundtrip
        val mediumBytes = intArraySerializer.toBytes(mediumArray)
        assertThat(mediumBytes.size > 1000).isTrue()
        assertThat(intArraySerializer.fromBytes(mediumBytes.toByteArray())).isEqualTo(mediumArray)

        val largeBytes = intArraySerializer.toBytes(largeArray)
        assertThat(largeBytes.size > 10000).isTrue()
        assertThat(intArraySerializer.fromBytes(largeBytes.toByteArray())).isEqualTo(largeArray)
    }

    @Test
    fun `test toStringImpl - int32`() {
        assertThat(toStringImpl(-2, Serializers.int32.impl)).isEqualTo("-2")
        assertThat(toStringImpl(300, Serializers.int32.impl)).isEqualTo("300")
    }

    @Test
    fun `test toStringImpl - int64`() {
        assertThat(toStringImpl(-2L, Serializers.int64.impl)).isEqualTo("-2L")
    }

    @Test
    fun `test toStringImpl - uint64`() {
        assertThat(toStringImpl(2UL, Serializers.uint64.impl)).isEqualTo("2UL")
    }

    @Test
    fun `test toStringImpl - float32`() {
        assertThat(toStringImpl(3.14F, Serializers.float32.impl)).isEqualTo("3.14F")
        assertThat(toStringImpl(Float.NaN, Serializers.float32.impl)).isEqualTo("Float.NaN")
        assertThat(toStringImpl(Float.POSITIVE_INFINITY, Serializers.float32.impl)).isEqualTo("Float.POSITIVE_INFINITY")
        assertThat(toStringImpl(Float.NEGATIVE_INFINITY, Serializers.float32.impl)).isEqualTo("Float.NEGATIVE_INFINITY")
    }

    @Test
    fun `test toStringImpl - float64`() {
        assertThat(toStringImpl(3.14, Serializers.float64.impl)).isEqualTo("3.14")
        assertThat(toStringImpl(Double.NaN, Serializers.float64.impl)).isEqualTo("Double.NaN")
        assertThat(toStringImpl(Double.POSITIVE_INFINITY, Serializers.float64.impl)).isEqualTo("Double.POSITIVE_INFINITY")
        assertThat(toStringImpl(Double.NEGATIVE_INFINITY, Serializers.float64.impl)).isEqualTo("Double.NEGATIVE_INFINITY")
    }

    @Test
    fun `test toStringImpl - bool`() {
        assertThat(toStringImpl(false, Serializers.bool.impl)).isEqualTo("false")
        assertThat(toStringImpl(true, Serializers.bool.impl)).isEqualTo("true")
    }

    @Test
    fun `test toStringImpl - timestamp`() {
        assertThat(
            toStringImpl(Instant.ofEpochMilli(123456789L), Serializers.timestamp.impl),
        ).isEqualTo(
            "Instant.ofEpochMillis(\n" +
                "  // 1970-01-02T10:17:36.789Z\n" +
                "  123456789L\n" +
                ")",
        )
    }

    @Test
    fun `test toStringImpl - string`() {
        assertThat(toStringImpl("", Serializers.string.impl)).isEqualTo("\"\"")
        assertThat(toStringImpl("foo", Serializers.string.impl)).isEqualTo("\"foo\"")
        assertThat(toStringImpl("foo\n", Serializers.string.impl)).isEqualTo("\"foo\\n\"")
        assertThat(toStringImpl("foo\nbar", Serializers.string.impl)).isEqualTo("\"foo\\n\" +\n  \"bar\"")
        assertThat(toStringImpl("\t\r\"\$", Serializers.string.impl)).isEqualTo("\"\\t\\r\\\"\\\$\"")
        assertThat(toStringImpl("\u0001", Serializers.string.impl)).isEqualTo("\"\\u0001\"")
    }

    @Test
    fun `test toStringImpl - bytes`() {
        assertThat(toStringImpl(okio.ByteString.EMPTY, Serializers.bytes.impl)).isEqualTo("\"\".decodeHex()")
        assertThat(toStringImpl("abcd".decodeHex(), Serializers.bytes.impl)).isEqualTo("\"abcd\".decodeHex()")
    }

    @Test
    fun `test toStringImpl - optional`() {
        assertThat(toStringImpl(null, Serializers.optional(Serializers.bool).impl)).isEqualTo("null")
        assertThat(toStringImpl(true, Serializers.optional(Serializers.bool).impl)).isEqualTo("true")
    }
}
