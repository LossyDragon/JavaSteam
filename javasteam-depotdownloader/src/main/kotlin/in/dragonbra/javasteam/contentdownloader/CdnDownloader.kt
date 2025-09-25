//package `in`.dragonbra.javasteam.contentdownloader
//
//import `in`.dragonbra.javasteam.util.log.LogManager
//import `in`.dragonbra.javasteam.util.log.Logger
//import io.ktor.client.HttpClient
//import io.ktor.client.engine.cio.CIO
//import io.ktor.client.plugins.HttpRequestRetry
//import io.ktor.client.plugins.HttpTimeout
//import io.ktor.client.request.get
//import io.ktor.client.statement.bodyAsChannel
//import io.ktor.http.HttpHeaders
//import io.ktor.http.isSuccess
//import io.ktor.util.cio.writeChannel
//import io.ktor.utils.io.readAvailable
//import io.ktor.utils.io.writeFully
//import kotlinx.coroutines.*
//import kotlinx.coroutines.channels.*
//import kotlinx.coroutines.flow.*
//import java.io.Closeable
//import java.io.File
//import java.util.concurrent.*
//import kotlin.concurrent.atomics.AtomicBoolean
//import kotlin.concurrent.atomics.ExperimentalAtomicApi
//import kotlin.coroutines.cancellation.CancellationException
//
//data class DownloadProgress(
//    val item: DownloadItem,
//    val bytesDownloaded: Long,
//    val totalBytes: Long,
//    val percentage: Float,
//    val speed: Long,
//    val status: DownloadStatus,
//)
//
//enum class DownloadStatus {
//    QUEUED,
//    DOWNLOADING,
//    PAUSED,
//    COMPLETED,
//    FAILED,
//    CANCELLED
//}
//
//data class DownloadResult(
//    val item: DownloadItem,
//    val status: DownloadStatus,
//    val file: File? = null,
//    val error: Throwable? = null,
//)
//
//
//interface CdnDownloadListener {
//    fun onDownloadStarted(item: DownloadItem)
//    fun onDownloadProgress(progress: DownloadProgress)
//    fun onDownloadCompleted(result: DownloadResult)
//    fun onDownloadFailed(result: DownloadResult)
//    fun onDownloadPaused(item: DownloadItem)
//    fun onDownloadResumed(item: DownloadItem)
//    fun onDownloadCancelled(item: DownloadItem)
//    fun onDownloaderClosed()
//}
//
//@Suppress("unused")
//@OptIn(ExperimentalAtomicApi::class)
//class CdnDownloader(
//    private val contentDownloader: ContentDownloader,
//    private val downloadDirectory: File,
//    private val maxConcurrentDownloads: Int = 3,
//) : Closeable {
//
//    companion object {
//        private val logger: Logger = LogManager.getLogger(CdnDownloader::class.java)
//    }
//
//
//    private val isClosed = AtomicBoolean(false)
//    private val isRunning = AtomicBoolean(false)
//    private val isPaused = AtomicBoolean(false)
//
//
//    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
//    private val downloadJobs = ConcurrentHashMap<DownloadItem, Job>()
//
//
//    private val httpClient = HttpClient(CIO) {
//        install(HttpTimeout) {
//            requestTimeoutMillis = 30000
//            connectTimeoutMillis = 10000
//        }
//
//        install(HttpRequestRetry) {
//            retryOnServerErrors(maxRetries = 3)
//            exponentialDelay()
//        }
//    }
//
//
//    private val listeners = CopyOnWriteArrayList<CdnDownloadListener>()
//
//
//    private val downloadStates = ConcurrentHashMap<DownloadItem, DownloadStatus>()
//    private val downloadProgresses = ConcurrentHashMap<DownloadItem, DownloadProgress>()
//
//
//    private val downloadChannel = Channel<DownloadItem>(Channel.UNLIMITED)
//
//    init {
//
//        if (!downloadDirectory.exists()) {
//            downloadDirectory.mkdirs()
//        }
//
//
//        contentDownloader.addListener(object : DownloadListener {
//            override fun onItemAdded(item: DownloadItem, index: Int) {
//                if (isRunning.load() && !isPaused.load()) {
//                    queueDownload(item)
//                }
//            }
//
//            override fun onItemRemoved(item: DownloadItem, index: Int) {
//                cancelDownload(item)
//            }
//
//            override fun onItemMoved(item: DownloadItem, fromIndex: Int, toIndex: Int) {
//
//            }
//
//            override fun onQueueCleared(previousItems: List<DownloadItem>) {
//                cancelAllDownloads()
//            }
//
//            override fun onQueueChanged(currentItems: List<DownloadItem>) {
//
//            }
//
//            override fun onQueueClosed() {
//                close()
//            }
//        })
//
//
//        startDownloadProcessor()
//    }
//
//
//    fun addListener(listener: CdnDownloadListener) {
//        checkNotClosed()
//        listeners.add(listener)
//    }
//
//    fun removeListener(listener: CdnDownloadListener) {
//        listeners.remove(listener)
//    }
//
//    @Throws(IllegalStateException::class)
//    fun start() {
//        checkNotClosed()
//        if (isRunning.compareAndSet(false, true)) {
//            isPaused.store(false)
//            logger.debug("started")
//
//            contentDownloader.getItems().forEach { item ->
//                queueDownload(item)
//            }
//        }
//    }
//
//    @Throws(IllegalStateException::class)
//    fun pause() {
//        checkNotClosed()
//        if (isPaused.compareAndSet(expectedValue = false, newValue = true)) {
//            logger.debug("paused")
//
//
//            downloadJobs.values.forEach { job ->
//                job.cancel(CancellationException("Download paused"))
//            }
//
//
//            downloadStates.entries
//                .filter { it.value == DownloadStatus.DOWNLOADING }
//                .forEach { (item, _) ->
//                    downloadStates[item] = DownloadStatus.PAUSED
//                    notifyListeners { listener -> listener.onDownloadPaused(item) }
//                }
//        }
//    }
//
//    @Throws(IllegalStateException::class)
//    fun resume() {
//        checkNotClosed()
//        if (isPaused.compareAndSet(expectedValue = true, newValue = false) && isRunning.load()) {
//            logger.debug("resumed")
//
//
//            downloadStates.entries
//                .filter { it.value == DownloadStatus.PAUSED }
//                .forEach { (item, _) ->
//                    queueDownload(item)
//                    notifyListeners { listener -> listener.onDownloadResumed(item) }
//                }
//        }
//    }
//
//    @Throws(IllegalStateException::class)
//    fun stop() {
//        checkNotClosed()
//        if (isRunning.compareAndSet(true, false)) {
//            logger.debug("stopped")
//            cancelAllDownloads()
//        }
//    }
//
//
//    @Throws(IllegalStateException::class)
//    fun cancelDownload(item: DownloadItem) {
//        checkNotClosed()
//        downloadJobs[item]?.cancel(CancellationException("Download cancelled"))
//        downloadJobs.remove(item)
//        downloadStates[item] = DownloadStatus.CANCELLED
//
//        notifyListeners { listener -> listener.onDownloadCancelled(item) }
//    }
//
//
//    fun getDownloadStatus(item: DownloadItem): DownloadStatus {
//        return downloadStates[item] ?: DownloadStatus.QUEUED
//    }
//
//    fun getDownloadProgress(item: DownloadItem): DownloadProgress? {
//        return downloadProgresses[item]
//    }
//
//    fun getActiveDownloads(): List<DownloadItem> {
//        return downloadStates.entries
//            .filter { it.value == DownloadStatus.DOWNLOADING }
//            .map { it.key }
//    }
//
//    fun isRunning(): Boolean = isRunning.load()
//    fun isPaused(): Boolean = isPaused.load()
//    fun isClosed(): Boolean = isClosed.load()
//
//
//    private fun startDownloadProcessor() {
//        scope.launch {
//
//            downloadChannel.consumeAsFlow()
//                .buffer(maxConcurrentDownloads)
//                .collect { item ->
//                    if (!isClosed.load() && isRunning.load() && !isPaused.load()) {
//                        val job = scope.launch {
//                            executeDownload(item)
//                        }
//                        downloadJobs[item] = job
//
//                        job.invokeOnCompletion { exception ->
//                            downloadJobs.remove(item)
//                            if (exception != null && exception !is CancellationException) {
//                                logger.error("Download job completed with exception", exception)
//                            }
//                        }
//                    }
//                }
//        }
//    }
//
//    private fun queueDownload(item: DownloadItem) {
//        if (!isClosed.load() && isRunning.load() && !isPaused.load()) {
//            downloadStates[item] = DownloadStatus.QUEUED
//            scope.launch {
//                downloadChannel.send(item)
//            }
//        }
//    }
//
//    private fun cancelAllDownloads() {
//        downloadJobs.values.forEach { job ->
//            job.cancel(CancellationException("All downloads cancelled"))
//        }
//        downloadJobs.clear()
//
//        downloadStates.keys.forEach { item ->
//            downloadStates[item] = DownloadStatus.CANCELLED
//            notifyListeners { listener -> listener.onDownloadCancelled(item) }
//        }
//    }
//
//    private suspend fun executeDownload(item: DownloadItem) {
//        try {
//            downloadStates[item] = DownloadStatus.DOWNLOADING
//            notifyListeners { listener -> listener.onDownloadStarted(item) }
//
//
//            val downloadUrl = getDownloadUrl(item)
//            val destinationFile = getDestinationFile(item)
//
//            downloadFile(item, downloadUrl, destinationFile)
//
//
//            downloadStates[item] = DownloadStatus.COMPLETED
//            val result = DownloadResult(item, DownloadStatus.COMPLETED, destinationFile)
//
//            notifyListeners { listener -> listener.onDownloadCompleted(result) }
//
//
//            contentDownloader.remove(item)
//
//        } catch (e: CancellationException) {
//            downloadStates[item] = DownloadStatus.CANCELLED
//            throw e
//        } catch (e: Exception) {
//            logger.error("Download failed for item: $item", e)
//            downloadStates[item] = DownloadStatus.FAILED
//
//            val result = DownloadResult(item, DownloadStatus.FAILED, error = e)
//            notifyListeners { listener -> listener.onDownloadFailed(result) }
//        }
//    }
//
//    private suspend fun downloadFile(item: DownloadItem, url: String, destinationFile: File) {
//
//        val response = httpClient.get(url) {
//
//        }
//
//        if (response.status.isSuccess()) {
//            val contentLength = response.headers[HttpHeaders.ContentLength]?.toLongOrNull() ?: 0L
//            var downloadedBytes = 0L
//            val startTime = System.currentTimeMillis()
//            val buffer = ByteArray(8192)
//
//            val inputChannel = response.bodyAsChannel()
//            val outputChannel = destinationFile.writeChannel()
//
//            try {
//                while (!inputChannel.isClosedForRead) {
//                    val bytesRead = inputChannel.readAvailable(buffer)
//                    if (bytesRead == -1) break
//
//                    outputChannel.writeFully(buffer, 0, bytesRead)
//                    downloadedBytes += bytesRead
//
//
//                    val currentTime = System.currentTimeMillis()
//                    val elapsedTime = currentTime - startTime
//                    val speed = if (elapsedTime > 0) (downloadedBytes * 1000) / elapsedTime else 0L
//                    val percentage = if (contentLength > 0) (downloadedBytes.toFloat() / contentLength * 100) else 0f
//
//                    val progress = DownloadProgress(
//                        item = item,
//                        bytesDownloaded = downloadedBytes,
//                        totalBytes = contentLength,
//                        percentage = percentage,
//                        speed = speed,
//                        status = DownloadStatus.DOWNLOADING
//                    )
//
//                    downloadProgresses[item] = progress
//                    notifyListeners { listener -> listener.onDownloadProgress(progress) }
//
//
//                    if (!currentCoroutineContext().isActive) {
//                        throw CancellationException("Download cancelled")
//                    }
//                }
//            } finally {
//                outputChannel.flushAndClose()
//            }
//        } else {
//            throw Exception("HTTP ${response.status.value}: ${response.status.description}")
//        }
//    }
//
//    private fun getDownloadUrl(item: DownloadItem): String {
//        TODO()
//    }
//
//    private fun getDestinationFile(item: DownloadItem): File {
//        TODO()
//    }
//
//    override fun close() {
//        if (isClosed.compareAndSet(expectedValue = false, newValue = true)) {
//            logger.debug("Closing")
//
//            stop()
//
//            downloadChannel.close()
//
//            scope.cancel("closed")
//
//            httpClient.close()
//
//            downloadStates.clear()
//            downloadProgresses.clear()
//
//            try {
//                listeners.forEach { listener ->
//                    try {
//                        listener.onDownloaderClosed()
//                    } catch (e: Exception) {
//                        logger.error("Error notifying listener of downloader closure", e)
//                    }
//                }
//            } finally {
//                listeners.clear()
//            }
//
//            logger.debug("closed")
//        }
//    }
//
//    @Throws(IllegalStateException::class)
//    private fun checkNotClosed() {
//        if (isClosed.load()) {
//            throw IllegalStateException("has been closed and cannot be used")
//        }
//    }
//
//    private fun notifyListeners(action: (CdnDownloadListener) -> Unit) {
//        if (isClosed.load()) return
//
//        listeners.forEach { listener ->
//            try {
//                action(listener)
//            } catch (e: Exception) {
//                logger.error("Error notifying download listener", e)
//            }
//        }
//    }
//}
//
//
///*
//fun main() {
//    val downloadDir = File("downloads")
//    val contentDownloader = ContentDownloader()
//    val = CdnDownloader(contentDownloader, downloadDir)
//
//
//    cdnDownloader.addListener(object : CdnDownloadListenerAdapter() {
//        override fun onDownloadProgress(progress: DownloadProgress) {
//            println("Downloading ${progress.item.appId}: ${progress.percentage}%")
//        }
//
//        override fun onDownloadCompleted(result: DownloadResult) {
//            println("Completed download: ${result.item.appId}")
//        }
//    })
//
//    contentDownloader.add(DownloadItem(12345))
//    contentDownloader.add(DownloadItem(67890))
//
//    cdnDownloader.start()
//
//    cdnDownloader.use { downloader ->
//        // Downloads will happen automatically
//        // downloader.pause()
//        // downloader.resume()
//        // downloader.stop()
//    }
//}
//*/
