package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
import `in`.dragonbra.javasteam.steam.cdn.ClientLancache
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.UGCDetailsCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.CDNAuthToken
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.Closeable
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.set
import kotlin.text.toLongOrNull

data class DownloadProgress(
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Double,
    val percentComplete: Double?,
) {
    fun formatProgress(): String {
        val downloadedMB = downloadedBytes / 1024.0 / 1024.0
        val totalMB = totalBytes?.let { it / 1024.0 / 1024.0 }
        val speedMBps = bytesPerSecond / 1024.0 / 1024.0

        return buildString {
            append("[$fileName] ")
            append("Downloaded: %.2f MB".format(downloadedMB))

            if (totalMB != null) {
                append(" / %.2f MB".format(totalMB))
            }

            if (percentComplete != null) {
                append(" (%.1f%%)".format(percentComplete))
            }

            append(" | Speed: %.2f MB/s".format(speedMBps))
        }
    }
}

fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var size = bytes.toDouble()
    var unitIndex = 0

    while (size >= 1024 && unitIndex < units.size - 1) {
        size /= 1024
        unitIndex++
    }

    return "%.2f %s".format(size, units[unitIndex])
}

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
    val language: String = "",
    val lowViolence: Boolean = false,
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

class ContentDownloaderException(value: String) : Exception(value)

