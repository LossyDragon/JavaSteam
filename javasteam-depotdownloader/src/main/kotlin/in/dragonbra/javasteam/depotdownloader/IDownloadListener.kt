package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem

interface IDownloadListener {
    fun onItemAdded(item: DownloadItem, index: Int)
    fun onItemRemoved(item: DownloadItem, index: Int)
    fun onItemMoved(item: DownloadItem, fromIndex: Int, toIndex: Int)
    fun onQueueCleared(previousItems: List<DownloadItem>)
    fun onQueueChanged(currentItems: List<DownloadItem>)
    fun onQueueClosed()

    // TODO (some maybe)
    // fun onQueueSize() // How many items are in the queue
    // fun onStatus() // Maybe sent an Enum on what is happening right now for each item:
    //                // "Validating", "Pre-allocating", "Downloading chunk xxx", etc.
    // fun onProgress() // Current Progress percent for item
    // fun onError() // If debugging is false, maybe broadcast certain info, warnings, or errors.
}
