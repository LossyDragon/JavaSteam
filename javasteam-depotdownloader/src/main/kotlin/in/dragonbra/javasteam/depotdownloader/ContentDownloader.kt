package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.depotdownloader.data.AppItem
import `in`.dragonbra.javasteam.depotdownloader.data.DepotDownloadCounter
import `in`.dragonbra.javasteam.depotdownloader.data.DepotDownloadInfo
import `in`.dragonbra.javasteam.depotdownloader.data.DepotFilesData
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadItem
import `in`.dragonbra.javasteam.depotdownloader.data.DownloadProgress
import `in`.dragonbra.javasteam.depotdownloader.data.GlobalDownloadCounter
import `in`.dragonbra.javasteam.depotdownloader.data.PubFileItem
import `in`.dragonbra.javasteam.depotdownloader.data.UgcItem
import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
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
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.SteamKitWebRequestException
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.readRemaining
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.future.future
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.io.readByteArray
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import java.io.Closeable
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.collections.mutableListOf
import kotlin.collections.set
import kotlin.text.toLongOrNull

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
        const val DEFAULT_BRANCH: String = "public"
        const val CONFIG_DIR: String = ".DepotDownloader"
        val STAGING_DIR: Path = CONFIG_DIR.toPath() / "staging"
    }

    // What is a PriorityQueue?

    private val filesystem: FileSystem by lazy { FileSystem.SYSTEM }

    private val items = CopyOnWriteArrayList(ArrayList<DownloadItem>())
    private val listeners = CopyOnWriteArrayList<IDownloadListener>()
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

    private var config: Config = Config(installPath = installPath.toPath())

    private data class Config(
        val installPath: Path? = null,
        val betaPassword: String? = null,
        val downloadAllPlatforms: Boolean = false,
        val downloadAllArchs: Boolean = false,
        val downloadAllLanguages: Boolean = false,
        val androidEmulation: Boolean = false,
        val downloadManifestOnly: Boolean = false,

        // Not used yet in code
        val usingFileList: Boolean = false,
        var filesToDownloadRegex: List<Regex> = emptyList(),
        var filesToDownload: HashSet<String> = hashSetOf(),
    )

    init {
        if (debug) {
            logger = LogManager.getLogger(ContentDownloader::class.java)
        }

        logger?.debug("DepotDownloader launched with ${licenses.size} for account")

        steamUser = requireNotNull(steamClient.getHandler<SteamUser>())
        steamContent = requireNotNull(steamClient.getHandler<SteamContent>())
        steamApps = requireNotNull(steamClient.getHandler<SteamApps>())
        steamCloud = requireNotNull(steamClient.getHandler<SteamCloud>())

        scope.launch {
            if (useLanCache) {
                ClientLancache.detectLancacheServer()
            }

            if (ClientLancache.useLanCacheServer) {
                logger?.debug("Detected Lan-Cache server! Downloads will be directed through the Lancache.")

                // Increasing the number of concurrent downloads when the cache is detected since the downloads will likely
                // be served much faster than over the internet.  Steam internally has this behavior as well.
                maxDownloads = if (ClientLancache.useLanCacheServer && maxDownloads == 8) 25 else maxDownloads
            }
        }
    }

    // region [REGION] Steam Operations
    private val packageInfoMutex = Mutex()
    suspend fun requestPackageInfo(packageIds: ArrayList<Int>) {
        packageInfoMutex.withLock {
            // I have a silly race condition???
            val packagesToFetch = packageIds.toMutableList() // I have to create a copy for some reason??
            packagesToFetch.removeAll(packageInfo.keys)

            if (packagesToFetch.isEmpty()) return

            val packageRequests = arrayListOf<PICSRequest>()

            packagesToFetch.forEach { pkg ->
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
        if (depotKeys.contains(depotId)) {
            return
        }

        val depotKey = steamApps!!.getDepotDecryptionKey(depotId, appId).await()

        logger?.debug("Got depot key for ${depotKey.depotID} result: ${depotKey.result}")

        if (depotKey.result != EResult.OK) {
            return
        }

        depotKeys[depotKey.depotID] = depotKey.depotKey
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
    suspend fun downloadPubFile(appId: Int, publishedFileId: Long) {
        val details = getPublishedFileDetails(appId, PublishedFileID(publishedFileId))

        requireNotNull(details)

        if (details.fileUrl.isNullOrBlank().not()) {
            downloadWebFile(appId, details.filename, details.fileUrl)
        } else if (details.hcontentFile > 0) {
            downloadApp(
                appId = appId,
                depotManifestIds = listOf(appId to details.hcontentFile),
                branch = DEFAULT_BRANCH,
                os = null,
                arch = null,
                language = null,
                lv = false,
                isUgc = true,
            )
        } else {
            logger?.error("Unable to locate manifest ID for published file $publishedFileId")
        }
    }

    suspend fun downloadUGC(
        appId: Int,
        ugcId: Long,
    ) {
        var details: UGCDetailsCallback? = null

        val steamUser = requireNotNull(steamUser)
        val steamId = requireNotNull(steamUser.steamID)

        if (steamId.accountType != EAccountType.AnonUser) {
            val ugcHandle = UGCHandle(ugcId)
            details = getUGCDetails(ugcHandle)
        } else {
            logger?.error("Unable to query UGC details for $ugcId from an anonymous account")
        }

        if (!details?.url.isNullOrBlank()) {
            downloadWebFile(appId = appId, fileName = details.fileName, url = details.url)
        } else {
            downloadApp(
                appId = appId,
                depotManifestIds = listOf(appId to ugcId),
                branch = DEFAULT_BRANCH,
                os = null,
                arch = null,
                language = null,
                lv = false,
                isUgc = true,
            )
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

            logger?.debug("File size: ${totalBytes?.let { Util.formatBytes(it) } ?: "Unknown"}")

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
                    "${Util.formatBytes(downloadedBytes)} in %.1f seconds (avg: %.2f MB/s)"
                        .format(totalTime, averageSpeed / 1024.0 / 1024.0)
            )
        }

        if (filesystem.exists(fileFinalPath)) {
            filesystem.delete(fileFinalPath)
        }

        filesystem.atomicMove(fileStagingPath, fileFinalPath)
        logger?.debug("File moved to final location: $fileFinalPath")
    }

    // L4D2 (app) supports LV
    suspend fun downloadApp(
        appId: Int,
        depotManifestIds: List<Pair<Int, Long>>,
        branch: String,
        os: String?,
        arch: String?,
        language: String?,
        lv: Boolean,
        isUgc: Boolean,
    ) {
        var depotManifestIds = depotManifestIds.toMutableList()

        val steamUser = requireNotNull(steamUser)
        cdnClientPool = CDNClientPool.init(steamClient, appId, debug)

        // Load our configuration data containing the depots currently installed
        val configPath = requireNotNull(config.installPath)

        filesystem.createDirectories(configPath)
        DepotConfigStore.loadFromFile(configPath / CONFIG_DIR / "depot.config")

        requestAppInfo(appId)

        if (!accountHasAccess(appId, appId)) {
            if (steamUser.steamID!!.accountType != EAccountType.AnonUser && requestFreeAppLicense(appId)) {
                logger?.debug("Obtained FreeOnDemand license for app $appId")

                // Fetch app info again in case we didn't get it fully without a license.
                requestAppInfo(appId, true)
            } else {
                val contentName = getAppName(appId)
                throw ContentDownloaderException("App $appId ($contentName) is not available from this account.")
            }
        }

        val hasSpecificDepots = depotManifestIds.isNotEmpty()
        val depotIdsFound = mutableListOf<Int>()
        val depotIdsExpected = depotManifestIds.map { x -> x.first }.toMutableList()
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)

        if (isUgc) {
            val workshopDepot = depots!!["workshopdepot"].asInteger()
            if (workshopDepot != 0 && !depotIdsExpected.contains(workshopDepot)) {
                depotIdsExpected.add(workshopDepot)
                depotManifestIds = depotManifestIds.map { pair -> workshopDepot to pair.second }.toMutableList()
            }

            depotIdsFound.addAll(depotIdsExpected)
        } else {
            logger?.debug("Using app branch: $branch")

            depots?.children?.forEach { depotSection ->
                var id = INVALID_DEPOT_ID

                if (depotSection.children.isEmpty()) {
                    return@forEach
                }

                id = depotSection.name?.toIntOrNull() ?: return@forEach

                if (hasSpecificDepots && !depotIdsExpected.contains(id)) {
                    return@forEach
                }

                if (!hasSpecificDepots) {
                    val depotConfig = depotSection["config"]
                    if (depotConfig != KeyValue.INVALID) {
                        if (!config.downloadAllPlatforms &&
                            depotConfig["oslist"] != KeyValue.INVALID &&
                            !depotConfig["oslist"].value.isNullOrBlank()
                        ) {
                            val osList = depotConfig["oslist"].value!!.split(",")
                            if (osList.indexOf(os ?: Util.getSteamOS(config.androidEmulation)) == -1) {
                                return@forEach
                            }
                        }

                        if (!config.downloadAllArchs &&
                            depotConfig["osarch"] != KeyValue.INVALID &&
                            !depotConfig["osarch"].value.isNullOrBlank()
                        ) {
                            val depotArch = depotConfig["osarch"].value
                            if (depotArch != (arch ?: Util.getSteamArch())) {
                                return@forEach
                            }
                        }

                        if (!config.downloadAllLanguages &&
                            depotConfig["language"] != KeyValue.INVALID &&
                            !depotConfig["language"].value.isNullOrBlank()
                        ) {
                            val depotLang = depotConfig["language"].value
                            if (depotLang != (language ?: "english")) {
                                return@forEach
                            }
                        }

                        if (!lv &&
                            depotConfig["lowviolence"] != KeyValue.INVALID &&
                            depotConfig["lowviolence"].asBoolean()
                        ) {
                            return@forEach
                        }
                    }
                }

                depotIdsFound.add(id)

                if (!hasSpecificDepots) {
                    depotManifestIds.add(id to INVALID_MANIFEST_ID)
                }
            }

            if (depotManifestIds.isEmpty() && !hasSpecificDepots) {
                throw ContentDownloaderException("Couldn't find any depots to download for app $appId")
            }

            if (depotIdsFound.size < depotIdsExpected.size) {
                val remainingDepotIds = depotIdsExpected.subtract(depotIdsFound.toSet())
                throw ContentDownloaderException("Depot ${remainingDepotIds.joinToString(", ")} not listed for app $appId")
            }
        }

        val infos = mutableListOf<DepotDownloadInfo>()

        depotManifestIds.forEach { (depotId, manifestId) ->
            val info = getDepotInfo(depotId, appId, manifestId, branch)
            if (info != null) {
                infos.add(info)
            }
        }

        downloadSteam3(infos)
    }

    private suspend fun getDepotInfo(
        depotId: Int,
        appId: Int,
        manifestId: Long,
        branch: String,
    ): DepotDownloadInfo? {
        var manifestId = manifestId
        var branch = branch

        if (appId != INVALID_APP_ID) {
            requestAppInfo(appId)
        }

        if (!accountHasAccess(appId, depotId)) {
            logger?.error("Depot $depotId is not available from this account.")
            return null
        }

        if (manifestId == INVALID_MANIFEST_ID) {
            manifestId = getSteam3DepotManifest(depotId, appId, branch)

            if (manifestId == INVALID_MANIFEST_ID && !branch.equals(DEFAULT_BRANCH, true)) {
                logger?.error("Warning: Depot $depotId does not have branch named \"$branch\". Trying $DEFAULT_BRANCH branch.")
                branch = DEFAULT_BRANCH
                manifestId = getSteam3DepotManifest(depotId, appId, branch)
            }

            if (manifestId == INVALID_MANIFEST_ID) {
                logger?.error("Depot $depotId missing public subsection or manifest section.")
                return null
            }
        }

        requestDepotKey(depotId, appId)

        val depotKey = depotKeys[depotId]
        if (depotKey == null) {
            logger?.error("No valid depot key for $depotId, unable to download.")
            return null
        }

        val uVersion = getSteam3AppBuildNumber(appId, branch)

        val result = createDirectories(depotId, uVersion)
        if (result == null) {
            logger?.error("Error: Unable to create install directories!")
            return null
        }

        // For depots that are proxied through depotfromapp, we still need to resolve the proxy app id, unless the app is freetodownload
        var containingAppId = appId
        val proxyAppId = getSteam3DepotProxyAppId(depotId, appId)
        if (proxyAppId != INVALID_APP_ID) {
            val common = getSteam3AppSection(appId, EAppInfoSection.Common)
            if (common == null || !common["FreeToDownload"].asBoolean()) {
                containingAppId = proxyAppId
            }
        }

        return DepotDownloadInfo(depotId, containingAppId, manifestId, branch, result, depotKey)
    }

    private suspend fun getSteam3DepotManifest(
        depotId: Int,
        appId: Int,
        branch: String,
    ): Long {
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)
        var depotChild = depots?.get(depotId.toString()) ?: KeyValue.INVALID

        if (depotChild == KeyValue.INVALID) {
            return INVALID_MANIFEST_ID
        }

        // Shared depots can either provide manifests, or leave you relying on their parent app.
        // It seems that with the latter, "sharedinstall" will exist (and equals 2 in the one existance I know of).
        // Rather than relay on the unknown sharedinstall key, just look for manifests. Test cases: 111710, 346680.
        if (depotChild["manifests"] == KeyValue.INVALID && depotChild["depotfromapp"] != KeyValue.INVALID) {
            val otherAppId = depotChild["depotfromapp"].asInteger()
            if (otherAppId == appId) {
                // This shouldn't ever happen, but ya never know with Valve. Don't infinite loop.
                logger?.error("App $appId, Depot $depotId has depotfromapp of $otherAppId!")
                return INVALID_MANIFEST_ID
            }

            requestAppInfo(otherAppId)

            return getSteam3DepotManifest(depotId, otherAppId, branch)
        }

        var manifests = depotChild["manifests"]

        if (manifests.children.isEmpty()) {
            return INVALID_MANIFEST_ID
        }

        var node = manifests[branch]["gid"]

        // Non passworded branch, found the manifest
        if (node.value != null) {
            return node.value!!.toLong()
        }

        // If we requested public branch, and it had no manifest, nothing to do
        if (branch.equals(DEFAULT_BRANCH, true)) {
            return INVALID_MANIFEST_ID
        }

        // Either the branch just doesn't exist, or it has a password
        if (config.betaPassword.isNullOrBlank()) {
            logger?.error("Branch $branch for depot $depotId was not found, either it does not exist or it has a password.")
            return INVALID_MANIFEST_ID
        }

        if (!appBetaPasswords.contains(branch)) {
            // Submit the password to Steam now to get encryption keys
            checkAppBetaPassword(appId, config.betaPassword!!)

            if (!appBetaPasswords.containsKey(branch)) {
                logger?.error("Error: Password was invalid for branch $branch (or the branch does not exist)")
                return INVALID_MANIFEST_ID
            }
        }

        // Got the password, request private depot section
        // TODO: (SK) We're probably repeating this request for every depot?
        val privateDepotSection = getPrivateBetaDepotSection(appId, branch)

        // Now repeat the same code to get the manifest gid from depot section
        depotChild = privateDepotSection[depotId.toString()]

        if (depotChild == KeyValue.INVALID) {
            return INVALID_MANIFEST_ID
        }

        manifests = depotChild["manifests"]

        if (manifests.children.isEmpty()) {
            return INVALID_MANIFEST_ID
        }

        node = manifests[branch]["gid"]

        if (node.value == null) {
            return INVALID_MANIFEST_ID
        }

        return node.value!!.toLong()
    }

    private fun getSteam3AppBuildNumber(appId: Int, branch: String): Int {
        if (appId == INVALID_APP_ID) {
            return 0
        }

        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID
        val branches = depots["branches"]
        val node = branches[branch]

        if (node == KeyValue.INVALID) {
            return 0
        }

        val buildId = node["buildid"]

        if (buildId == KeyValue.INVALID) {
            return 0
        }

        return buildId.value!!.toInt()
    }

    private fun getSteam3DepotProxyAppId(depotId: Int, appId: Int): Int {
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID
        val depotChild = depots[depotId.toString()]

        if (depotChild == KeyValue.INVALID) {
            return INVALID_APP_ID
        }

        if (depotChild["depotfromapp"] == KeyValue.INVALID) {
            return INVALID_APP_ID
        }

        return depotChild["depotfromapp"].asInteger()
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
                val appIds = pkg.keyValues["appids"].children.map { it.asInteger() }
                val depotIds = pkg.keyValues["depotids"].children.map { it.asInteger() }
                if (depotId in appIds) {
                    return true
                }
                if (depotId in depotIds) {
                    return true
                }
            }
        }

        // Check if this app is free to download without a license
        val info = getSteam3AppSection(appId, EAppInfoSection.Common)

        return info != null && info["FreeToDownload"].asBoolean()
    }

    private suspend fun downloadSteam3(depots: List<DepotDownloadInfo>): Unit = coroutineScope {
        // TODO Indeterminate progress info

        cdnClientPool?.updateServerList()

        val downloadCounter = GlobalDownloadCounter()
        val depotsToDownload = ArrayList<DepotFilesData>(depots.size)
        val allFileNamesAllDepots = hashSetOf<String>()

        // First, fetch all the manifests for each depot (including previous manifests) and perform the initial setup
        depots.forEach { depot ->
            val depotFileData = processDepotManifestAndFiles(depot, downloadCounter)

            if (depotFileData != null) {
                depotsToDownload.add(depotFileData)
                allFileNamesAllDepots.union(depotFileData.allFileNames)
            }

            ensureActive()
        }

        // If we're about to write all the files to the same directory, we will need to first de-duplicate any files by path
        // This is in last-depot-wins order, from Steam or the list of depots supplied by the user
        if (config.installPath != null && depotsToDownload.isNotEmpty()) {
            val claimedFileNames = mutableSetOf<String>()
            for (i in depotsToDownload.indices.reversed()) {
                // For each depot, remove all files from the list that have been claimed by a later depot
                depotsToDownload[i].filteredFiles.removeAll { file -> file.fileName in claimedFileNames }
                claimedFileNames.addAll(depotsToDownload[i].allFileNames)
            }
        }

        depotsToDownload.forEach { depotFileData ->
            downloadSteam3DepotFiles(downloadCounter, depotFileData, allFileNamesAllDepots)
        }

        logger?.debug(
            "Total downloaded: ${downloadCounter.totalBytesCompressed} bytes " +
                "(${downloadCounter.totalBytesUncompressed} bytes uncompressed) from ${depots.size} depots"
        )
    }

    private suspend fun processDepotManifestAndFiles(
        depot: DepotDownloadInfo,
        downloadCounter: GlobalDownloadCounter,
    ): DepotFilesData? = withContext(Dispatchers.IO) {
        val depotCounter = DepotDownloadCounter()

        logger?.debug("Processing depot ${depot.depotId}")

        var oldManifest: DepotManifest? = null
        var newManifest: DepotManifest? = null
        val configDir = depot.installDir / CONFIG_DIR

        var lastManifestId = INVALID_MANIFEST_ID
        lastManifestId = DepotConfigStore.getInstance().installedManifestIDs[depot.depotId] ?: INVALID_MANIFEST_ID

        // In case we have an early exit, this will force equiv of verifyall next run.
        DepotConfigStore.getInstance().installedManifestIDs[depot.depotId] = INVALID_MANIFEST_ID
        DepotConfigStore.save()

        if (lastManifestId != INVALID_MANIFEST_ID) {
            // We only have to show this warning if the old manifest ID was different
            val badHashWarning = lastManifestId != depot.manifestId
            oldManifest = Util.loadManifestFromFile(configDir, depot.depotId, lastManifestId, badHashWarning)
        }

        if (lastManifestId == depot.manifestId && oldManifest != null) {
            newManifest = oldManifest
            logger?.debug("Already have manifest ${depot.manifestId} for depot ${depot.depotId}.")
        } else {
            newManifest = Util.loadManifestFromFile(configDir, depot.depotId, depot.manifestId, true)

            if (newManifest != null) {
                logger?.debug("Already have manifest ${depot.manifestId} for depot ${depot.depotId}.")
            } else {
                logger?.debug("Downloading depot ${depot.depotId} manifest")

                var manifestRequestCode: Long = 0
                var manifestRequestCodeExpiration = Instant.MIN

                do {
                    ensureActive()

                    var connection: Server? = null

                    try {
                        connection = cdnClientPool!!.getConnection()

                        var cdnToken: String? = null

                        val authTokenCallbackPromise = cdnAuthTokens[depot.depotId to connection.host]
                        if (authTokenCallbackPromise != null) {
                            val result = authTokenCallbackPromise.await()
                            cdnToken = result.token
                        }

                        val now = Instant.now()

                        // In order to download this manifest, we need the current manifest request code
                        // The manifest request code is only valid for a specific period in time
                        if (manifestRequestCode == 0L || now >= manifestRequestCodeExpiration) {
                            manifestRequestCode = getDepotManifestRequestCode(
                                depotId = depot.depotId,
                                appId = depot.appId,
                                manifestId = depot.manifestId,
                                branch = depot.branch,
                            )

                            // This code will hopefully be valid for one period following the issuing period
                            manifestRequestCodeExpiration = now.plus(5, ChronoUnit.MINUTES)

                            // If we could not get the manifest code, this is a fatal error
                            if (manifestRequestCode == 0L) {
                                cancel()
                            }
                        }

                        logger?.debug("Downloading manifest ${depot.manifestId} from $connection with ${cdnClientPool!!.proxyServer ?: "no proxy"}")

                        newManifest = cdnClientPool!!.cdnClient!!.downloadManifest(
                            depotId = depot.depotId,
                            manifestId = depot.manifestId,
                            manifestRequestCode = manifestRequestCode,
                            server = connection,
                            depotKey = depot.depotKey,
                            proxyServer = cdnClientPool!!.proxyServer,
                            cdnAuthToken = cdnToken,
                        )

                        cdnClientPool!!.returnConnection(connection)
                    } catch (e: CancellationException) {
                        // logger?.error("Connection timeout downloading depot manifest ${depot.depotId} ${depot.manifestId}. Retrying.")
                        logger?.error(e)
                        break
                    } catch (e: SteamKitWebRequestException) {
                        // If the CDN returned 403, attempt to get a cdn auth if we didn't yet
                        if (e.statusCode == 403 && cdnAuthTokens.containsKey(depot.depotId to connection!!.host)) {
                            requestCDNAuthToken(depot.appId, depot.depotId, connection)

                            cdnClientPool!!.returnConnection(connection)

                            continue
                        }

                        cdnClientPool!!.returnBrokenConnection(connection)

                        // Unauthorized || Forbidden
                        if (e.statusCode == 401 || e.statusCode == 403) {
                            logger?.error("Encountered ${depot.depotId} for depot manifest ${depot.manifestId} ${e.statusCode}. Aborting.")
                            break
                        }

                        // NotFound
                        if (e.statusCode == 404) {
                            logger?.error("Encountered 404 for depot manifest ${depot.depotId} ${depot.manifestId}. Aborting.")
                            break
                        }

                        logger?.error("Encountered error downloading depot manifest ${depot.depotId} ${depot.manifestId}: ${e.statusCode}")
                    } catch (e: Exception) {
                        cdnClientPool!!.returnBrokenConnection(connection)
                        logger?.error("Encountered error downloading manifest for depot ${depot.depotId} ${depot.manifestId}: ${e.message}")
                    }
                } while (newManifest == null)

                if (newManifest == null) {
                    logger?.error("\nUnable to download manifest ${depot.manifestId} for depot ${depot.depotId}")
                    cancel()
                }

                // Throw the cancellation exception if requested so that this task is marked failed
                ensureActive()

                Util.saveManifestToFile(configDir, newManifest!!)
            }
        }

        logger?.debug("Manifest ${depot.manifestId} (${newManifest!!.creationTime})")

        if (config.downloadManifestOnly) {
            Util.dumpManifestToTextFile(depot, newManifest)
            return@withContext null
        }

        val stagingDir = depot.installDir / STAGING_DIR

        val filesAfterExclusions = coroutineScope {
            newManifest.files.filter { file ->
                async { testIsFileIncluded(file.fileName) }.await()
            }
        }
        val allFileNames = HashSet<String>(filesAfterExclusions.size)

        // Pre-process
        filesAfterExclusions.forEach { file ->
            allFileNames.add(file.fileName)

            val fileFinalPath = depot.installDir / file.fileName
            val fileStagingPath = stagingDir / file.fileName

            if (file.flags.contains(EDepotFileFlag.Directory)) {
                filesystem.createDirectories(fileFinalPath)
                filesystem.createDirectories(fileStagingPath)
            } else {
                // Some manifests don't explicitly include all necessary directories
                filesystem.createDirectories(fileFinalPath.parent!!)
                filesystem.createDirectories(fileStagingPath.parent!!)

                downloadCounter.completeDownloadSize += file.totalSize
                depotCounter.completeDownloadSize += file.totalSize
            }
        }

        return@withContext DepotFilesData(
            depotDownloadInfo = depot,
            depotCounter = depotCounter,
            stagingDir = stagingDir,
            manifest = newManifest,
            previousManifest = oldManifest,
            filteredFiles = filesAfterExclusions.toMutableList(),
            allFileNames = allFileNames,
        )
    }

    private suspend fun downloadSteam3DepotFiles(
        downloadCounter: GlobalDownloadCounter,
        depotFilesData: DepotFilesData,
        allFileNamesAllDepots: HashSet<String>,
    ) {
        TODO()
    }

    private fun testIsFileIncluded(filename: String): Boolean {
        if (!config.usingFileList) {
            return true
        }

        val normalizedFilename = filename.replace('\\', '/')

        if (normalizedFilename in config.filesToDownload) {
            return true
        }

        for (regex in config.filesToDownloadRegex) {
            if (regex.matches(normalizedFilename)) {
                return true
            }
        }

        return false
    }

    // endregion

    // region [REGION] Listener Operations

    fun addListener(listener: IDownloadListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: IDownloadListener) {
        listeners.remove(listener)
    }

    // endregion

    // region [REGION] Array Operations

    fun getItems(): List<DownloadItem> = items.toList()

    fun size(): Int = items.size

    fun isEmpty(): Boolean = items.isEmpty()

    fun addAll(items: List<DownloadItem>) {
        items.forEach(::add)
    }

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

    /**
     * Android emulation uses Windows, so this method sets internal configs to return "windows" if the device is android.
     */
    fun setAndroidEmulation(value: Boolean) {
        config = config.copy(androidEmulation = true)
    }

    fun start(): CompletableFuture<Boolean> = scope.future {
        if (isStarted.get()) {
            logger?.debug("Downloading already started.")
            return@future false
        }

        isStarted.set(true)

        val initialItems = items.toList()

        if (initialItems.isEmpty()) {
            logger?.debug("No items to download")
            return@future false
        }

        initialItems.forEach { processingChannel.send(it) }

        val downloadJobs = mutableListOf<Job>()

        // TODO revert back to suspending loop incase more is added in the Channel.
        repeat(initialItems.size) {
            ensureActive()

            if (!isStarted.get()) {
                return@future false
            }

            val item = processingChannel.receive()

            val job = scope.launch {
                try {
                    when (item) {
                        is PubFileItem -> {
                            if (item.pubfile == INVALID_MANIFEST_ID) {
                                logger?.debug("Invalid Pub File ID for ${item.appId}")
                                return@launch
                            }
                            logger?.debug("Downloading PUB File for ${item.appId}")
                            config = config.copy(downloadManifestOnly = item.downloadManifestOnly)
                            downloadPubFile(item.appId, item.pubfile)
                        }

                        is UgcItem -> {
                            if (item.ugcId == INVALID_MANIFEST_ID) {
                                logger?.debug("Invalid UGC ID for ${item.appId}")
                                return@launch
                            }
                            logger?.debug("Downloading UGC File for ${item.appId}")
                            config = config.copy(downloadManifestOnly = item.downloadManifestOnly)
                            downloadUGC(item.appId, item.ugcId)
                        }

                        is AppItem -> {
                            logger?.debug("Downloading App for ${item.appId}")

                            val branch = item.branch ?: DEFAULT_BRANCH
                            config = config.copy(betaPassword = item.branchPassword)

                            if (!config.betaPassword.isNullOrBlank() && branch.isBlank()) {
                                logger?.error("Error: Cannot specify 'branchpassword' when 'branch' is not specified.")
                                return@launch
                            }

                            config = config.copy(downloadAllPlatforms = item.downloadAllPlatforms)

                            val os = item.os

                            if (config.downloadAllPlatforms && !os.isNullOrBlank()) {
                                logger?.error("Error: Cannot specify `os` when `all-platforms` is specified.")
                                return@launch
                            }

                            config = config.copy(downloadAllArchs = item.downloadAllArchs)

                            val arch = item.osArch

                            if (config.downloadAllArchs && !arch.isNullOrBlank()) {
                                logger?.error("Error: Cannot specify `osarch` when `all-archs` is specified.")
                                return@launch
                            }

                            config = config.copy(downloadAllLanguages = item.downloadAllLanguages)

                            val language = item.language

                            if (config.downloadAllLanguages && !language.isNullOrBlank()) {
                                logger?.error("Error: Cannot specify `language` when `all-languages` is specified.")
                                return@launch
                            }

                            val lv = item.lowViolence

                            val depotManifestIds = mutableListOf<Pair<Int, Long>>()
                            val isUGC = false

                            val depotIdList = item.depot
                            val manifestIdList = item.manifest

                            if (manifestIdList.isNotEmpty()) {
                                if (depotIdList.size != manifestIdList.size) {
                                    logger?.error("Error: `manifest` requires one id for every `depot` specified")
                                    return@launch
                                }
                                val zippedDepotManifest = depotIdList.zip(manifestIdList) { depotId, manifestId ->
                                    Pair(depotId, manifestId)
                                }
                                depotManifestIds.addAll(zippedDepotManifest)
                            } else {
                                depotManifestIds.addAll(
                                    depotIdList.map { depotId ->
                                        Pair(depotId, INVALID_MANIFEST_ID)
                                    }
                                )
                            }

                            config = config.copy(downloadManifestOnly = item.downloadManifestOnly)

                            downloadApp(
                                appId = item.appId,
                                depotManifestIds = depotManifestIds,
                                branch = branch,
                                os = os,
                                arch = arch,
                                language = language,
                                lv = lv,
                                isUgc = isUGC,
                            )
                        }
                    }
                } catch (e: Exception) {
                    logger?.error("Error downloading item ${item.appId}: ${e.message}", e)
                    throw e
                }
            }

            downloadJobs.add(job)
        }

        downloadJobs.joinAll()
        return@future true
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

    private fun notifyListeners(action: (IDownloadListener) -> Unit) {
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
