package land.soia.reflection

/**
 * A function object that takes in a Soia value of any type and returns a new value of
 * the same type.
 *
 * Usage: write your own implementations of this interface, and pass it to methods such
 * as [StructDescriptor.Reflective.mapFields] to recursively transform a Soia value.
 *
 * See a complete example at
 * https://github.com/gepheum/soia-dart-example/blob/main/lib/all_strings_to_upper_case.dart
 */
interface ReflectiveTransformer {
    /** Expects a Soia value of any type, returns a value of the same type. */
    fun <T> transform(
        input: T,
        descriptor: TypeDescriptor.Reflective<T>,
    ): T

    /** Implementation of a [ReflectiveTransformer] which returns its argument unchanged. */
    object Identity : ReflectiveTransformer {
        override fun <T> transform(
            input: T,
            descriptor: TypeDescriptor.Reflective<T>,
        ): T {
            return input
        }
    }
}
