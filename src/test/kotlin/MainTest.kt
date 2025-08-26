package soia

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

data class Foo(val bar: Bar)

data class Bar(val baz: Int)

class CalculatorTest {
    // A simple function being tested
    fun add(
        a: Int,
        b: Int,
    ): Int {
        return a + b
    }

    val foo: String get() = "foo"

    @Test
    fun `test addition of two numbers`() {
        // Arrange
        val calculator = CalculatorTest()

        // Act
        val result = calculator.add(2, 3)

        val f1: (Foo) -> Int = { it.bar.baz }
        val f2: (Foo) -> Int = { it.bar.baz }

        // Assert
//        assertEquals(5, result, "The addition of 2 and 3 should be 5")
//        assertEquals(f1, f2);

        val foo = listOf(1, 2, 3)
        assertEquals(foo is MutableList<*>, true)
    }
}
