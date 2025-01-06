package `in`.dragonbra.javasteam.steam.contentdownloader

import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.CDNAuthToken
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PubFile
import `in`.dragonbra.javasteam.types.PubFile.PublishedFileID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import jdk.javadoc.internal.doclets.formats.html.markup.HtmlStyle.details
import kotlinx.coroutines.future.await
import kotlinx.coroutines.future.future
import okhttp3.Request
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.mutableMapOf

class ContentDownloader(val steamClient: SteamClient) {

    companion object {
        private val logger: Logger = LogManager.getLogger(ContentDownloader::class.java)

        enum class HTTP(val code: Int, val text: String) {
            UNAUTHORIZED(401, "Unauthorized"),
            FORBIDDEN(403, "Forbidden"),
            NOT_FOUND(404, "Not Found"),
            SERVICE_UNAVAILABLE(503, "Service Unavailable"),
        }

        const val DEFAULT_BRANCH: String = "public"

        internal const val INVALID_APP_ID: Int = Int.MAX_VALUE
        internal const val INVALID_DEPOT_ID: Int = Int.MAX_VALUE
        internal const val INVALID_MANIFEST_ID: Long = Long.MAX_VALUE

        @JvmStatic
        fun getSteamOS() : String {

        }
    }

    data class DepotManifestIds(val depotId: Int, val manifestId: Long)

    var appTokens: MutableMap<Int, Long> = mutableMapOf()
        private set

    var depotKeys: MutableMap<Int, ByteArray> = mutableMapOf()
        private set

    var appInfo: MutableMap<Int, PICSProductInfo?> = mutableMapOf()
        private set

    var packageInfo: MutableMap<Int, PICSProductInfo?> = mutableMapOf()
        private set

    var cdnAuthTokens: ConcurrentHashMap<Pair<Int, String>, CompletableFuture<CDNAuthToken>> = ConcurrentHashMap()
        private set

    var appBetaPasswords: MutableMap<String, ByteArray> = mutableMapOf()
        private set

    var licenses: List<License> = listOf()

    fun createDirectories(
        depotId: Int,
        depotVersion: Int,
    ): Pair<Boolean, String?> {
        TODO("Not Impl.")
    }

    fun testIsFileIncluded(fileName: String): Boolean {
        TODO("Not Impl")
    }

    fun accountHasAccess(
        appId: Int,
        depotId: Int,
    ): CompletableFuture<Boolean> = steamClient.defaultScope.future {
        if (steamClient.steamID == null ||
            (licenses.isEmpty() && steamClient.steamID.accountType != EAccountType.AnonUser)
        ) {
            return@future false
        }

        val licenseQuery = mutableListOf<Int>()
        if (steamClient.steamID.accountType == EAccountType.AnonUser) {
            licenseQuery.add(17906)
        } else {
            licenses.map { it.packageID }.distinct().also(licenseQuery::addAll)
        }

        requestPackageInfo(licenseQuery, licenses)

        licenses.forEach { license ->
            val pkg = packageInfo[license.packageID]
            if (pkg != null) {
                if (pkg.keyValues["appids"].children.any { child -> child.asInteger() == depotId }) {
                    return@future true
                }
                if (pkg.keyValues["depotids"].children.any { child -> child.asInteger() == depotId }) {
                    return@future true
                }
            }
        }

        // Check if this app is free to download without a license
        val info = getSteam3AppSection(appId, EAppInfoSection.Common)
        if (info != null && info["FreeToDownload"].asBoolean()) {
            return@future true
        }

        return@future true
    }

    internal fun getSteam3AppSection(appId: Int, section: EAppInfoSection): KeyValue? {
        if (appInfo.isEmpty()) {
            return null
        }

        val app = appInfo[appId]
        if (app == null) {
            return null
        }

        val appInfo = app.keyValues
        val sectionKey = when (section) {
            EAppInfoSection.Common -> "common"
            EAppInfoSection.Extended -> "extended"
            EAppInfoSection.Config -> "config"
            EAppInfoSection.Depots -> "depots"
            else -> throw IllegalStateException("Unknown app section $section")
        }

        val sectionKv = appInfo.children.firstOrNull { c -> c.name == sectionKey }

        return sectionKv
    }

    fun getSteam3AppBuildNumber(appId: Int, branch: String): Int {
        if (appId == INVALID_APP_ID) {
            return 0
        }

        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID
        val branches = depots["branches"] ?: KeyValue.INVALID
        val node = branches[branch] ?: KeyValue.INVALID

        if (node == KeyValue.INVALID) {
            return 0
        }

        val buildid = node["buildid"]

        if (buildid == KeyValue.INVALID) {
            return 0
        }

        return buildid.value.toInt()
    }

    fun getSteam3DepotProxyAppId(depotId: Int, appId: Int): Int {
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID
        val depotChild = depots[depotId.toString()] ?: KeyValue.INVALID

        if (depotChild == KeyValue.INVALID) {
            return INVALID_APP_ID
        }

        if (depotChild["depotfromapp"] == KeyValue.INVALID) {
            return INVALID_APP_ID
        }

        return depotChild["depotfromapp"].value.toInt()
    }

