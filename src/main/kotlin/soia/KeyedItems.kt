package soia

interface KeyedItems<T, K> : List<T> {
    val indexing: Map<K, T>
}

fun <T, K> toKeyedItems(
    elements: Iterable<T>,
    getKey: (T) -> K,
): KeyedItems<T, K> {
    return soia.internal.toKeyedItems(elements, "", getKey) { it }
}
