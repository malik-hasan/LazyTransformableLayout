package mobile.oats.lazytransformablelayout.tiledimage.internal

internal class LruCache<K : Any, V : Any>(
    private val maxSize: Long,
    private val sizeOf: (V) -> Long
) {
    private val entries = LinkedHashMap<K, V>()
    private var size = 0L

    operator fun get(key: K): V? = entries.remove(key)?.also { entries[key] = it }

    operator fun set(key: K, value: V) {
        entries.remove(key)?.let { size -= sizeOf(it) }
        entries[key] = value
        size += sizeOf(value)

        while (size > maxSize) {
            val eldest = entries.keys.first()
            size -= sizeOf(entries.remove(eldest)!!)
        }
    }
}