    fun getSteam3DepotManifest() = steamClient.defaultScope.future {
        TODO("Not Impl")
    }

    fun getAppName(appId: Int): String {
        val info = getSteam3AppSection(appId, EAppInfoSection.Common)

        if (info == null) {
            return ""
        }

        return info["name"].asString()
    }

    fun downloadPubfileAsync(appId: Int, publishedFileId: Long) = steamClient.defaultScope.future {
        val details = getPublishedFileDetails(appId, PublishedFileID(publishedFileId))

        requireNotNull(details)

        if (!details.fileUrl.isNullOrEmpty()) {
            downloadWebFile(appId, details.filename, details.fileUrl)
        } else if (details.hcontentFile > 0) {
            downloadAppAsync(
                appId = appId,
                depotManifestIds = listOf(DepotManifestIds(appId, details.hcontentFile)),
                branch = DEFAULT_BRANCH,
                os = null,
                arch = null,
                language = null,
                lv = false,
                isUgc = true
            )
        } else {
            logger.debug("Unable to locate manifest ID for published file $publishedFileId")
        }
    }

    fun downloadAppAsync(
        appId: Int,
        depotManifestIds: List<DepotManifestIds>,
        branch: String,
        os: String?,
        arch: String?,
        language: String?,
        lv: Boolean,
        isUgc: Boolean
    ) = steamClient.defaultScope.future {
        var depotManifestIds = depotManifestIds
        val cdnPool = CDNClientPool(steamClient, appId, this)

        requestAppInfo(appId).await()

        if (accountHasAccess(appId, appId).await()) {
            if (requestFreeAppLicense(appId).await()) {
                logger.debug("Obtained FreeOnDemand license for app $appId")

                // Fetch app info again in case we didn't get it fully without a license.
                requestAppInfo(appId)
            } else {
                val contentName = getAppName(appId)
                throw Exception("App $appId ($contentName) is not available from this account.")
            }
        }

        val hasSpecificDepots = depotManifestIds.isNotEmpty()
        val depotIdsFound = mutableListOf<Int>()
        val depotIdsExpected = depotManifestIds.map { it.depotId }.toMutableList()
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)

