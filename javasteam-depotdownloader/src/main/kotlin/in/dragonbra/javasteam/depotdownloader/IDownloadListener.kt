package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadProgress

/**
 * @author Lossy
 * @since Oct 1, 2025
 */
interface IDownloadListener {
    // List<DownloadItem> callbacks.
    fun onItemAdded(item: DownloadItem, index: Int)
    fun onItemRemoved(item: DownloadItem, index: Int)
    fun onItemMoved(item: DownloadItem, fromIndex: Int, toIndex: Int)
    fun onQueueCleared(previousItems: List<DownloadItem>)
    fun onQueueChanged(currentItems: List<DownloadItem>)
    fun onQueueClosed()

    // Download callbacks.
    fun onDownloadProgress(appId: Int, progress: DownloadProgress)
    // TODO()

    // Other
    fun onAndroidEmulation(value: Boolean) {}
}
