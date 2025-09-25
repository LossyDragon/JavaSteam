package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.Closeable
import java.util.concurrent.ConcurrentLinkedDeque
import java.util.concurrent.CopyOnWriteArrayList
import kotlin.concurrent.atomics.AtomicBoolean
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.jvm.Throws

/**
 *
 */
data class DownloadItem(
    val appId: Int,
    val manifestOnly: Boolean = false,
    val pubFile: Long? = null,
    val ugcId: Long? = null,
    val branch: String = "public",

)

interface DownloadListener {
    fun onItemAdded(item: DownloadItem, index: Int)
    fun onItemRemoved(item: DownloadItem, index: Int)
    fun onItemMoved(item: DownloadItem, fromIndex: Int, toIndex: Int)
    fun onQueueCleared(previousItems: List<DownloadItem>)
    fun onQueueChanged(currentItems: List<DownloadItem>)
    fun onQueueClosed()
}

@Suppress("unused")
@OptIn(ExperimentalAtomicApi::class)
class ContentDownloader @JvmOverloads constructor(
    packageTokens: Map<Int, Long>, // To be provided from [LicenseListCallback]
    debug: Boolean = false, // Enable debugging features, such as logging
    useLanCache: Boolean = false, // Try and detect a lan cache server.
    maxDownloads: Int = 8, // Max concurrent downloads
) : Closeable {

    companion object {

    }

    private var logger: Logger? = null

    private val isClosed = AtomicBoolean(false)

    private val items = ConcurrentLinkedDeque<DownloadItem>()

    private val listeners = CopyOnWriteArrayList<DownloadListener>()

    init {
        if (debug) {
            logger = LogManager.getLogger(ContentDownloader::class.java)
        }
    }

    @Throws(IllegalStateException::class)
    fun addListener(listener: DownloadListener) {
        checkNotClosed()
        listeners.add(listener)
    }

    fun removeListener(listener: DownloadListener) {
        listeners.remove(listener)
    }

    @Throws(IllegalStateException::class)
    fun getItems(): List<DownloadItem> {
        checkNotClosed()
        return ArrayList(items)
    }

    fun size(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun isClosed(): Boolean = isClosed.load()

    @Throws(IllegalStateException::class)
    fun add(item: DownloadItem) {
        checkNotClosed()

        items.addLast(item)

        notifyListeners { listener -> listener.onItemAdded(item, items.size - 1) }
    }

    @Throws(IllegalStateException::class)
    fun addFirst(item: DownloadItem) {
        checkNotClosed()

        items.addFirst(item)

        notifyListeners { listener -> listener.onItemAdded(item, 0) }
    }

    @Throws(IllegalStateException::class)
    fun addAt(index: Int, item: DownloadItem): Boolean {
        checkNotClosed()
        val itemsList = ArrayList(items)
        if (index < 0 || index > itemsList.size) {
            return false
        }

        itemsList.add(index, item)

        items.clear()
        items.addAll(itemsList)

        notifyListeners { listener -> listener.onItemAdded(item, index) }

        return true
    }

    @Throws(IllegalStateException::class)
    fun removeFirst(): DownloadItem? {
        checkNotClosed()
        val item = items.pollFirst()

        item?.let { item ->
            notifyListeners { listener -> listener.onItemRemoved(item, 0) }
        }

        return item
    }

    @Throws(IllegalStateException::class)
    fun removeLast(): DownloadItem? {
        checkNotClosed()

        val item = items.pollLast()

        item?.let { item ->
            notifyListeners { listener -> listener.onItemRemoved(item, items.size) }
        }

        return item
    }

    @Throws(IllegalStateException::class)
    fun remove(item: DownloadItem): Boolean {
        checkNotClosed()
        val itemsList = ArrayList(items)
        val index = itemsList.indexOf(item)

        if (index >= 0) {
            itemsList.removeAt(index)
            items.clear()
            items.addAll(itemsList)
            notifyListeners { listener -> listener.onItemRemoved(item, index) }
            return true
        }

        return false
    }

    @Throws(IllegalStateException::class)
    fun removeAt(index: Int): DownloadItem? {
        checkNotClosed()
        val itemsList = ArrayList(items)
        if (index < 0 || index >= itemsList.size) {
            return null
        }

        val item = itemsList.removeAt(index)
        items.clear()
        items.addAll(itemsList)
        notifyListeners { listener -> listener.onItemRemoved(item, index) }

        return item
    }

    @Throws(IllegalStateException::class)
    fun moveItem(fromIndex: Int, toIndex: Int): Boolean {
        checkNotClosed()
        val itemsList = ArrayList(items)

        if (fromIndex < 0 || fromIndex >= itemsList.size ||
            toIndex < 0 || toIndex >= itemsList.size
        ) {
            return false
        }

        val item = itemsList.removeAt(fromIndex)
        itemsList.add(toIndex, item)
        items.clear()
        items.addAll(itemsList)
        notifyListeners { listener -> listener.onItemMoved(item, fromIndex, toIndex) }

        return true
    }

    @Throws(IllegalStateException::class)
    fun clear() {
        checkNotClosed()
        val oldItems = ArrayList(items)
        items.clear()
        notifyListeners { listener -> listener.onQueueCleared(oldItems) }
    }

    @Throws(IllegalStateException::class)
    fun get(index: Int): DownloadItem? {
        checkNotClosed()
        val itemsList = ArrayList(items)
        return if (index >= 0 && index < itemsList.size) {
            itemsList[index]
        } else {
            null
        }
    }

    fun contains(item: DownloadItem): Boolean = items.contains(item)

    fun indexOf(item: DownloadItem): Int = ArrayList(items).indexOf(item)

    fun enqueue(item: DownloadItem) {
        add(item)
    }

    fun dequeue(): DownloadItem? = removeFirst()

    fun peek(): DownloadItem? = get(0)

    fun peekLast(): DownloadItem? = get(size() - 1)

    override fun close() {
        if (isClosed.compareAndSet(expectedValue = false, newValue = true)) {
            items.clear()

            listeners.forEach { listener ->
                try {
                    listener.onQueueClosed()
                } catch (e: Exception) {
                    logger?.error(e)
                }
            }
            listeners.clear()
        }
    }

    @Throws(IllegalStateException::class)
    private fun checkNotClosed() {
        if (isClosed.load()) {
            throw IllegalStateException("Queue has been closed and cannot be used")
        }
    }

    private fun notifyListeners(action: (DownloadListener) -> Unit) {
        if (isClosed.load()) {
            return
        }

        listeners.forEach { listener ->
            try {
                action(listener)
                listener.onQueueChanged(getItems())
            } catch (e: Exception) {
                logger?.error(e)
            }
        }
    }
}
