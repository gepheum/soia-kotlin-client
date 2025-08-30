package soia

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import soia.internal.StructSerializer
import soia.internal.UnrecognizedFields

class StructSerializerTest {
    // Test data structures
    data class PersonFrozen(
        val name: String = "",
        val age: Int = 0,
        val email: String? = null,
        val isActive: Boolean = false,
        val tags: List<String> = emptyList(),
        val unrecognizedFields: UnrecognizedFields<PersonFrozen>? = null,
    )

    data class PersonMutable(
        var name: String = "",
        var age: Int = 0,
        var email: String? = null,
        var isActive: Boolean = false,
        var tags: List<String> = emptyList(),
        var unrecognizedFields: UnrecognizedFields<PersonFrozen>? = null,
    )

    private val defaultPerson = PersonFrozen()

    private val personStructSerializer =
        StructSerializer<PersonFrozen, PersonMutable>(
            defaultInstance = defaultPerson,
            newMutable = { PersonMutable() },
            toFrozen = { mutable ->
                PersonFrozen(
                    name = mutable.name,
                    age = mutable.age,
                    email = mutable.email,
                    isActive = mutable.isActive,
                    tags = mutable.tags,
                    unrecognizedFields = mutable.unrecognizedFields,
                )
            },
            getUnrecognizedFields = { frozen -> frozen.unrecognizedFields },
            setUnrecognizedFields = { mutable, fields -> mutable.unrecognizedFields = fields },
        ).apply {
            // Add fields in order by field number
            addField(
                StructSerializer.Field(
                    name = "name",
                    number = 0,
                    serializer = Serializers.string,
                    getter = { it.name },
                    setter = { mutable, value -> mutable.name = value },
                ),
            )
            addField(
                StructSerializer.Field(
                    name = "age",
                    number = 1,
                    serializer = Serializers.int32,
                    getter = { it.age },
                    setter = { mutable, value -> mutable.age = value },
                ),
            )
            addField(
                StructSerializer.Field(
                    name = "email",
                    number = 2,
                    serializer = Serializers.optional(Serializers.string),
                    getter = { it.email },
                    setter = { mutable, value -> mutable.email = value },
                ),
            )
            addField(
                StructSerializer.Field(
                    name = "isActive",
                    number = 3,
                    serializer = Serializers.bool,
                    getter = { it.isActive },
                    setter = { mutable, value -> mutable.isActive = value },
                ),
            )
            addField(
                StructSerializer.Field(
                    name = "tags",
                    number = 4,
                    serializer = Serializers.list(Serializers.string),
                    getter = { it.tags },
                    setter = { mutable, value -> mutable.tags = value },
                ),
            )
            finalizeStruct()
        }

    private val personSerializer = Serializer(personStructSerializer)

    @Test
    fun `test struct serializer - default instance`() {
        // Test isDefault
        assertTrue(personStructSerializer.isDefault(defaultPerson), "Default instance should be detected as default")

        val nonDefaultPerson = PersonFrozen(name = "John")
        assertFalse(personStructSerializer.isDefault(nonDefaultPerson), "Non-default instance should not be detected as default")

        // Test JSON serialization - default should be empty array/object
        val defaultDenseJson = personStructSerializer.toJson(defaultPerson, readableFlavor = false)
        assertTrue(defaultDenseJson is JsonArray && defaultDenseJson.isEmpty(), "Default dense JSON should be empty array")

        val defaultReadableJson = personStructSerializer.toJson(defaultPerson, readableFlavor = true)
        assertTrue(defaultReadableJson is JsonObject && defaultReadableJson.isEmpty(), "Default readable JSON should be empty object")

        // Test JSON deserialization
        assertEquals(defaultPerson, personStructSerializer.fromJson(JsonPrimitive(0), keepUnrecognizedFields = false))
        assertEquals(defaultPerson, personStructSerializer.fromJson(JsonArray(emptyList()), keepUnrecognizedFields = false))
        assertEquals(defaultPerson, personStructSerializer.fromJson(JsonObject(emptyMap()), keepUnrecognizedFields = false))
    }

