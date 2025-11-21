package land.soia.internal

import land.soia.Serializer

fun <T> makeSerializer(impl: SerializerImpl<T>): Serializer<T> {
    return Serializer(impl)
}

fun <T : Any> makeSerializer(impl: StructSerializer<T, *>): land.soia.StructSerializer<T> {
    return land.soia.StructSerializer(impl, impl)
}

fun <T : Any> makeSerializer(impl: EnumSerializer<T>): land.soia.EnumSerializer<T> {
    return land.soia.EnumSerializer(impl, impl)
}
