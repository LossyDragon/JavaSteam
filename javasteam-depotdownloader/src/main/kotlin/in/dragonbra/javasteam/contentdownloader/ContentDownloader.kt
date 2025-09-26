package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.steam.cdn.ClientLancache
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import java.io.Closeable
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.log

/**
 *
 */
data class DownloadItem(
    val appId: Int,
    val manifestId: Long? = null,
    val depotId: Int? = null,
    val depotKey: Int? = null,
    val manifestOnly: Boolean = false,
    val pubFile: Long? = null,
    val ugcId: Long? = null,
    val branch: String = "public",
    val validate: Boolean = false,
    val betaOrBranchPassword: String? = null,
    val os: EOS = EOS.WINDOWS,
    val arch: EArch = EArch.X64,
)

enum class EArch(val value: String) {
    X86("32"),
    X64("64"),
}

enum class EOS(val value: String) {
    WINDOWS("windows"),
    MACOS("macos"),
    LINUX("linux"),
}

interface DownloadListener {
    fun onItemAdded(item: DownloadItem, index: Int)
    fun onItemRemoved(item: DownloadItem, index: Int)
    fun onItemMoved(item: DownloadItem, fromIndex: Int, toIndex: Int)
    fun onQueueCleared(previousItems: List<DownloadItem>)
    fun onQueueChanged(currentItems: List<DownloadItem>)
    fun onQueueClosed()
}