    @Test
    fun `test struct serializer - dense JSON format`() {
        val person =
            PersonFrozen(
                name = "Alice",
                age = 30,
                email = "alice@example.com",
                isActive = true,
                tags = listOf("developer", "kotlin"),
            )

        // Test dense JSON - should be an array
        val denseJson = personStructSerializer.toJson(person, readableFlavor = false)
        assertTrue(denseJson is JsonArray, "Dense JSON should be an array")

        val jsonArray = denseJson as JsonArray
        assertEquals("Alice", (jsonArray[0] as JsonPrimitive).content.removeSurrounding("\""))
        assertEquals("30", (jsonArray[1] as JsonPrimitive).content)
        assertEquals("alice@example.com", (jsonArray[2] as JsonPrimitive).content.removeSurrounding("\""))
        assertEquals("1", (jsonArray[3] as JsonPrimitive).content) // bool dense format
        assertTrue(jsonArray[4] is JsonArray, "Tags should be an array") // tags array

        // Test roundtrip
        val restored = personStructSerializer.fromJson(denseJson, keepUnrecognizedFields = false)
        assertEquals(person.name, restored.name)
        assertEquals(person.age, restored.age)
        assertEquals(person.email, restored.email)
        assertEquals(person.isActive, restored.isActive)
        assertEquals(person.tags, restored.tags)
    }

    @Test
    fun `test struct serializer - readable JSON format`() {
        val person =
            PersonFrozen(
                name = "Bob",
                age = 25,
                email = null,
                isActive = true,
                tags = listOf("tester"),
            )

        // Test readable JSON - should be an object with only non-default values
        val readableJson = personStructSerializer.toJson(person, readableFlavor = true)
        assertTrue(readableJson is JsonObject, "Readable JSON should be an object")

        val jsonObject = readableJson as JsonObject
        assertTrue(jsonObject.contains("name"), "Should contain name field")
        assertTrue(jsonObject.contains("age"), "Should contain age field")
        assertFalse(jsonObject.contains("email"), "Should not contain null/default email field") // null/default value should be omitted
        assertTrue(jsonObject.contains("isActive"), "Should contain isActive field")
        assertTrue(jsonObject.contains("tags"), "Should contain tags field")

        // Test roundtrip
        val restored = personStructSerializer.fromJson(readableJson, keepUnrecognizedFields = false)
        assertEquals(person.name, restored.name)
        assertEquals(person.age, restored.age)
        assertEquals(person.email, restored.email)
        assertEquals(person.isActive, restored.isActive)
        assertEquals(person.tags, restored.tags)
    }

    @Test
    fun `test struct serializer - binary format small structs`() {
        // Test binary encoding for different struct sizes (0-3 fields)
        val emptyStruct = defaultPerson
        val oneFieldStruct = PersonFrozen(name = "Dave")
        val twoFieldStruct = PersonFrozen(name = "Eve", age = 28)
        val threeFieldStruct = PersonFrozen(name = "Frank", age = 30, email = "frank@example.com")

        // Empty struct should encode to wire 246 (0 fields)
        val emptyBytes = personSerializer.toBytes(emptyStruct)
        assertTrue(emptyBytes.hex().startsWith("736f6961f6"), "Empty struct should start with soia prefix + 246") // soia prefix + 246

        // One field should encode to wire 247 (1 field)
        val oneFieldBytes = personSerializer.toBytes(oneFieldStruct)
        assertTrue(
            oneFieldBytes.hex().startsWith("736f6961f7"),
            "One field struct should start with soia prefix + 247",
        ) // soia prefix + 247

        // Two fields should encode to wire 248 (2 fields)
        val twoFieldBytes = personSerializer.toBytes(twoFieldStruct)
        assertTrue(
            twoFieldBytes.hex().startsWith("736f6961f8"),
            "Two field struct should start with soia prefix + 248",
        ) // soia prefix + 248

        // Three fields should encode to wire 249 (3 fields)
        val threeFieldBytes = personSerializer.toBytes(threeFieldStruct)
        assertTrue(
            threeFieldBytes.hex().startsWith("736f6961f9"),
            "Three field struct should start with soia prefix + 249",
        ) // soia prefix + 249

        // Test roundtrips
        assertEquals(emptyStruct, personSerializer.fromBytes(emptyBytes.toByteArray()))
        assertEquals(oneFieldStruct, personSerializer.fromBytes(oneFieldBytes.toByteArray()))
        assertEquals(twoFieldStruct, personSerializer.fromBytes(twoFieldBytes.toByteArray()))
        assertEquals(threeFieldStruct, personSerializer.fromBytes(threeFieldBytes.toByteArray()))
    }