@Suppress("unused")
class ContentDownloader @JvmOverloads constructor(
    private val steamClient: SteamClient,
    private val licenses: List<License>, // To be provided from [LicenseListCallback]
    private val installPath: String, // The path to download too.
    private val debug: Boolean = false, // Enable debugging features, such as logging
    private var maxDownloads: Int = 8, // Max concurrent downloads
    useLanCache: Boolean = false, // Try and detect a lan cache server.
) : Closeable {

    companion object {
        const val INVALID_APP_ID: Int = Int.MAX_VALUE
        const val INVALID_DEPOT_ID: Int = Int.MAX_VALUE
        const val INVALID_MANIFEST_ID: Long = Long.MAX_VALUE
    }

    // What is a PriorityQueue?

    private val filesystem: FileSystem by lazy { FileSystem.SYSTEM }

    private val items = CopyOnWriteArrayList(ArrayList<DownloadItem>())
    private val listeners = CopyOnWriteArrayList<DownloadListener>()
    private val scope: CoroutineScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var logger: Logger? = null
    private val isStarted: AtomicBoolean = AtomicBoolean(false)
    private val processingChannel = Channel<DownloadItem>(Channel.UNLIMITED)

    internal var steamUser: SteamUser? = null
    internal var steamContent: SteamContent? = null
    internal var steamApps: SteamApps? = null
    internal var steamCloud: SteamCloud? = null
    internal var steamPublishedFile: PublishedFile? = null

    internal val appTokens = mutableMapOf<Int, Long>()
    internal val packageTokens = mutableMapOf<Int, Long>()
    internal val depotKeys = mutableMapOf<Int, ByteArray>()
    internal val cdnAuthTokens = ConcurrentHashMap<Pair<Int, String>, CompletableDeferred<CDNAuthToken>>()
    internal val appInfo = mutableMapOf<Int, PICSProductInfo?>()
    internal val packageInfo = mutableMapOf<Int, PICSProductInfo?>()
    internal val appBetaPasswords = mutableMapOf<String, ByteArray>()

    private var cdnClientPool: CDNClientPool? = null

    init {
        if (debug) {
            logger = LogManager.getLogger(ContentDownloader::class.java)
        }

        steamUser = steamClient.getHandler<SteamUser>()
        steamContent = steamClient.getHandler<SteamContent>()
        steamApps = steamClient.getHandler<SteamApps>()
        steamCloud = steamClient.getHandler<SteamCloud>()

        scope.launch {
            if (useLanCache) {
                ClientLancache.detectLancacheServer()
            }

            if (ClientLancache.useLanCacheServer) {
                logger?.debug("Detected Lan-Cache server! Downloads will be directed through the Lancache.")
            }

            // Increasing the number of concurrent downloads when the cache is detected since the downloads will likely
            // be served much faster than over the internet.  Steam internally has this behavior as well.
            maxDownloads = if (ClientLancache.useLanCacheServer && maxDownloads == 8) 25 else maxDownloads
        }
    }

    // region [REGION] Steam Operations
    suspend fun requestPackageInfo(packageIds: ArrayList<Int>) {
        packageIds.removeAll(packageInfo.keys)

        if (packageIds.isEmpty()) return

        val packageRequests = arrayListOf<PICSRequest>()

        packageIds.forEach { pkg ->
            val request = PICSRequest(id = pkg)

            packageTokens[pkg]?.let { token ->
                request.accessToken = token
            }

            packageRequests.add(request)
        }

        val packageInfoMultiple = steamApps!!.picsGetProductInfo(emptyList(), packageRequests).await()

        packageInfoMultiple.results.forEach { pkgInfo ->
            pkgInfo.packages.forEach { pkgValue ->
                val pkg = pkgValue.value
                packageInfo[pkg.id] = pkg
            }
            pkgInfo.unknownPackages.forEach { pkgValue ->
                packageInfo[pkgValue] = null
            }
        }
    }

    suspend fun getPublishedFileDetails(appId: Int, pubFile: PublishedFileID): PublishedFileDetails? {
        val pubFileRequest = CPublishedFile_GetDetails_Request.newBuilder().apply {
            this.appid = appId
            this.addPublishedfileids(pubFile.toLong())
        }.build()

        val details = steamPublishedFile!!.getDetails(pubFileRequest).await()

        if (details.result == EResult.OK) {
            return details.body.publishedfiledetailsBuilderList.firstOrNull()?.build()
        }

        throw ContentDownloaderException("EResult ${details.result.code()} (${details.result}) while retrieving file details for pubfile $pubFile.")
    }

    suspend fun getUGCDetails(ugcHandle: UGCHandle): UGCDetailsCallback? {
        val callback = steamCloud!!.requestUGCDetails(ugcHandle).await()

        if (callback.result == EResult.OK) {
            return callback
        } else if (callback.result == EResult.FileNotFound) {
            return null
        }

        throw ContentDownloaderException($"EResult ${callback.result.code()} (${callback.result}) while retrieving UGC details for ${ugcHandle.value}.")
    }

    suspend fun requestAppInfo(appId: Int, bForce: Boolean = false) {
        if ((appInfo.contains(appId) && !bForce)) {
            return
        }

        val appTokens = steamApps!!.picsGetAccessTokens(appId).await()

        if (appTokens.appTokensDenied.contains(appId)) {
            logger?.error("Insufficient privileges to get access token for app $appId")
        }

        appTokens.appTokens.forEach { tokenDict ->
            this.appTokens[tokenDict.key] = tokenDict.value
        }

        val request = PICSRequest(appId)

        this.appTokens[appId]?.let { token ->
            request.accessToken = token
        }

        val appInfoMultiple = steamApps!!.picsGetProductInfo(request).await()

        appInfoMultiple.results.forEach { appInfo ->
            appInfo.apps.forEach { appValue ->
                val app = appValue.value
                logger?.debug("Got AppInfo for ${app.id}")
                this.appInfo[app.id] = app
            }
            appInfo.unknownApps.forEach { app ->
                this.appInfo[app] = null
            }
        }
    }

    suspend fun requestFreeAppLicense(appId: Int): Boolean {
        try {
            val resultInfo = steamApps!!.requestFreeLicense(appId).await()
            return resultInfo.grantedApps.contains(appId)
        } catch (e: Exception) {
            logger?.error("Failed to request FreeOnDemand license for app $appId: ${e.message}")
            return false
        }
    }

    suspend fun requestDepotKey(depotId: Int, appId: Int = 0) {
        if (this.depotKeys.contains(depotId)) {
            return
        }

        val depotKey = steamApps!!.getDepotDecryptionKey(depotId, appId).await()

        logger?.debug("Got depot key for ${depotKey.depotID} result: ${depotKey.result}")

        if (depotKey.result != EResult.OK) {
            return
        }

        this.depotKeys[depotKey.depotID] = depotKey.depotKey
    }

    suspend fun getDepotManifestRequestCode(
        depotId: Int,
        appId: Int,
        manifestId: Long,
        branch: String,
    ) = withContext(Dispatchers.IO) {
        val requestCode = steamContent!!.getManifestRequestCode(
            depotId = depotId,
            appId = appId,
            manifestId = manifestId,
            branch = branch,
            branchPasswordHash = null,
            parentScope = this
        ).await()

        if (requestCode == 0L) {
            logger?.error("No manifest request code was returned for depot $depotId from app $appId, manifest $manifestId")

            if (steamClient.isDisconnected) {
                logger?.debug("Suggestion: Try logging in with -username as old manifests may not be available for anonymous accounts.")
            }
        } else {
            logger?.debug("Got manifest request code for depot $depotId from app $appId, manifest $manifestId, result: $requestCode")
        }

        return@withContext requestCode
    }

    suspend fun requestCDNAuthToken(appId: Int, depotId: Int, server: Server) = withContext(Dispatchers.IO) {
        val cdnKey = depotId to server.host!!
        val completion = CompletableDeferred<CDNAuthToken>()

        cdnAuthTokens[cdnKey] = completion

        logger?.debug("Requesting CDN auth token for ${server.host}")

        val cdnAuth = steamContent!!.getCDNAuthToken(appId, depotId, server.host!!, this).await()

        logger?.debug("Got CDN auth token for ${server.host} result: ${cdnAuth.result} (expires ${cdnAuth.expiration})")

        if (cdnAuth.result != EResult.OK) {
            return@withContext
        }

        completion.complete(cdnAuth)
    }

    suspend fun checkAppBetaPassword(appId: Int, password: String) {
        val appPassword = steamApps!!.checkAppBetaPassword(appId, password).await()

        logger?.debug("Retrieved ${appPassword.betaPasswords.size} beta keys with result: ${appPassword.result}")

        appPassword.betaPasswords.forEach { entry ->
            this.appBetaPasswords[entry.key] = entry.value
        }
    }

    suspend fun getPrivateBetaDepotSection(appId: Int, branch: String): KeyValue {
        // Should be filled by CheckAppBetaPassword
        val branchPassword = appBetaPasswords[branch] ?: return KeyValue()

        // Should be filled by RequestAppInfo
        val accessToken = appTokens[appId] ?: 0L

        val privateBeta = steamApps!!.picsGetPrivateBeta(appId, accessToken, branch, branchPassword).await()

        return privateBeta.depotSection
    }
    // endregion

    // region [REGION] Downloading Operations
    suspend fun downloadPubFile(item: DownloadItem) {
        requireNotNull(item.pubFile)

        val pubFile = PublishedFileID(item.pubFile)
        val details = getPublishedFileDetails(item.appId, pubFile)

        requireNotNull(details)

        if (details.fileUrl.isNullOrBlank().not()) {
            downloadWebFile(item.appId, details.filename, details.fileUrl)
        } else if (details.hcontentFile > 0) {
            downloadApp(item)
        } else {
            logger?.error("Unable to locate manifest ID for published file $pubFile")
        }
    }

    suspend fun downloadUGC(item: DownloadItem) {
        var details: UGCDetailsCallback? = null

        val steamUser = requireNotNull(steamUser)
        val steamId = requireNotNull(steamUser.steamID)
        val ugcId = requireNotNull(item.ugcId)

        if (steamId.accountType != EAccountType.AnonUser) {
            val ugcHandle = UGCHandle(ugcId)
            details = getUGCDetails(ugcHandle)
        } else {
            logger?.error("Unable to query UGC details for $ugcId from an anonymous account")
        }

        if (!details?.url.isNullOrBlank()) {
            downloadWebFile(item.appId, details.fileName, details.url)
        } else {
            downloadApp(item)
        }
    }

    suspend fun downloadWebFile(appId: Int, fileName: String, url: String) {
        val installDir = createDirectories(appId, 0)

        if (installDir == null) {
            logger?.debug("Error: Unable to create install directories!")
            return
        }

        val stagingDir = installDir.resolve("staging")
        val fileStagingPath = stagingDir.resolve(fileName)
        val fileFinalPath = installDir.resolve(fileName)

        fileFinalPath.parent?.let { filesystem.createDirectories(it) }
        fileStagingPath.parent?.let { filesystem.createDirectories(it) }

        HttpClient.httpClient.use { client ->
            logger?.debug("Starting download of $fileName...")

            val response = client.get(url)
            val channel = response.bodyAsChannel()

            val totalBytes = response.headers[HttpHeaders.ContentLength]?.toLongOrNull()

            var downloadedBytes = 0L
            var lastProgressTime = System.currentTimeMillis()
            var lastDownloadedBytes = 0L
            val progressUpdateInterval = 1000L

            logger?.debug("File size: ${totalBytes?.let { formatBytes(it) } ?: "Unknown"}")

            filesystem.write(fileStagingPath) {
                while (!channel.isClosedForRead) {
                    val packet = channel.readRemaining(DEFAULT_BUFFER_SIZE.toLong())
                    if (!packet.exhausted()) {
                        val bytes = packet.readByteArray()
                        write(bytes)
                        downloadedBytes += bytes.size

                        val currentTime = System.currentTimeMillis()
                        if (currentTime - lastProgressTime >= progressUpdateInterval) {
                            val timeDiff = (currentTime - lastProgressTime) / 1000.0
                            val bytesDiff = downloadedBytes - lastDownloadedBytes
                            val bytesPerSecond = bytesDiff / timeDiff

                            val percentComplete = totalBytes?.let {
                                (downloadedBytes.toDouble() / it.toDouble()) * 100.0
                            }

                            val progress = DownloadProgress(
                                fileName = fileName,
                                downloadedBytes = downloadedBytes,
                                totalBytes = totalBytes,
                                bytesPerSecond = bytesPerSecond,
                                percentComplete = percentComplete
                            )

                            logger?.debug(progress.formatProgress())

                            lastProgressTime = currentTime
                            lastDownloadedBytes = downloadedBytes
                        }
                    }
                }
            }

            val totalTime = (System.currentTimeMillis() - (lastProgressTime - progressUpdateInterval)) / 1000.0
            val averageSpeed = downloadedBytes / totalTime

            logger?.debug(
                "Download completed. \n Final stats: " +
                    "${formatBytes(downloadedBytes)} in %.1f seconds (avg: %.2f MB/s)"
                        .format(totalTime, averageSpeed / 1024.0 / 1024.0)
            )
        }

        if (filesystem.exists(fileFinalPath)) {
            filesystem.delete(fileFinalPath)
        }

        filesystem.atomicMove(fileStagingPath, fileFinalPath)
        logger?.debug("File moved to final location: $fileFinalPath")
    }

    suspend fun downloadApp(item: DownloadItem) {
        cdnClientPool = CDNClientPool.init(steamClient, debug)

        requestAppInfo(item.appId)

        if (!accountHasAccess(item.appId, item.appId)) {
            val contentName = getAppName(item.appId)
            throw ContentDownloaderException("App ${item.appId} ($contentName) is not available from this account.")
        }

        val depots = getSteam3AppSection(item.appId, EAppInfoSection.Depots)

        val depotIdsExpected = mutableListOf(item.depotId)

        if (item.ugcId != null) {
            val workshopDepot = depots?.get("workshopdepot")?.asInteger()
            if (workshopDepot != 0) {
                depotIdsExpected.add(workshopDepot)
                // ?? depotManifestIds = depotManifestIds.Select(pair => (workshopDepot, pair.manifestId)).ToList();
            }
        } else {
            logger?.debug("Using app branch: ${item.branch}")

            depots?.children?.forEach { depotSection ->
                var id: Int? = INVALID_DEPOT_ID

                if (depotSection.children.isEmpty()) {
                    logger?.debug("Empty children, continuing")
                    return@forEach
                }

                id = depotSection.name?.toIntOrNull() ?: return@forEach

                // ??
                // if (hasSpecificDepots && !depotIdsExpected.Contains(id))
                //     continue;


            }
        }

        TODO()
    }

    private fun createDirectories(depotId: Int, depotVersion: Int): Path? = try {
        val installDir = installPath.toPath()
        filesystem.createDirectories(installDir)
        filesystem.createDirectories(installDir.resolve("config"))
        filesystem.createDirectories(installDir.resolve("staging"))
        installDir
    } catch (e: Exception) {
        logger?.error(e)
        null
    }

    private fun getAppName(appId: Int): String {
        val info = getSteam3AppSection(appId, EAppInfoSection.Common) ?: KeyValue.INVALID
        return info["name"].asString() ?: ""
    }

    private fun getSteam3AppSection(appId: Int, section: EAppInfoSection): KeyValue? {
        if (appInfo.isEmpty()) {
            return null
        }

        val app = appInfo[appId] ?: return null

        val appInfo = app.keyValues
        val sectionKey = when (section) {
            EAppInfoSection.Common -> "common"
            EAppInfoSection.Extended -> "extended"
            EAppInfoSection.Config -> "config"
            EAppInfoSection.Depots -> "depots"
            else -> throw ContentDownloaderException("${section.name} not implemented")
        }

        val sectionKV = appInfo.children.firstOrNull { c -> c.name == sectionKey }
        return sectionKV
    }

    private suspend fun accountHasAccess(appId: Int, depotId: Int): Boolean {
        val steamUser = requireNotNull(steamUser)
        val steamID = requireNotNull(steamUser.steamID)

        if (licenses.isEmpty() && steamID.accountType != EAccountType.AnonUser) {
            return false
        }

        val licenseQuery = arrayListOf<Int>()
        if (steamID.accountType == EAccountType.AnonUser) {
            licenseQuery.add(17906)
        } else {
            licenseQuery.addAll(licenses.map { it.packageID }.distinct())
        }

        requestPackageInfo(licenseQuery)

        licenseQuery.forEach { license ->
            packageInfo[license]?.let { pkg ->
                if (pkg.keyValues["appids"].children.any { child -> child.asInteger() == depotId }) {
                    return true
                }
                if (pkg.keyValues["depotids"].children.any { child -> child.asInteger() == depotId }) {
                    return true
                }
            }
        }

        // Check if this app is free to download without a license
        val info = getSteam3AppSection(appId, EAppInfoSection.Common)

        return info != null && info["FreeToDownload"].asBoolean()
    }
    // endregion

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

                    downloadPubFile(item)
                } else if (item.ugcId != null) {
                    logger?.debug("Downloading UGC File for ${item.appId}")

                    downloadUGC(item)
                } else {
                    logger?.debug("Trying App download for ${item.appId}")

                    downloadApp(item)
                }

                // TODO remove from internal list and notify when done??
            }
        }
    }

    override fun close() {
        isStarted.set(false)

        HttpClient.close()

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

        steamUser = null
        steamContent = null
        steamApps = null
        steamCloud = null

        appTokens.clear()
        packageTokens.clear()
        depotKeys.clear()
        cdnAuthTokens.clear()
        appInfo.clear()
        packageInfo.clear()
        appBetaPasswords.clear()

        cdnClientPool?.close()
        cdnClientPool = null

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
