package land.soia

/**
 * A list that provides fast lookup by key, combining the ordered access of a list
 * with the fast key-based access of a map.
 *
 * This interface extends [List] with a [mapView] property that allows O(1) lookup of
 * elements by their associated keys.
 *
 * @param T The type of elements in the list
 * @param K The type of keys used for indexing
 */
interface KeyedList<T, K> : List<T> {
    /**
     * A map that provides fast key-based access to elements in the list.
     * Each element can be looked up by its associated key in O(1) time.
     */
    val mapView: Map<K, T>
}

/**
 * Creates a [KeyedList] from the given elements, using the provided function to extract keys.
 *
 * @param elements The collection of elements to include in the keyed list
 * @param getKey A function that extracts the key from each element
 * @return A new KeyedList containing the elements with key-based indexing
 */
fun <T, K> toKeyedList(
    elements: Iterable<T>,
    getKey: (T) -> K,
): KeyedList<T, K> {
    return land.soia.internal.toKeyedList(elements, "", getKey) { it }
}