    @Test
    fun `test struct serializer - binary format large structs`() {
        // Test struct with more than 3 fields (should use wire 250 + length prefix)
        val fullStruct =
            PersonFrozen(
                name = "Grace",
                age = 45,
                email = "grace@example.com",
                isActive = true,
                tags = listOf("manager", "leader"),
            )

        val bytes = personSerializer.toBytes(fullStruct)
        assertTrue(bytes.hex().startsWith("736f6961fa"), "Large struct should start with soia prefix + 250") // soia prefix + 250

        val restored = personSerializer.fromBytes(bytes.toByteArray())
        assertEquals(fullStruct.name, restored.name)
        assertEquals(fullStruct.age, restored.age)
        assertEquals(fullStruct.email, restored.email)
        assertEquals(fullStruct.isActive, restored.isActive)
        assertEquals(fullStruct.tags, restored.tags)
    }

    @Test
    fun `test struct serializer - all field types roundtrip`() {
        val testPerson =
            PersonFrozen(
                name = "John Doe",
                age = 42,
                email = "john.doe@example.com",
                isActive = true,
                tags = listOf("senior", "architect", "kotlin"),
            )

        // Test JSON roundtrip with both flavors
        val denseJson = personSerializer.toJsonCode(testPerson, readableFlavor = false)
        val readableJson = personSerializer.toJsonCode(testPerson, readableFlavor = true)

        val restoredFromDense = personSerializer.fromJsonCode(denseJson)
        val restoredFromReadable = personSerializer.fromJsonCode(readableJson)

        // Both should restore to the same object
        assertEquals(testPerson.name, restoredFromDense.name)
        assertEquals(testPerson.age, restoredFromDense.age)
        assertEquals(testPerson.email, restoredFromDense.email)
        assertEquals(testPerson.isActive, restoredFromDense.isActive)
        assertEquals(testPerson.tags, restoredFromDense.tags)

        assertEquals(testPerson.name, restoredFromReadable.name)
        assertEquals(testPerson.age, restoredFromReadable.age)
        assertEquals(testPerson.email, restoredFromReadable.email)
        assertEquals(testPerson.isActive, restoredFromReadable.isActive)
        assertEquals(testPerson.tags, restoredFromReadable.tags)

        // Test binary roundtrip
        val bytes = personSerializer.toBytes(testPerson)
        val restoredFromBinary = personSerializer.fromBytes(bytes.toByteArray())

        assertEquals(testPerson.name, restoredFromBinary.name)
        assertEquals(testPerson.age, restoredFromBinary.age)
        assertEquals(testPerson.email, restoredFromBinary.email)
        assertEquals(testPerson.isActive, restoredFromBinary.isActive)
        assertEquals(testPerson.tags, restoredFromBinary.tags)
    }

    @Test
    fun `test struct serializer - error cases`() {
        // Test that finalizeStruct() can only be called once
        val testSerializer =
            StructSerializer<PersonFrozen, PersonMutable>(
                defaultInstance = defaultPerson,
                newMutable = { PersonMutable() },
                toFrozen = { PersonFrozen() },
                getUnrecognizedFields = { null },
                setUnrecognizedFields = { _, _ -> },
            )

        testSerializer.addField(
            StructSerializer.Field("name", 0, Serializers.string, { it.name }, { m, v -> m.name = v }),
        )

        testSerializer.finalizeStruct()

        // Adding fields after finalization should throw
        var exceptionThrown = false
        try {
            testSerializer.addField(
                StructSerializer.Field("age", 1, Serializers.int32, { it.age }, { m, v -> m.age = v }),
            )
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown, "Should throw exception when adding fields after finalization")

        // Adding removed numbers after finalization should throw
        exceptionThrown = false
        try {
            testSerializer.addRemovedNumber(5)
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown, "Should throw exception when adding removed numbers after finalization")

        // Double finalization should throw
        exceptionThrown = false
        try {
            testSerializer.finalizeStruct()
        } catch (e: IllegalStateException) {
            exceptionThrown = true
        }
        assertTrue(exceptionThrown, "Should throw exception when finalizing twice")
    }

