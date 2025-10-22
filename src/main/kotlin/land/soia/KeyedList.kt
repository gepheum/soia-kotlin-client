package land.soia

/**
 * An immutable list which exposes a [mapView] for fast lookups by key. A key is
 * extracted from each element using the function passed to [toKeyedList]. Each
 * element in the list is assumed to "contain" its own key.
 *
 * @param T The type of elements in the list; contains the key
 * @param K The type of keys used for indexing
 */
interface KeyedList<T, K> : List<T> {
    /**
     * A map that provides fast key-based access to elements in the list.
     *
     * The indexing, which runs in O(N), only happens once.
     */
    val mapView: Map<K, T>
}

/**
 * Creates an immutable [KeyedList] from the given elements, using the provided function
 * to extract keys.
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