        if (isUgc) {
            val workshopDepot = depots?.get("workshopdepot")?.asInteger() ?: 0
            if (workshopDepot != 0 && !depotIdsExpected.contains(workshopDepot)) {
                depotIdsExpected.add(workshopDepot)
                depotManifestIds = depotManifestIds.map { pair -> DepotManifestIds(workshopDepot, pair.manifestId) }
            }

            depotIdsFound.addAll(depotIdsExpected)
        } else {
            logger.debug("Using app branch: $branch")

            if (depots != null) {
                depots.children.forEach { depotSection ->
                    var id = INVALID_DEPOT_ID

                    if (depotSection.children.isEmpty()) {
                        return@forEach
                    }

                    if (depotSection.name.toIntOrNull() == null) {
                        return@forEach
                    }

                    if (hasSpecificDepots && !depotIdsExpected.contains(id)) {
                        return@forEach
                    }

                    if(!hasSpecificDepots) {
                        val depotConfig = depotSection["config"]
                        if(depotConfig != KeyValue.INVALID) {

                        }
                    }
                }
            }
        }
    }

    private suspend fun requestPackageInfo(packageIds: List<Int>, licenses: List<License>) {
        var packages = packageIds.toMutableList()
        packages.removeAll { packageInfo.containsKey(it) }

        if (packages.isEmpty()) {
            return
        }

        val packageRequests = mutableListOf<PICSRequest>()

        packageIds.forEach { pkg ->
            val request = PICSRequest(pkg)

            licenses.find { it.packageID == pkg }?.let {
                request.accessToken = it.accessToken
            }

            packageRequests.add(request)
        }

        val packageInfoMultiple = steamClient.getHandler<SteamApps>()!!
            .picsGetProductInfo(listOf(), packageRequests).await()

        packageInfoMultiple.results.forEach { pkgInfo ->
            pkgInfo.packages.forEach { packageValue ->
                val pkg = packageValue.value
                packageInfo[pkg.id] = pkg
            }

            pkgInfo.unknownPackages.forEach { pkg ->
                packageInfo[pkg] = null
            }
        }
    }

    private suspend fun getPublishedFileDetails(appId: Int, pubFile: PubFile.PublishedFileID): PublishedFileDetails? {
        var pubFileRequest = CPublishedFile_GetDetails_Request.newBuilder().apply {
            this.appid = appId
        }.build()

        val unifiedMessages = steamClient.getHandler(SteamUnifiedMessages::class.java)
            ?: throw NullPointerException("Unable to get SteamUnifiedMessages handler")
        val steamPublishedFile = unifiedMessages.createService<PublishedFile>()

        val details = steamPublishedFile.getDetails(pubFileRequest).await()

        if (details.result == EResult.OK) {
            return details.body.publishedfiledetailsList.firstOrNull()
        }

        throw Exception("EResult ${details.result.code()} (${details.result}) while retrieving file details for pubfile $pubFile.")
    }

    private suspend fun downloadWebFile(appId: Int, fileName: String, url: String) {

        val dir = createDirectories(appId, 0)
        if (dir.first == false || dir.second == null) {
            logger.debug("Unable to create install directories")
            return
        }

        val stagingDir = File(dir.second, STAGING_DIR).path
        val fileStagingPath = File(stagingDir, fileName).path
        val fileFinalPath = File(dir.second, fileName).path

        // Create directories for both paths
        File(fileFinalPath).parentFile?.mkdirs()
        File(fileStagingPath).parentFile?.mkdirs()

        val request = Request.Builder()
            .url(url)
            .build()

        steamClient.configuration.httpClient.newCall(request).execute().use { response ->
            File(fileStagingPath).outputStream().use { fileOutput ->
                response.body.byteStream().use { inputStream ->
                    inputStream.copyTo(fileOutput)
                }
            }
        }

        val finalFile = File(fileFinalPath)
        if (finalFile.exists()) {
            finalFile.delete()
        }

        Files.move(
            File(fileStagingPath).toPath(),
            finalFile.toPath(),
            StandardCopyOption.REPLACE_EXISTING
        )
    }

    fun requestAppInfo(appId: Int) = steamClient.defaultScope.future {
        val steamApps = steamClient.getHandler<SteamApps>()
            ?: throw NullPointerException("Unable to get SteamApps handler")

        val appTokens = steamApps.picsGetAccessTokens(appId, null).await()

        if (appTokens.appTokensDenied.contains(appId)) {
            logger.debug("Insufficient privileges to get access token for app $appId")
        }

        appTokens.appTokens.forEach { tokenDict ->
            this@ContentDownloader.appTokens[tokenDict.key] = tokenDict.value
        }

        val request = PICSRequest(appId)

        this@ContentDownloader.appTokens[appId]?.let {
            request.accessToken = it
        }

        val appInfoMultiple = steamApps.picsGetProductInfo(request).await()

        appInfoMultiple.results.forEach { appInfo ->
            appInfo.apps.forEach { appValue ->
                val app = appValue.value

                logger.debug("Got info for ${app.id}")
                this@ContentDownloader.appInfo[app.id] = app
            }

            appInfo.unknownPackages.forEach { app ->
                this@ContentDownloader.appInfo[app] = null
            }
        }
    }

    fun requestFreeAppLicense(appId: Int): CompletableFuture<Boolean> = steamClient.defaultScope.future {
        val steamApps = steamClient.getHandler<SteamApps>()
            ?: throw NullPointerException("Unable to get SteamApps handler")

        val resultInfo = steamApps.requestFreeLicense(appId).await()

        return@future resultInfo.grantedApps.contains(appId)
    }

    @JvmOverloads
    fun requestDepotKey(depotId: Int, appId: Int = 0) = steamClient.defaultScope.future {
        if (depotKeys.containsKey(depotId)) {
            return@future
        }

        val steamApps = steamClient.getHandler<SteamApps>()
            ?: throw NullPointerException("Unable to get SteamApps handler")

        val depotKey = steamApps.getDepotDecryptionKey(depotId, appId).await()

        logger.debug("Got depot key for ${depotKey.depotKey} result: ${depotKey.result}")

        if (depotKey.result != EResult.OK) {
            return@future
        }

        depotKeys[depotKey.depotID] = depotKey.depotKey
    }

    fun getDepotManifestRequestCodeAsync(
        depotId: Int,
        appId: Int,
        manifestId: Long,
        branch: String
    ): CompletableFuture<Long> = steamClient.defaultScope.future {
        val steamContent = steamClient.getHandler<SteamContent>()
            ?: throw NullPointerException("Unable to get SteamContent handler")

        val requestCode = steamContent.getManifestRequestCode(depotId, appId, manifestId, branch, null, this).await()

        if (requestCode == 0L) {
            logger.debug("No manifest request code was returned for depot $depotId from app $appId, manifest $manifestId")
        } else {
            logger.debug("Got manifest request code for depot $depotId from app $appId, manifest $manifestId, result: $requestCode")
        }

        return@future requestCode
    }

    fun requestCDNAuthToken(
        appId: Int,
        depotId: Int,
        server: Server
    ): CompletableFuture<Unit> = steamClient.defaultScope.future {
        val cdnKey = Pair(depotId, server.host)

        val completion = CompletableFuture<CDNAuthToken>()
        cdnAuthTokens[cdnKey] = completion

        logger.debug("Requesting CDN auth token for ${server.host}")

        val steamContent = steamClient.getHandler<SteamContent>()
            ?: throw NullPointerException("Unable to get SteamContent handler")

        var cdnAuth = steamContent.getCDNAuthToken(appId, depotId, server.host, this).await()

        logger.debug("Got CDN auth token for ${server.host} result: ${cdnAuth.result} (expires ${cdnAuth.expiration})")

        if (cdnAuth.result != EResult.OK) {
            return@future
        }

        completion.complete(cdnAuth)
    }

    fun checkAppBetaPassword(appId: Int, password: String) = steamClient.defaultScope.future {
        val steamApps = steamClient.getHandler<SteamApps>()
            ?: throw NullPointerException("Unable to get SteamApps handler")

        val appPassword = steamApps.checkAppBetaPassword(appId, password).await()

        logger.debug("Retrieved ${appPassword.betaPasswords.size} beta keys with result: ${appPassword.result}")

        appPassword.betaPasswords.forEach { entry ->
            appBetaPasswords[entry.key] = entry.value
        }
    }

    fun getUGCDetails(ugcHandle: UGCHandle) = steamClient.defaultScope.future {
        val steamCloud = steamClient.getHandler<SteamCloud>()
            ?: throw NullPointerException("Unable to get SteamApps handler")

        val callback = steamCloud.requestUGCDetails(ugcHandle).await()

        if (callback.result == EResult.OK) {
            return@future callback
        } else {
            return@future null
        }

        throw Exception("EResult ${callback.result.code()} (${callback.result}) while retrieving UGC details for $ugcHandle.");
    }

