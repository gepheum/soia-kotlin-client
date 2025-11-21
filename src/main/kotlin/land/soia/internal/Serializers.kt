package land.soia.internal

import land.soia.Serializer

fun <T> makeSerializer(impl: SerializerImpl<T>): Serializer<T> {
    return Serializer(impl)
}
