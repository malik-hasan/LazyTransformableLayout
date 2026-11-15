package mobile.oats.lazytransformablelayout.tiledimage.internal

internal class LruCache<K : Any, V : Any>(
    private val maxSize: Long,
    private val sizeOf: (V) -> Long
) {
    private val entries = LinkedHashMap<K, V>()
    private var size = 0L

    fun peek(key: K): V? = entries[key]

    fun remove(key: K): V? = entries.remove(key)?.also { size -= sizeOf(it) }

    operator fun set(key: K, value: V) {
        remove(key)
        entries[key] = value
        size += sizeOf(value)

        while (size > maxSize) {
            remove(entries.keys.first())
        }
    }
}