//    private fun getDepotManifestId(
//        app: PICSProductInfo,
//        depotId: Int,
//        branchId: String,
//        parentScope: CoroutineScope,
//    ): Deferred<Pair<Int, Long>> = parentScope.async {
//        val depot = app.keyValues["depots"][depotId.toString()]
//        if (depot == KeyValue.INVALID) {
//            logger.error("Could not find depot $depotId of ${app.id}")
//            return@async Pair(app.id, INVALID_MANIFEST_ID)
//        }
//
//        val manifest = depot["manifests"][branchId]
//        if (manifest != KeyValue.INVALID) {
//            return@async Pair(app.id, manifest["gid"].asLong())
//        }
//
//        val depotFromApp = depot["depotfromapp"].asInteger(INVALID_APP_ID)
//        if (depotFromApp == app.id || depotFromApp == INVALID_APP_ID) {
//            logger.error("Failed to find manifest of app ${app.id} within depot $depotId on branch $branchId")
//            return@async Pair(app.id, INVALID_MANIFEST_ID)
//        }
//
//        val innerApp = getAppInfo(depotFromApp, parentScope).await()
//        if (innerApp == null) {
//            logger.error("Failed to find manifest of app ${app.id} within depot $depotId on branch $branchId")
//            return@async Pair(app.id, INVALID_MANIFEST_ID)
//        }
//
//        return@async getDepotManifestId(innerApp, depotId, branchId, parentScope).await()
//    }
//
//    private fun getAppDirName(app: PICSProductInfo): String {
//        val installDirKeyValue = app.keyValues["config"]["installdir"]
//
//        return if (installDirKeyValue != KeyValue.INVALID) installDirKeyValue.value else app.id.toString()
//    }
//
//    private fun getAppInfo(
//        appId: Int,
//        parentScope: CoroutineScope,
//    ): Deferred<PICSProductInfo?> = parentScope.async {
//        val steamApps = steamClient.getHandler(SteamApps::class.java)
//        val callback = steamApps?.picsGetProductInfo(PICSRequest(appId))?.await()
//        val apps = callback?.results?.flatMap { (it as PICSProductInfoCallback).apps.values }
//
//        if (apps.isNullOrEmpty()) {
//            logger.error("Received empty apps list in PICSProductInfo response for $appId")
//            return@async null
//        }
//
//        if (apps.size > 1) {
//            logger.debug("Received ${apps.size} apps from PICSProductInfo for $appId, using first result")
//        }
//
//        return@async apps.first()
//    }
//
//    @JvmOverloads
//    fun downloadApp(
//        appId: Int,
//        depotId: Int,
//        installPath: String,
//        stagingPath: String,
//        branch: String = "public",
//        maxDownloads: Int = 8,
//        onDownloadProgress: ProgressCallback? = null,
//        scope: CoroutineScope = steamClient.defaultScope,
//    ): CompletableFuture<Boolean> = scope.future {
//        if (!scope.isActive) {
//            logger.error("App $appId was not completely downloaded. Operation was canceled.")
//            return@future false
//        }
//
//        val cdnPool = CDNClientPool(steamClient, appId, scope)
//
//        val shiftedAppId: Int
//        val manifestId: Long
//        val appInfo = getAppInfo(appId, scope).await()
//
//        if (appInfo == null) {
//            logger.error("Could not retrieve PICSProductInfo of $appId")
//            return@future false
//        }
//
//        getDepotManifestId(appInfo, depotId, branch, scope).await().apply {
//            shiftedAppId = first
//            manifestId = second
//        }
//
//        val depotKeyResult = requestDepotKey(shiftedAppId, depotId, scope).await()
//
//        if (depotKeyResult.first != EResult.OK || depotKeyResult.second == null) {
//            logger.error("Depot key request for $appId failed with result ${depotKeyResult.first}")
//            return@future false
//        }
//
//        val depotKey = depotKeyResult.second!!
//
//        var newProtoManifest = steamClient.configuration.depotManifestProvider.fetchManifest(depotId, manifestId)
//        var oldProtoManifest = steamClient.configuration.depotManifestProvider.fetchLatestManifest(depotId)
//
//        if (oldProtoManifest?.manifestGID == manifestId) {
//            oldProtoManifest = null
//        }
//
//        // In case we have an early exit, this will force equiv of verifyall next run.
//        steamClient.configuration.depotManifestProvider.setLatestManifestId(depotId, INVALID_MANIFEST_ID)
//
//        try {
//            if (newProtoManifest == null) {
//                newProtoManifest =
//                    downloadFilesManifestOf(shiftedAppId, depotId, manifestId, branch, depotKey, cdnPool, scope).await()
//            } else {
//                logger.debug("Already have manifest $manifestId for depot $depotId.")
//            }
//
//            if (newProtoManifest == null) {
//                logger.error("Failed to retrieve files manifest for app: $shiftedAppId depot: $depotId manifest: $manifestId branch: $branch")
//                return@future false
//            }
//
//            if (!scope.isActive) {
//                return@future false
//            }
//
//            val downloadCounter = GlobalDownloadCounter()
//            val installDir = Paths.get(installPath, getAppDirName(appInfo)).toString()
//            val stagingDir = Paths.get(stagingPath, getAppDirName(appInfo)).toString()
//            val depotFileData = DepotFilesData(
//                depotDownloadInfo = DepotDownloadInfo(depotId, shiftedAppId, manifestId, branch, installDir, depotKey),
//                depotCounter = DepotDownloadCounter(
//                    completeDownloadSize = newProtoManifest.totalUncompressedSize
//                ),
//                stagingDir = stagingDir,
//                manifest = newProtoManifest,
//                previousManifest = oldProtoManifest
//            )
//
//            downloadDepotFiles(cdnPool, downloadCounter, depotFileData, maxDownloads, onDownloadProgress, scope).await()
//
//            steamClient.configuration.depotManifestProvider.setLatestManifestId(depotId, manifestId)
//
//            cdnPool.shutdown()
//
//            // delete the staging directory of this app
//            File(stagingDir).deleteRecursively()
//
//            logger.debug(
//                "Depot $depotId - Downloaded ${depotFileData.depotCounter.depotBytesCompressed} " +
//                    "bytes (${depotFileData.depotCounter.depotBytesUncompressed} bytes uncompressed)"
//            )
//
//            return@future true
//        } catch (e: CancellationException) {
//            logger.error("App $appId was not completely downloaded. Operation was canceled.", e)
//
//            return@future false
//        } catch (e: Exception) {
//            logger.error("Error occurred while downloading app $shiftedAppId", e)
//
//            return@future false
//        }
//    }
//
//    private fun downloadDepotFiles(
//        cdnPool: CDNClientPool,
//        downloadCounter: GlobalDownloadCounter,
//        depotFilesData: DepotFilesData,
//        maxDownloads: Int,
//        onDownloadProgress: ProgressCallback? = null,
//        parentScope: CoroutineScope,
//    ) = parentScope.async {
//        if (!parentScope.isActive) {
//            return@async
//        }
//
//        depotFilesData.manifest.files.forEach { file ->
//            val fileFinalPath = Paths.get(depotFilesData.depotDownloadInfo.installDir, file.fileName).toString()
//            val fileStagingPath = Paths.get(depotFilesData.stagingDir, file.fileName).toString()
//
//            if (file.flags.contains(EDepotFileFlag.Directory)) {
//                File(fileFinalPath).mkdirs()
//                File(fileStagingPath).mkdirs()
//            } else {
//                // Some manifests don't explicitly include all necessary directories
//                File(fileFinalPath).parentFile.mkdirs()
//                File(fileStagingPath).parentFile.mkdirs()
//            }
//        }
//
//        logger.debug("Downloading depot ${depotFilesData.depotDownloadInfo.depotId}")
//
//        val files = depotFilesData.manifest.files.filter { !it.flags.contains(EDepotFileFlag.Directory) }.toTypedArray()
//        val networkChunkQueue = ConcurrentLinkedQueue<Triple<FileStreamData, FileData, ChunkData>>()
//
//        val downloadSemaphore = Semaphore(maxDownloads)
//        files.map { file ->
//            async {
//                downloadSemaphore.withPermit {
//                    downloadDepotFile(depotFilesData, file, networkChunkQueue, onDownloadProgress, parentScope).await()
//                }
//            }
//        }.awaitAll()
//
//        networkChunkQueue.map { (fileStreamData, fileData, chunk) ->
//            async {
//                downloadSemaphore.withPermit {
//                    downloadSteam3DepotFileChunk(
//                        cdnPool = cdnPool,
//                        downloadCounter = downloadCounter,
//                        depotFilesData = depotFilesData,
//                        file = fileData,
//                        fileStreamData = fileStreamData,
//                        chunk = chunk,
//                        onDownloadProgress = onDownloadProgress,
//                        parentScope = parentScope
//                    ).await()
//                }
//            }
//        }.awaitAll()
//
//        // Check for deleted files if updating the depot.
//        depotFilesData.previousManifest?.apply {
//            val previousFilteredFiles = files.asSequence().map { it.fileName }.toMutableSet()
//
//            // Of the list of files in the previous manifest, remove any file names that exist in the current set of all file names
//            previousFilteredFiles.removeAll(depotFilesData.manifest.files.map { it.fileName }.toSet())
//
//            for (existingFileName in previousFilteredFiles) {
//                val fileFinalPath = Paths.get(depotFilesData.depotDownloadInfo.installDir, existingFileName).toString()
//
//                if (!File(fileFinalPath).exists()) {
//                    continue
//                }
//
//                File(fileFinalPath).delete()
//                logger.debug("Deleted $fileFinalPath")
//            }
//        }
//    }
//
//    private fun downloadDepotFile(
//        depotFilesData: DepotFilesData,
//        file: FileData,
//        networkChunkQueue: ConcurrentLinkedQueue<Triple<FileStreamData, FileData, ChunkData>>,
//        onDownloadProgress: ProgressCallback? = null,
//        parentScope: CoroutineScope,
//    ) = parentScope.async {
//        if (!isActive) {
//            return@async
//        }
//
//        val depotDownloadCounter = depotFilesData.depotCounter
//        val oldManifestFile = depotFilesData.previousManifest?.files?.find { it.fileName == file.fileName }
//
//        val fileFinalPath = Paths.get(depotFilesData.depotDownloadInfo.installDir, file.fileName).toString()
//        val fileStagingPath = Paths.get(depotFilesData.stagingDir, file.fileName).toString()
//
//        // This may still exist if the previous run exited before cleanup
//        File(fileStagingPath).takeIf { it.exists() }?.delete()
//
//        val neededChunks: MutableList<ChunkData>
//        val fi = File(fileFinalPath)
//        val fileDidExist = fi.exists()
//
//        if (!fileDidExist) {
//            // create new file. need all chunks
//            FileOutputStream(fileFinalPath).use { fs ->
//                fs.channel.truncate(file.totalSize)
//            }
//
//            neededChunks = file.chunks.toMutableList()
//        } else {
//            // open existing
//            if (oldManifestFile != null) {
//                neededChunks = mutableListOf()
//
//                val hashMatches = oldManifestFile.fileHash.contentEquals(file.fileHash)
//                if (!hashMatches) {
//                    logger.debug("Validating $fileFinalPath")
//
//                    val matchingChunks = mutableListOf<ChunkMatch>()
//
//                    for (chunk in file.chunks) {
//                        val oldChunk = oldManifestFile.chunks.find { it.chunkID.contentEquals(chunk.chunkID) }
//                        if (oldChunk != null) {
//                            matchingChunks.add(ChunkMatch(oldChunk, chunk))
//                        } else {
//                            neededChunks.add(chunk)
//                        }
//                    }
//
//                    val orderedChunks = matchingChunks.sortedBy { it.oldChunk.offset }
//
//                    val copyChunks = mutableListOf<ChunkMatch>()
//
//                    FileInputStream(fileFinalPath).use { fsOld ->
//                        for (match in orderedChunks) {
//                            fsOld.channel.position(match.oldChunk.offset)
//
//                            val tmp = ByteArray(match.oldChunk.uncompressedLength)
//                            fsOld.readNBytesCompat(tmp, 0, tmp.size)
//
//                            val adler = Utils.adlerHash(tmp)
//                            if (adler != match.oldChunk.checksum) {
//                                neededChunks.add(match.newChunk)
//                            } else {
//                                copyChunks.add(match)
//                            }
//                        }
//                    }
//
//                    if (neededChunks.isNotEmpty()) {
//                        File(fileFinalPath).renameTo(File(fileStagingPath))
//
//                        FileInputStream(fileStagingPath).use { fsOld ->
//                            FileOutputStream(fileFinalPath).use { fs ->
//                                fs.channel.truncate(file.totalSize)
//
//                                for (match in copyChunks) {
//                                    fsOld.channel.position(match.oldChunk.offset)
//
//                                    val tmp = ByteArray(match.oldChunk.uncompressedLength)
//                                    fsOld.readNBytesCompat(tmp, 0, tmp.size)
//
//                                    fs.channel.position(match.newChunk.offset)
//                                    fs.write(tmp)
//                                }
//                            }
//                        }
//
//                        File(fileStagingPath).delete()
//                    }
//                }
//            } else {
//                // No old manifest or file not in old manifest. We must validate.
//                RandomAccessFile(fileFinalPath, "rw").use { fs ->
//                    if (fi.length() != file.totalSize) {
//                        fs.channel.truncate(file.totalSize)
//                    }
//
//                    logger.debug("Validating $fileFinalPath")
//                    neededChunks = Utils.validateSteam3FileChecksums(
//                        fs,
//                        file.chunks.sortedBy { it.offset }.toTypedArray()
//                    )
//                }
//            }
//
//            if (neededChunks.isEmpty()) {
//                synchronized(depotDownloadCounter) {
//                    depotDownloadCounter.sizeDownloaded += file.totalSize
//                }
//
//                onDownloadProgress?.onProgress(
//                    depotFilesData.depotCounter.sizeDownloaded.toFloat() / depotFilesData.depotCounter.completeDownloadSize
//                )
//
//                return@async
//            }
//
//            val sizeOnDisk = file.totalSize - neededChunks.sumOf { it.uncompressedLength.toLong() }
//            synchronized(depotDownloadCounter) {
//                depotDownloadCounter.sizeDownloaded += sizeOnDisk
//            }
//
//            onDownloadProgress?.onProgress(
//                depotFilesData.depotCounter.sizeDownloaded.toFloat() / depotFilesData.depotCounter.completeDownloadSize
//            )
//        }
//
//        val fileIsExecutable = file.flags.contains(EDepotFileFlag.Executable)
//        if (fileIsExecutable &&
//            (!fileDidExist || oldManifestFile == null || !oldManifestFile.flags.contains(EDepotFileFlag.Executable))
//        ) {
//            File(fileFinalPath).setExecutable(true)
//        } else if (!fileIsExecutable && oldManifestFile != null && oldManifestFile.flags.contains(EDepotFileFlag.Executable)) {
//            File(fileFinalPath).setExecutable(false)
//        }
//
//        val fileStreamData = FileStreamData(
//            fileStream = null,
//            fileLock = Semaphore(1),
//            chunksToDownload = neededChunks.size
//        )
//
//        for (chunk in neededChunks) {
//            networkChunkQueue.add(Triple(fileStreamData, file, chunk))
//        }
//    }
//
//    private fun downloadSteam3DepotFileChunk(
//        cdnPool: CDNClientPool,
//        downloadCounter: GlobalDownloadCounter,
//        depotFilesData: DepotFilesData,
//        file: FileData,
//        fileStreamData: FileStreamData,
//        chunk: ChunkData,
//        onDownloadProgress: ProgressCallback? = null,
//        parentScope: CoroutineScope,
//    ) = parentScope.async {
//        if (!isActive) {
//            return@async
//        }
//
//        val depot = depotFilesData.depotDownloadInfo
//        val depotDownloadCounter = depotFilesData.depotCounter
//
//        val chunkID = Strings.toHex(chunk.chunkID)
//
//        val chunkInfo = ChunkData(chunk)
//
//        var outputChunkData = ByteArray(chunkInfo.uncompressedLength)
//        var writtenBytes = 0
//
//        do {
//            var connection: Server? = null
//
//            try {
//                connection = cdnPool.getConnection().await()
//
//                outputChunkData = ByteArray(chunkInfo.uncompressedLength)
//                writtenBytes = cdnPool.cdnClient.downloadDepotChunk(
//                    depotId = depot.depotId,
//                    chunk = chunkInfo,
//                    server = connection!!,
//                    destination = outputChunkData,
//                    depotKey = depot.depotKey,
//                    proxyServer = cdnPool.proxyServer
//                ).await()
//
//                cdnPool.returnConnection(connection)
//            } catch (e: SteamKitWebRequestException) {
//                cdnPool.returnBrokenConnection(connection)
//
//                when (e.statusCode) {
//                    HTTP.UNAUTHORIZED.code,
//                    HTTP.FORBIDDEN.code,
//                        -> {
//                        logger.error("Encountered ${e.statusCode} for chunk $chunkID. Aborting.")
//                        break
//                    }
//
//                    else -> logger.error("Encountered error downloading chunk $chunkID: ${e.statusCode}")
//                }
//            } catch (e: Exception) {
//                cdnPool.returnBrokenConnection(connection)
//
//                logger.error("Encountered unexpected error downloading chunk $chunkID", e)
//            }
//        } while (isActive && writtenBytes <= 0)
//
//        if (writtenBytes <= 0) {
//            logger.error("Failed to find any server with chunk $chunkID for depot ${depot.depotId}. Aborting.")
//            throw CancellationException("Failed to download chunk")
//        }
//
//        try {
//            fileStreamData.fileLock.acquire()
//
//            if (fileStreamData.fileStream == null) {
//                val fileFinalPath = Paths.get(depot.installDir, file.fileName).toString()
//                val randomAccessFile = RandomAccessFile(fileFinalPath, "rw") // TODO this resource leaks. (see below)
//                fileStreamData.fileStream = randomAccessFile.channel
//            }
//
//            fileStreamData.fileStream?.position(chunkInfo.offset)
//            fileStreamData.fileStream?.write(ByteBuffer.wrap(outputChunkData, 0, writtenBytes))
//        } finally {
//            fileStreamData.fileLock.release()
//        }
//
//        val remainingChunks = synchronized(fileStreamData) {
//            fileStreamData.chunksToDownload--
//        }
//        if (remainingChunks == 0) {
//            // TODO this condition is never called?
//            fileStreamData.fileStream?.close()
//            fileStreamData.fileLock.release()
//        }
//
//        var sizeDownloaded: Long
//        synchronized(depotDownloadCounter) {
//            sizeDownloaded = depotDownloadCounter.sizeDownloaded + outputChunkData.size
//            depotDownloadCounter.sizeDownloaded = sizeDownloaded
//            depotDownloadCounter.depotBytesCompressed += chunk.compressedLength
//            depotDownloadCounter.depotBytesUncompressed += chunk.uncompressedLength
//        }
//
//        synchronized(downloadCounter) {
//            downloadCounter.totalBytesCompressed += chunk.compressedLength
//            downloadCounter.totalBytesUncompressed += chunk.uncompressedLength
//        }
//
//        onDownloadProgress?.onProgress(
//            depotFilesData.depotCounter.sizeDownloaded.toFloat() / depotFilesData.depotCounter.completeDownloadSize
//        )
//    }
//
//    private fun downloadFilesManifestOf(
//        appId: Int,
//        depotId: Int,
//        manifestId: Long,
//        branch: String,
//        depotKey: ByteArray,
//        cdnPool: CDNClientPool,
//        parentScope: CoroutineScope,
//    ): Deferred<DepotManifest?> = parentScope.async {
//        if (!isActive) {
//            return@async null
//        }
//
//        var depotManifest: DepotManifest? = null
//        var manifestRequestCode = 0L
//        var manifestRequestCodeExpiration = Instant.MIN
//
//        do {
//            var connection: Server? = null
//
//            try {
//                connection = cdnPool.getConnection().await()
//
//                if (connection == null) continue
//
//                val now = Instant.now()
//
//                // In order to download this manifest, we need the current manifest request code
//                // The manifest request code is only valid for a specific period of time
//                if (manifestRequestCode == 0L || now >= manifestRequestCodeExpiration) {
//                    val steamContent = steamClient.getHandler(SteamContent::class.java)!!
//
//                    manifestRequestCode = steamContent.getManifestRequestCode(
//                        depotId = depotId,
//                        appId = appId,
//                        manifestId = manifestId,
//                        branch = branch,
//                        parentScope = parentScope
//                    ).await()
//
//                    // This code will hopefully be valid for one period following the issuing period
//                    manifestRequestCodeExpiration = now.plus(5, ChronoUnit.MINUTES)
//
//                    // If we could not get the manifest code, this is a fatal error
//                    if (manifestRequestCode == 0L) {
//                        throw CancellationException("No manifest request code was returned for manifest $manifestId in depot $depotId")
//                    }
//                }
//
//                depotManifest = cdnPool.cdnClient.downloadManifest(
//                    depotId = depotId,
//                    manifestId = manifestId,
//                    manifestRequestCode = manifestRequestCode,
//                    server = connection,
//                    depotKey = depotKey,
//                    proxyServer = cdnPool.proxyServer
//                ).await()
//
//                cdnPool.returnConnection(connection)
//            } catch (e: CancellationException) {
//                logger.error("Connection timeout downloading depot manifest $depotId $manifestId", e)
//
//                return@async null
//            } catch (e: SteamKitWebRequestException) {
//                cdnPool.returnBrokenConnection(connection)
//
//                val statusName = when (e.statusCode) {
//                    HTTP.UNAUTHORIZED.code -> HTTP.UNAUTHORIZED.name
//                    HTTP.FORBIDDEN.code -> HTTP.FORBIDDEN.name
//                    HTTP.NOT_FOUND.code -> HTTP.NOT_FOUND.name
//                    HTTP.SERVICE_UNAVAILABLE.code -> HTTP.SERVICE_UNAVAILABLE.name
//                    else -> null
//                }
//
//                logger.error(
//                    "Downloading of manifest $manifestId failed for depot $depotId with " +
//                        if (statusName != null) {
//                            "response of $statusName(${e.statusCode})"
//                        } else {
//                            "status code of ${e.statusCode}"
//                        }
//                )
//
//                return@async null
//            } catch (e: Exception) {
//                cdnPool.returnBrokenConnection(connection)
//
//                logger.error("Encountered error downloading manifest for depot $depotId $manifestId", e)
//
//                return@async null
//            }
//        } while (isActive && depotManifest == null)
//
//        if (depotManifest == null) {
//            throw CancellationException("Unable to download manifest $manifestId for depot $depotId")
//        }
//
//        val newProtoManifest = DepotManifest(depotManifest)
//        steamClient.configuration.depotManifestProvider.updateManifest(newProtoManifest)
//
//        return@async newProtoManifest
//    }
}