@Suppress("unused")
class ContentDownloader @JvmOverloads constructor(
    steamClient: SteamClient,
    packageTokens: Map<Int, Long>, // To be provided from [LicenseListCallback]
    debug: Boolean = false, // Enable debugging features, such as logging
    useLanCache: Boolean = false, // Try and detect a lan cache server.
    maxDownloads: Int = 8, // Max concurrent downloads
) : Closeable {

    // What is a PriorityQueue?

    private lateinit var cdnClient: CDNClient
    private var steamSession: SteamSession

    private val items = CopyOnWriteArrayList(ArrayList<DownloadItem>())
    private val listeners = CopyOnWriteArrayList<DownloadListener>()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logger: Logger? = null
    private val isStarted: AtomicBoolean = AtomicBoolean(false)
    private val processingChannel = Channel<DownloadItem>(Channel.UNLIMITED)

    init {
        if (debug) {
            logger = LogManager.getLogger(ContentDownloader::class.java)
        }

        steamSession = SteamSession(steamClient, debug)

        scope.launch {
            if (useLanCache) {
                ClientLancache.detectLancacheServer()
            }

            if (ClientLancache.useLanCacheServer) {
                logger?.debug("Detected Lan-Cache server! Downloads will be directed through the Lancache.")
            }

            // Increasing the number of concurrent downloads when the cache is detected since the downloads will likely
            // be served much faster than over the internet.  Steam internally has this behavior as well.
            cdnClient = CDNClient(
                steamSession = steamSession,
                debug = debug,
                useLanCache = useLanCache,
                maxDownloads = if (ClientLancache.useLanCacheServer && maxDownloads == 8) 25 else maxDownloads
            )
        }
    }

    // region [REGION] Listener Operations

    fun addListener(listener: DownloadListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DownloadListener) {
        listeners.remove(listener)
    }

    // endregion

    // region [REGION] Array Operations

    fun getItems(): List<DownloadItem> = items.toList()

    fun size(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun add(item: DownloadItem) {
        val index = items.size
        items.add(item)

        if (isStarted.get()) {
            scope.launch { processingChannel.send(item) }
        }

        notifyListeners { listener -> listener.onItemAdded(item, index) }
    }

    fun addFirst(item: DownloadItem) {
        if (isStarted.get()) {
            logger?.debug("Cannot add item when started.")
            return
        }

        items.add(0, item)
        notifyListeners { listener -> listener.onItemAdded(item, 0) }
    }

    fun addAt(index: Int, item: DownloadItem): Boolean {
        if (isStarted.get()) {
            logger?.debug("Cannot addAt item when started.")
            return false
        }

        return try {
            items.add(index, item)
            notifyListeners { listener -> listener.onItemAdded(item, index) }
            true
        } catch (e: IndexOutOfBoundsException) {
            false
        }
    }

    fun removeFirst(): DownloadItem? {
        if (isStarted.get()) {
            logger?.debug("Cannot removeFirst item when started.")
            return null
        }

        return if (items.isNotEmpty()) {
            val item = items.removeAt(0)
            notifyListeners { listener -> listener.onItemRemoved(item, 0) }
            item
        } else {
            null
        }
    }

    fun removeLast(): DownloadItem? {
        if (isStarted.get()) {
            logger?.debug("Cannot removeLast item when started.")
            return null
        }

        return if (items.isNotEmpty()) {
            val lastIndex = items.size - 1
            val item = items.removeAt(lastIndex)
            notifyListeners { listener -> listener.onItemRemoved(item, lastIndex) }
            item
        } else {
            null
        }
    }

    fun remove(item: DownloadItem): Boolean {
        if (isStarted.get()) {
            logger?.debug("Cannot remove item when started.")
            return false
        }

        val index = items.indexOf(item)
        return if (index >= 0) {
            items.removeAt(index)
            notifyListeners { listener -> listener.onItemRemoved(item, index) }
            true
        } else {
            false
        }
    }

    fun removeAt(index: Int): DownloadItem? {
        if (isStarted.get()) {
            logger?.debug("Cannot removeAt item when started.")
            return null
        }

        return try {
            val item = items.removeAt(index)
            notifyListeners { listener -> listener.onItemRemoved(item, index) }
            item
        } catch (e: IndexOutOfBoundsException) {
            null
        }
    }

    fun moveItem(fromIndex: Int, toIndex: Int): Boolean {
        if (isStarted.get()) {
            logger?.debug("Cannot moveItem item when started.")
            return false
        }

        return try {
            val item = items.removeAt(fromIndex)
            items.add(toIndex, item)
            notifyListeners { listener -> listener.onItemMoved(item, fromIndex, toIndex) }
            true
        } catch (e: IndexOutOfBoundsException) {
            false
        }
    }

    fun clear() {
        if (isStarted.get()) {
            logger?.debug("Cannot clear item when started.")
            return
        }

        val oldItems = items.toList()
        items.clear()
        notifyListeners { listener -> listener.onQueueCleared(oldItems) }
    }

    fun get(index: Int): DownloadItem? = items.getOrNull(index)

    fun contains(item: DownloadItem): Boolean = items.contains(item)

    fun indexOf(item: DownloadItem): Int = items.indexOf(item)

    // endregion

    fun start() {
        scope.launch {
            isStarted.set(true) // Deny Array manipulation, except for adding.

            items.forEach { processingChannel.send(it) } // Add existing to queue

            // Process the channel, adding more items after we start is allowed.
            for (item in processingChannel) {

                ensureActive()

                if (!isStarted.get()) {
                    break
                }

                if (item.pubFile != null) {
                    logger?.debug("Downloading PUB File for ${item.appId}")

                    cdnClient.downloadPubFile(item)
                } else if (item.ugcId != null) {
                    logger?.debug("Downloading UGC File for ${item.appId}")

                    cdnClient.downloadUGC(item)
                } else {
                    logger?.debug("Trying App download for ${item.appId}")

                    if(item.betaOrBranchPassword.isNullOrBlank().not() && item.branch.isBlank()) {
                        logger?.error("Error: Cannot specify 'branch password' when 'branch' is not specified")
                        continue
                    }



                    TODO()
                }

                // TODO remove from internal list and notify when done??
            }
        }
    }

    override fun close() {
        isStarted.set(false)

        items.clear()
        processingChannel.close()

        listeners.forEach { listener ->
            try {
                listener.onQueueClosed()
            } catch (e: Exception) {
                logger?.error(e)
            }
        }
        listeners.clear()

        LogManager.removeLogger(ContentDownloader::class.java)
        logger = null
    }

    private fun notifyListeners(action: (DownloadListener) -> Unit) {
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
