package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem

interface IDownloadListener {
    fun onItemAdded(item: DownloadItem, index: Int)
    fun onItemRemoved(item: DownloadItem, index: Int)
    fun onItemMoved(item: DownloadItem, fromIndex: Int, toIndex: Int)
    fun onQueueCleared(previousItems: List<DownloadItem>)
    fun onQueueChanged(currentItems: List<DownloadItem>)
    fun onQueueClosed()
}
