package soia.internal.soia

import kotlinx.serialization.json.JsonPrimitive
import okio.ByteString
import okio.ByteString.Companion.encodeUtf8
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import soia.JsonFlavor
import soia.Serializer
import java.time.Instant

class SerializersTest {
    @Test
    fun `test bool serializer - basic functionality`() {
        // Test true value - should be 1 in dense, true in readable
        val trueJson = Serializers.bool.toJson(true, JsonFlavor.DENSE)
        assertTrue(trueJson is JsonPrimitive)
        assertEquals(1, (trueJson as JsonPrimitive).int)

        val trueReadableJson = Serializers.bool.toJson(true, JsonFlavor.READABLE)
        assertEquals(true, (trueReadableJson as JsonPrimitive).boolean)

        // Test false value - should be 0 in dense, false in readable
        val falseJson = Serializers.bool.toJson(false, JsonFlavor.DENSE)
        assertEquals(0, (falseJson as JsonPrimitive).int)

        val falseReadableJson = Serializers.bool.toJson(false, JsonFlavor.READABLE)
        assertEquals(false, (falseReadableJson as JsonPrimitive).boolean)

        // Test round-trip
        assertEquals(true, Serializers.bool.fromJson(trueJson))
        assertEquals(true, Serializers.bool.fromJson(trueReadableJson))
        assertEquals(false, Serializers.bool.fromJson(falseJson))
        assertEquals(false, Serializers.bool.fromJson(falseReadableJson))
    }

