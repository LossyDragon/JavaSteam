package `in`.dragonbra.javasteam.depotdownloader

import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger

/**
 * A pool of byte arrays for reuse to reduce GC pressure.
 * Similar to C#'s ArrayPool<byte>.Shared functionality.
 */
object BufferPool {
    // Use ConcurrentHashMap to safely access buffers from multiple coroutines
    private val buffers = ConcurrentHashMap<Int, ConcurrentLinkedQueue<ByteArray>>()

    // Optional: Track statistics for debugging/monitoring
    private val stats = ConcurrentHashMap<Int, AtomicInteger>()

    // Max number of buffers to keep in the pool per size
    private const val MAX_BUFFERS_PER_SIZE = 32

    /**
     * Get a byte array of at least the specified size
     */
    fun rent(size: Int): ByteArray {
        // Get or create queue for this size
        val queue = buffers.getOrPut(size) { ConcurrentLinkedQueue() }

        // Try to get an existing buffer
        val buffer = queue.poll()

        // If no buffer available, create a new one
        return buffer ?: ByteArray(size).also {
            // Track allocations for debugging if needed
            stats.getOrPut(size) { AtomicInteger(0) }.incrementAndGet()
        }
    }

    /**
     * Return a byte array to the pool for future reuse
     */
    fun returnBuffer(buffer: ByteArray) {
        val size = buffer.size
        val queue = buffers.getOrPut(size) { ConcurrentLinkedQueue() }

        // Only store the buffer if we're under the limit for this size
        if (queue.size < MAX_BUFFERS_PER_SIZE) {
            // Optional: Clear the buffer for security
            buffer.fill(0)
            queue.offer(buffer)
        }
    }

    /**
     * Helper method to use a rented buffer within a block and return it afterward
     */
    inline fun <T> useBuffer(size: Int, block: (ByteArray) -> T): T {
        val buffer = rent(size)
        try {
            return block(buffer)
        } finally {
            returnBuffer(buffer)
        }
    }

    /**
     * Clear all pooled buffers
     */
    fun clear() {
        buffers.clear()
    }

    /**
     * Get statistics about the pool usage
     */
    fun getStats(): Map<Int, Int> = stats.mapValues { it.value.get() }
}