    @Test
    fun `test struct serializer - removed field numbers`() {
        // Test struct with removed field numbers
        data class SimpleStruct(val name: String = "", val value: Int = 0)

        data class SimpleMutable(var name: String = "", var value: Int = 0)
        val defaultSimple = SimpleStruct()

        val serializerWithRemovedFields =
            StructSerializer<SimpleStruct, SimpleMutable>(
                defaultInstance = defaultSimple,
                newMutable = { SimpleMutable() },
                toFrozen = { mutable -> SimpleStruct(mutable.name, mutable.value) },
                getUnrecognizedFields = { null },
                setUnrecognizedFields = { _, _ -> },
            ).apply {
                addField(StructSerializer.Field("name", 0, Serializers.string, { it.name }, { m, v -> m.name = v }))
                addRemovedNumber(1) // Field number 1 was removed
                addField(StructSerializer.Field("value", 2, Serializers.int32, { it.value }, { m, v -> m.value = v }))
                finalizeStruct()
            }

        val testStruct = SimpleStruct("test", 42)
        val serializer = Serializer(serializerWithRemovedFields)

        // Test JSON roundtrips - should still work normally
        val denseJson = serializer.toJsonCode(testStruct, readableFlavor = false)
        val readableJson = serializer.toJsonCode(testStruct, readableFlavor = true)

        val restoredFromDense = serializer.fromJsonCode(denseJson)
        val restoredFromReadable = serializer.fromJsonCode(readableJson)

        assertEquals(testStruct.name, restoredFromDense.name)
        assertEquals(testStruct.value, restoredFromDense.value)
        assertEquals(testStruct.name, restoredFromReadable.name)
        assertEquals(testStruct.value, restoredFromReadable.value)

        // Test binary roundtrip - should handle removed field properly
        val bytes = serializer.toBytes(testStruct)
        val restoredFromBinary = serializer.fromBytes(bytes.toByteArray())

        assertEquals(testStruct.name, restoredFromBinary.name)
        assertEquals(testStruct.value, restoredFromBinary.value)
    }

    @Test
    fun `test struct serializer - default detection`() {
        // Test isDefault with various field configurations

        // All default values
        val allDefaults = PersonFrozen()
        assertTrue(personStructSerializer.isDefault(allDefaults), "All defaults should be detected as default")

        // One non-default value
        val oneNonDefault = PersonFrozen(name = "test")
        assertFalse(personStructSerializer.isDefault(oneNonDefault), "One non-default value should not be detected as default")

        // Edge case: empty string vs default string
        val emptyString = PersonFrozen(name = "")
        assertTrue(personStructSerializer.isDefault(emptyString), "Empty string should be detected as default") // empty string is default

        // Edge case: empty list vs default list
        val emptyList = PersonFrozen(tags = emptyList())
        assertTrue(personStructSerializer.isDefault(emptyList), "Empty list should be detected as default") // empty list is default

        // Edge case: null vs non-null optional
        val nullOptional = PersonFrozen(email = null)
        assertTrue(
            personStructSerializer.isDefault(nullOptional),
            "Null optional should be detected as default",
        ) // null is default for optional

        val nonNullOptional = PersonFrozen(email = "")
        assertFalse(
            personStructSerializer.isDefault(nonNullOptional),
            "Non-null optional should not be detected as default",
        ) // even empty string is non-default for optional

        // Complex case: multiple defaults with one non-default
        val mixedDefaults = PersonFrozen(name = "", age = 0, email = null, isActive = false, tags = listOf("test"))
        assertFalse(
            personStructSerializer.isDefault(mixedDefaults),
            "Mixed defaults with one non-default should not be detected as default",
        ) // tags makes it non-default
    }
}