    @Test
    fun `test bool serializer - binary serialization`() {
        // From TypeScript tests: true -> "01", false -> "00"
        val trueBytes = Serializers.bool.toBytes(true)
        assertEquals("01", trueBytes.hex())

        val falseBytes = Serializers.bool.toBytes(false)
        assertEquals("00", falseBytes.hex())

        // Test roundtrip
        val restoredTrue = Serializers.bool.fromBytes(trueBytes.toByteArray().inputStream())
        assertEquals(true, restoredTrue)

        val restoredFalse = Serializers.bool.fromBytes(falseBytes.toByteArray().inputStream())
        assertEquals(false, restoredFalse)
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
            val restoredFromBytes = Serializers.int32.fromBytes(bytes.toByteArray().inputStream())
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
            val restoredFromBytes = Serializers.int64.fromBytes(bytes.toByteArray().inputStream())
            assertEquals(value, restoredFromBytes, "Binary failed for value: $value")
        }
    }

    @Test
    fun `test uint64 serializer`() {
        val values = listOf(0L, 1L, 42L, Long.MAX_VALUE)

        for (value in values) {
            val jsonCode = Serializers.uint64.toJsonCode(value)
            val restored = Serializers.uint64.fromJsonCode(jsonCode)
            assertEquals(value, restored, "Failed for value: $value")

            val bytes = Serializers.uint64.toBytes(value)
            val restoredFromBytes = Serializers.uint64.fromBytes(bytes.toByteArray().inputStream())
            assertEquals(value, restoredFromBytes, "Binary failed for value: $value")
        }
    }

    @Test
    fun `test float32 serializer`() {
        val values =
            listOf(0.0f, 1.0f, -1.0f, 3.14f, Float.MAX_VALUE, Float.MIN_VALUE, Float.NaN, Float.POSITIVE_INFINITY, Float.NEGATIVE_INFINITY)

        for (value in values) {
            val jsonCode = Serializers.float32.toJsonCode(value)
            val restored = Serializers.float32.fromJsonCode(jsonCode)

            if (value.isNaN()) {
                assertTrue(restored.isNaN(), "NaN not preserved for value: $value")
            } else {
                assertEquals(value, restored, "Failed for value: $value")
            }

            val bytes = Serializers.float32.toBytes(value)
            val restoredFromBytes = Serializers.float32.fromBytes(bytes.toByteArray().inputStream())

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
            val restoredFromBytes = Serializers.float64.fromBytes(bytes.toByteArray().inputStream())

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
            val restoredFromBytes = Serializers.string.fromBytes(bytes.toByteArray().inputStream())
            assertEquals(value, restoredFromBytes, "Binary failed for value: '$value'")

            // JsonFlavor shouldn't affect plain strings
            assertEquals(
                Serializers.string.toJsonCode(value, JsonFlavor.DENSE),
                Serializers.string.toJsonCode(value, JsonFlavor.READABLE),
            )
        }
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
            val restoredFromBytes = Serializers.bytes.fromBytes(bytes.toByteArray().inputStream())
            assertEquals(value, restoredFromBytes, "Binary failed for value: $value")
        }
    }

    @Test
    fun `test bytes serializer - readable flavor`() {
        val value = "hello".encodeUtf8()
        val readableJson = Serializers.bytes.toJsonCode(value, JsonFlavor.READABLE)

        // Readable should produce an object with base64 and size
        assertTrue(readableJson.contains("\"base64\""), "Readable format should contain base64 field")
        assertTrue(readableJson.contains("\"size\""), "Readable format should contain size field")
        assertTrue(readableJson.contains("5"), "Size should be 5 for 'hello'")

        val restoredFromReadable = Serializers.bytes.fromJsonCode(readableJson)
        assertEquals(value, restoredFromReadable, "Readable failed for value: $value")
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
            val restoredFromBytes = Serializers.timestamp.fromBytes(bytes.toByteArray().inputStream())
            assertEquals(timestamp, restoredFromBytes, "Binary failed for timestamp: $timestamp")
        }
    }

    @Test
    fun `test timestamp serializer - readable flavor`() {
        val timestamp = Instant.parse("2025-08-25T10:30:45Z")
        val readableJson = Serializers.timestamp.toJsonCode(timestamp, JsonFlavor.READABLE)

        // Readable should produce an object with unix_millis and formatted
        assertTrue(readableJson.contains("\"unix_millis\""), "Readable format should contain unix_millis field")
        assertTrue(readableJson.contains("\"formatted\""), "Readable format should contain formatted field")
        assertTrue(readableJson.contains("2025-08-25T10:30:45Z"), "Should contain the formatted timestamp")

        val restoredFromReadable = Serializers.timestamp.fromJsonCode(readableJson)
        assertEquals(timestamp, restoredFromReadable, "Readable failed for timestamp: $timestamp")
    }

    @Test
    fun `test all serializers - json flavor consistency`() {
        // For booleans, DENSE and READABLE should be different
        assertNotEquals(
            Serializers.bool.toJsonCode(true, JsonFlavor.DENSE),
            Serializers.bool.toJsonCode(true, JsonFlavor.READABLE),
        )

        assertEquals(
            Serializers.int32.toJsonCode(42, JsonFlavor.DENSE),
            Serializers.int32.toJsonCode(42, JsonFlavor.READABLE),
        )

        assertEquals(
            Serializers.string.toJsonCode("test", JsonFlavor.DENSE),
            Serializers.string.toJsonCode("test", JsonFlavor.READABLE),
        )

        // But bytes and timestamp should be different
        val bytes = "test".encodeUtf8()
        val timestamp = Instant.now()

        assertNotEquals(
            Serializers.bytes.toJsonCode(bytes, JsonFlavor.DENSE),
            Serializers.bytes.toJsonCode(bytes, JsonFlavor.READABLE),
        )

        assertNotEquals(
            Serializers.timestamp.toJsonCode(timestamp, JsonFlavor.DENSE),
            Serializers.timestamp.toJsonCode(timestamp, JsonFlavor.READABLE),
        )
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

        // Test epoch timestamp
        assertEquals(Instant.EPOCH, Serializers.timestamp.fromJsonCode("\"1970-01-01T00:00:00Z\""))
    }

    @Test
    fun `test serialization roundtrip for all types`() {
        // Test that all serializers can handle roundtrip JSON serialization
        val testData =
            mapOf(
                Serializers.bool to true,
                Serializers.int32 to -42,
                Serializers.int64 to 123456789L,
                Serializers.uint64 to 987654321L,
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
            val restoredFromBytes = typedSerializer.fromBytes(bytes.toByteArray().inputStream())
            assertEquals(value, restoredFromBytes, "Binary roundtrip failed for $value")
        }
    }
}
