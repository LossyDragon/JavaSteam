package `in`.dragonbra.javasteam.steam.contentdownloader

import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.*
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
import `in`.dragonbra.javasteam.steam.contentdownloader.CDNClientPool
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.UGCDetailsCallback
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.PublishedFileID.Companion.toLong
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.Strings
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.*
import java.io.File
import java.io.FileWriter
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.util.concurrent.ConcurrentHashMap

class ContentDownloaderException(value: String) : Exception(value)

sealed class DirectoryResult {
    data class Success(val installDir: String) : DirectoryResult()
    data object Failed : DirectoryResult()
}

private const val DEFAULT_DOWNLOAD_DIR = "downloads"
private const val CONFIG_DIR = "config"
private const val STAGING_DIR = "staging"

data class DownloadConfig(
    var downloadAllPlatforms: Boolean = false,
    var downloadAllArchs: Boolean = false,
    var downloadAllLanguages: Boolean = false,
    var downloadManifestOnly: Boolean = false,
    var installDirectory: String? = null,
    var usingFileList: Boolean = false,
    var filesToDownload: MutableSet<String> = mutableSetOf(),
    var filesToDownloadRegex: MutableList<Regex> = mutableListOf(),
    var verifyAll: Boolean = false,
    var maxServers: Int = 0,
    var maxDownloads: Int = 0,
    var betaPassword: String? = null,

    // TODO these are session info
    var licenses: List<License> = emptyList(),
    val appTokens: ConcurrentHashMap<Int, Long> = ConcurrentHashMap<Int, Long>(),
    val packageTokens: ConcurrentHashMap<Int, Long> = ConcurrentHashMap<Int, Long>(),
    val depotKeys: ConcurrentHashMap<Int, ByteArray> = ConcurrentHashMap<Int, ByteArray>(),
    var appInfo: ConcurrentHashMap<Int, PICSProductInfo?> = ConcurrentHashMap<Int, PICSProductInfo?>(),
    var packageInfo: ConcurrentHashMap<Int, PICSProductInfo?> = ConcurrentHashMap<Int, PICSProductInfo?>(),
    var appBetaPasswords: ConcurrentHashMap<String, ByteArray> = ConcurrentHashMap<String, ByteArray>(),
)

@Suppress("unused", "SpellCheckingInspection")
class ContentDownloader(val steamClient: SteamClient, private val config: DownloadConfig) {

    companion object {
        private const val HTTP_UNAUTHORIZED = 401
        private const val HTTP_FORBIDDEN = 403
        private const val HTTP_NOT_FOUND = 404
        private const val SERVICE_UNAVAILABLE = 503

        internal const val INVALID_APP_ID = Int.MAX_VALUE
        internal const val INVALID_DEPOT_ID = Int.MAX_VALUE
        internal const val INVALID_MANIFEST_ID = Long.MAX_VALUE
        internal const val DEFAULT_BRANCH = "public"

        private val logger: Logger = LogManager.getLogger(ContentDownloader::class.java)

        @JvmStatic
        fun dumpManifestToTextFile(depot: DepotDownloadInfo, manifest: DepotManifest) {
            val txtManifest =
                Paths.get(depot.installDir, "manifest_${depot.depotId}_${depot.manifestId}.txt").toString()
            FileWriter(txtManifest).use { writer ->
                writer.write("Content Manifest for Depot ${depot.depotId}\n\n")
                writer.write("Manifest ID / date     : ${depot.manifestId} / ${manifest.creationTime}\n")

                val uniqueChunks = HashSet<ByteArray>()
                manifest.files.forEach { file ->
                    file.chunks.forEach { chunk ->
                        chunk.chunkID?.let { uniqueChunks.add(it) }
                    }
                }

                writer.write("Total number of files  : ${manifest.files.size}\n")
                writer.write("Total number of chunks : ${uniqueChunks.size}\n")
                writer.write("Total bytes on disk    : ${manifest.totalUncompressedSize}\n")
                writer.write("Total bytes compressed : ${manifest.totalCompressedSize}\n\n\n")
                writer.write("          Size Chunks File SHA                                 Flags Name\n")

                manifest.files.forEach { file ->
                    val sha1Hash = file.fileHash.joinToString("") { "%02x".format(it) }
                    writer.write(
                        "%14d %6d %s %5x %s\n".format(
                            file.totalSize,
                            file.chunks.size,
                            sha1Hash,
                            EDepotFileFlag.code(file.flags),
                            file.fileName
                        )
                    )
                }
            }
        }
    }

    private val defaultScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private var cdnPool: CDNClientPool? = null

    fun createDirectories(depotId: Int, depotVersion: Int): DirectoryResult = runCatching {
        var installDir: String? = null

        if (config.installDirectory.isNullOrBlank()) {
            File(DEFAULT_DOWNLOAD_DIR).mkdirs()
            val depotPath = File(DEFAULT_DOWNLOAD_DIR, depotId.toString()).path
            File(depotPath).mkdirs()
            installDir = File(depotPath, depotVersion.toString()).path
            File(installDir).mkdirs()
            File(installDir, CONFIG_DIR).mkdirs()
            File(installDir, STAGING_DIR).mkdirs()
        } else {
            File(config.installDirectory!!).mkdirs()
            installDir = config.installDirectory
            File(installDir, CONFIG_DIR).mkdirs()
            File(installDir, STAGING_DIR).mkdirs()
        }

        DirectoryResult.Success(installDir!!)
    }.getOrElse {
        DirectoryResult.Failed
    }

    fun testIsFileIncluded(filename: String): Boolean {
        if (!config.usingFileList) {
            return true
        }

        val normalizedFilename = filename.replace('\\', '/')

        if (config.filesToDownload.contains(normalizedFilename)) {
            return true
        }

        return config.filesToDownloadRegex.any { regex ->
            regex.matches(normalizedFilename)
        }
    }

    suspend fun accountHasAccess(appId: Int, depotId: Int): Boolean {
        if (steamClient.steamID == null || (config.licenses.isEmpty() && steamClient.steamID.accountType != EAccountType.AnonUser)) {
            return false
        }

        val licenseQuery: Iterable<Int> = if (steamClient.steamID.accountType == EAccountType.AnonUser) {
            listOf(17906)
        } else {
            config.licenses.map { it.packageID }.distinct()
        }

        requestPackageInfo(licenseQuery)

        licenseQuery.forEach { license ->
            val pkg = config.packageInfo[license]

            if (pkg != null) {
                if (pkg.keyValues["appids"].children.any { it.asInteger() == depotId }) {
                    return true
                }

                if (pkg.keyValues["depotids"].children.any { it.asInteger() == depotId }) {
                    return true
                }
            }
        }

        // Check if this app is free to download without a license
        val info = getSteam3AppSection(appId, EAppInfoSection.Common)

        return info != null && info["FreeToDownload"].asBoolean()
    }

    @Suppress("SameParameterValue")
    private fun getSteam3AppSection(appId: Int, section: EAppInfoSection): KeyValue? {
        if (config.appInfo.isEmpty()) {
            return null
        }

        val app = config.appInfo[appId] ?: return null

        val appInfo = app.keyValues
        val sectionKey = when (section) {
            EAppInfoSection.Common -> "common"
            EAppInfoSection.Extended -> "extended"
            EAppInfoSection.Config -> "config"
            EAppInfoSection.Depots -> "depots"
            else -> throw RuntimeException("${section.name} not implemented")
        }

        val sectionKV = appInfo.children.firstOrNull { it.name == sectionKey }
        return sectionKV
    }

    fun getSteam3AppBuildNumber(appId: Int, branch: String): Int {
        if (appId == INVALID_APP_ID) {
            return 0
        }

        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID
        val branches = depots["branches"]
        val node = branches[branch]

        if (node == KeyValue.INVALID) {
            return 0
        }

        val buildId = node["buildid"] ?: KeyValue.INVALID

        if (buildId == KeyValue.INVALID) {
            return 0
        }

        return buildId.asInteger()
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

        return depotChild["depotfromapp"].asInteger()
    }

    suspend fun getSteam3DepotManifest(depotId: Int, appId: Int, branch: String): Long {
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID
        val depotChild = depots[depotId.toString()] ?: KeyValue.INVALID

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
                logger.error("App $appId, Depot $depotId has depotfromapp of $otherAppId")
                return INVALID_MANIFEST_ID
            }

            requestAppInfo(otherAppId)

            return getSteam3DepotManifest(depotId, otherAppId, branch) // recursion
        }

        val manifests = depotChild["manifests"]
        val manifestsEncrypted = depotChild["encryptedmanifests"]

        if (manifests.children.isEmpty() && manifestsEncrypted.children.isEmpty()) {
            return INVALID_MANIFEST_ID
        }

        val node = manifests[branch]["gid"] ?: KeyValue.INVALID

        if (node == KeyValue.INVALID && !branch.equals(DEFAULT_BRANCH, ignoreCase = true)) {
            val nodeEncrypted = manifestsEncrypted[branch] ?: KeyValue.INVALID
            if (nodeEncrypted != KeyValue.INVALID) {
                val password = config.betaPassword

                // Could wait here and complete a future to obtain a password on the fly?

                val encryptedGid = nodeEncrypted["gid"] ?: KeyValue.INVALID
                if (encryptedGid != KeyValue.INVALID) {
                    // Submit the password to Steam now to get encryption keys
                    checkAppBetaPassword(appId, password.orEmpty())

                    val appBetaPassword = config.appBetaPasswords[branch]
                    if (appBetaPassword == null) {
                        logger.debug("Password was invalid for branch $branch")
                        return INVALID_MANIFEST_ID
                    }

                    val input = Strings.decodeHex(encryptedGid.value)
                    val manifestBytes: ByteArray
                    try {
                        manifestBytes = CryptoHelper.symmetricDecryptECB(input, appBetaPassword)
                    } catch (e: Exception) {
                        logger.error("Failed to decrypt app $branch", e)
                        return INVALID_MANIFEST_ID
                    }

                    return ByteBuffer.wrap(manifestBytes).order(ByteOrder.LITTLE_ENDIAN).getLong(0) // TODO ??
                }

                logger.error("Unhandled depot encryption for depotId $depotId")
                return INVALID_MANIFEST_ID
            }

            return INVALID_MANIFEST_ID
        }

        if (node.value == null) {
            return INVALID_MANIFEST_ID
        }

        return node.value.toLong()
    }

    fun getAppName(appId: Int): String {
        val info = getSteam3AppSection(appId, EAppInfoSection.Common) ?: return ""

        return info["name"].asString()
    }


    suspend fun downloadPubfile(appId: Int, publishedFileId: Long) {
        val details = getPublishedFileDetails(appId, PublishedFileID(publishedFileId))

        if (!details!!.fileUrl.isNullOrEmpty()) {
            downloadWebFile(appId, details.filename, details.filename)
        } else if (details.hcontentFile > 0) {
            downloadApp() // TODO
        } else {
            logger.error("Unable to locate manifest ID for published file $publishedFileId")
        }
    }

    suspend fun downloadUGC(appId: Int, ugcId: Long) {
        var details: UGCDetailsCallback? = null

        if (steamClient.steamID.accountType != EAccountType.AnonUser) {
            details = getUGCDetails(UGCHandle(ugcId))
        } else {
            logger.error("Unable to query UGC details for $ugcId from anonymous account.")
        }

        if (!details?.url.isNullOrEmpty()) {
            downloadWebFile(appId, details!!.fileName, details.url)
        } else {
            downloadApp()
        }
    }

    suspend fun downloadWebFile(appId: Int, fileName: String, url: String) = withContext(Dispatchers.IO) {
        when (val result = createDirectories(appId, 0)) {
            is DirectoryResult.Success -> {
                val stagingDir = File(result.installDir, STAGING_DIR)
                val fileStagingPath = File(stagingDir, fileName)
                val fileFinalPath = File(result.installDir, fileName)

                File(fileFinalPath.parent).mkdirs()
                File(fileStagingPath.parent).mkdirs()

                HttpClient(CIO).use { client ->
                    val response = client.get(url)
                    FileChannel.open(
                        fileStagingPath.toPath(),
                        StandardOpenOption.CREATE,
                        StandardOpenOption.WRITE
                    ).use { file ->
                        logger.debug("Downloading: $fileName")
                        response.bodyAsChannel().copyTo(file)
                    }
                }

                if (fileFinalPath.exists()) {
                    fileFinalPath.delete()
                }

                fileStagingPath.renameTo(fileFinalPath)
            }
            DirectoryResult.Failed -> {
                logger.error("Error: Unable to create install directories!")
            }
        }
    }

    data class DepotManifestIds(val depotId: Int, val manifestId: Long)

    suspend fun downloadAppAsync(
        appId: Int,
        depotManifestIds: List<DepotManifestIds>,
        branch: String,
        os: String? = null,
        arch: String,
        language: String,
        lv: Boolean,
        isUgc: Boolean
    ) = withContext(Dispatchers.IO) {
        var depotManifestIds = depotManifestIds
        cdnPool = CDNClientPool(steamClient, appId, this)

        // Load our configuration data containing the depots currently installed
        var configPath = config.installDirectory
        if (configPath.isNullOrEmpty()) {
            configPath = DEFAULT_DOWNLOAD_DIR
        }

        File(Paths.get(configPath, CONFIG_DIR).toString()).mkdirs()

        requestAppInfo(appId)

        if (!accountHasAccess(appId, appId)) {
            if (requestFreeAppLicense(appId)) {
                logger.debug("Obtained FreeOnDemand license for app $appId")

                // Fetch app info again in case we didn't get it fully without a license.
                requestAppInfo(appId, true)
            } else {
                val contentName = getAppName(appId)
                throw ContentDownloaderException("App $appId ($contentName) is not available from this account.")
            }
        }

        val hasSpecificDepots = depotManifestIds.isNotEmpty()
        val depotIdsFound = mutableListOf<Int>()
        val depotIdsExpected = depotManifestIds.map { it.depotId }.toMutableList()
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID

        if (isUgc) {
            val workshopDepot = depots["workshopdepot"].asInteger()
            if (workshopDepot != 0 && !depotIdsExpected.contains(workshopDepot)) {
                depotIdsExpected.add(workshopDepot)
                depotManifestIds = depotManifestIds.map { DepotManifestIds(workshopDepot, it.manifestId) }.toList()
            }

            depotIdsFound.addAll(depotIdsExpected)
        } else {
            logger.debug("Using app brach: $branch")

            if (depots != null) {
                depots.children.forEach { depotSection ->
                    var id = INVALID_DEPOT_ID
                    if (depotSection.children.isEmpty()) {
                        return@forEach
                    }

                    id = depotSection.name.toIntOrNull() ?: return@forEach

                    if (hasSpecificDepots && !depotIdsExpected.contains(id)) {
                        return@forEach
                    }

                    if (!hasSpecificDepots) {
                        val depotConfig = depotSection["config"] ?: KeyValue.INVALID
                        if (depotConfig != KeyValue.INVALID) {
                            if (!config.downloadAllPlatforms &&
                                depotConfig["oslist"] != KeyValue.INVALID &&
                                !depotConfig["oslist"].value.isNullOrBlank()
                            ) {
                                val oslist = depotConfig["oslist"].value.split(',')
                                val targetOs = os ?: Util.getSteamOS()
                                if (targetOs !in oslist)
                                    continue
                            }
                        }

                        // TODO continue
                    }
                }
            } else {

            }
        }
    }

    // TODO
    //region [REGION] Steam Session
    suspend fun requestPackageInfo(packageIds: Iterable<Int>) {
        val packages = packageIds.toMutableList()
        packages.removeAll { config.packageInfo.containsKey(it) }

        if (packages.isEmpty()) {
            return
        }

        val packageRequests = mutableListOf<PICSRequest>()
        packages.forEach { pkg ->
            val request = PICSRequest(pkg)

            val token = config.packageTokens[pkg]
            if (token != null) {
                request.accessToken = token
            }

            packageRequests.add(request)
        }

        val packageInfoMultiple = steamClient.getHandler<SteamApps>()!!
            .picsGetProductInfo(emptyList(), packageRequests).await()

        packageInfoMultiple.results.forEach { packageInfo ->
            packageInfo.packages.forEach { packageValue ->
                val pkg = packageValue.value
                config.packageInfo[pkg.id] = pkg
            }

            packageInfo.unknownPackages.forEach { pkg ->
                config.packageInfo[pkg] = null
            }
        }
    }

    suspend fun requestAppInfo(appId: Int, bForce: Boolean = false) {
        if (config.appInfo.containsKey(appId) && !bForce) {
            return
        }

        val appTokens = steamClient.getHandler<SteamApps>()!!.picsGetAccessTokens(listOf(appId), emptyList()).await()

        if (appTokens.appTokensDenied.contains(appId)) {
            logger.error("Insufficient privileges to get access token for app $appId")
        }

        appTokens.appTokens.forEach { tokenDict ->
            config.appTokens[tokenDict.key] = tokenDict.value
        }

        val request = PICSRequest(appId)

        val token = config.appTokens[appId]
        if (token != null) {
            request.accessToken = token
        }

        val appInfoMultiple = steamClient.getHandler<SteamApps>()!!
            .picsGetProductInfo(listOf(request), emptyList()).await()

        appInfoMultiple.results.forEach { appInfo ->
            appInfo.packages.forEach { appValue ->
                val app = appValue.value

                logger.debug("Got AppInfo for ${app.id}")
                config.appInfo[app.id] = app
            }

            appInfo.unknownPackages.forEach { app ->
                config.appInfo[app] = null
            }
        }
    }

    suspend fun checkAppBetaPassword(appId: Int, password: String) {
        val appPassword = steamClient.getHandler<SteamApps>()!!.checkAppBetaPassword(appId, password).await()

        logger.debug("Retrieved ${appPassword.betaPasswords.size} beta keys with result ${appPassword.result}")

        appPassword.betaPasswords.forEach { entry ->
            config.appBetaPasswords[entry.key] = entry.value
        }
    }

    suspend fun getPublishedFileDetails(
        appId: Int,
        pubFile: PublishedFileID
    ): SteammessagesPublishedfileSteamclient.PublishedFileDetails? {
        val pubFileRequest = CPublishedFile_GetDetails_Request.newBuilder().apply {
            this.appid = appId
            this.addPublishedfileids(pubFile.toLong())
        }.build()

        val details = steamClient.getHandler<SteamUnifiedMessages>()!!
            .createService<PublishedFile>()
            .getDetails(pubFileRequest)
            .await()

        if (details.result == EResult.OK) {
            return details.body.publishedfiledetailsList.firstOrNull()
        }

        steamClient.getHandler<SteamUnifiedMessages>()!!.removeService<PublishedFile>()

        throw Exception("EResult ${details.result.code()} (${details.result} while retrieving file details for pubfile $pubFile)")

    }

    suspend fun getUGCDetails(ugcHandle: UGCHandle): UGCDetailsCallback? {
        val callback = steamClient.getHandler<SteamCloud>()!!
            .requestUGCDetails(ugcHandle)
            .await()

        if (callback.result == EResult.OK) {
            return callback
        } else if (callback.result == EResult.FileNotFound) {
            return null
        }

        throw Exception("EResult ${callback.result.code()} (${callback.result}) while retrieving UGC details for $ugcHandle")
    }

    suspend fun requestFreeAppLicense(appId: Int): Boolean {
        try {
            val resultInfo = steamClient.getHandler<SteamApps>()!!.requestFreeLicense(appId).await()
            return resultInfo.grantedApps.contains(appId)
        } catch (e: Exception) {
            logger.error("Failed to request free license for app $appId", e)
            return false
        }
    }

    //endregion

//    private fun requestDepotKey(
//        appId: Int,
//        depotId: Int,
//        parentScope: CoroutineScope,
//    ): Deferred<Pair<EResult, ByteArray?>> = parentScope.async {
//        val steamApps = steamClient.getHandler(SteamApps::class.java)
//        val callback = steamApps?.getDepotDecryptionKey(depotId, appId)?.await()
//
//        return@async Pair(callback?.result ?: EResult.Fail, callback?.depotKey)
//    }
//
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
//        val apps = callback?.results?.flatMap { it.apps.values }
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
//    /**
//     * Kotlin coroutines version
//     */
//    fun downloadApp(
//        appId: Int,
//        depotId: Int,
//        installPath: String,
//        stagingPath: String,
//        branch: String = "public",
//        maxDownloads: Int = 8,
//        onDownloadProgress: ((Float) -> Unit)? = null,
//        parentScope: CoroutineScope = defaultScope,
//    ): Deferred<Boolean> = parentScope.async {
//        downloadAppInternal(
//            appId = appId,
//            depotId = depotId,
//            installPath = installPath,
//            stagingPath = stagingPath,
//            branch = branch,
//            maxDownloads = maxDownloads,
//            onDownloadProgress = onDownloadProgress,
//            scope = parentScope
//        )
//    }
//
//    /**
//     * Java-friendly version that returns a CompletableFuture
//     */
//    @JvmOverloads
//    fun downloadApp(
//        appId: Int,
//        depotId: Int,
//        installPath: String,
//        stagingPath: String,
//        branch: String = "public",
//        maxDownloads: Int = 8,
//        progressCallback: ProgressCallback? = null,
//    ): CompletableFuture<Boolean> = defaultScope.future {
//        downloadAppInternal(
//            appId = appId,
//            depotId = depotId,
//            installPath = installPath,
//            stagingPath = stagingPath,
//            branch = branch,
//            maxDownloads = maxDownloads,
//            onDownloadProgress = progressCallback?.let { callback -> { progress -> callback.onProgress(progress) } },
//            scope = defaultScope
//        )
//    }
//
//    private suspend fun downloadAppInternal(
//        appId: Int,
//        depotId: Int,
//        installPath: String,
//        stagingPath: String,
//        branch: String = "public",
//        maxDownloads: Int = 8,
//        onDownloadProgress: ((Float) -> Unit)? = null,
//        scope: CoroutineScope,
//    ): Boolean {
//        if (!scope.isActive) {
//            logger.error("App $appId was not completely downloaded. Operation was canceled.")
//            return false
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
//            return false
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
//            return false
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
//                return false
//            }
//
//            if (!scope.isActive) {
//                return false
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
//            return true
//        } catch (e: CancellationException) {
//            logger.error("App $appId was not completely downloaded. Operation was canceled.")
//
//            return false
//        } catch (e: Exception) {
//            logger.error("Error occurred while downloading app $shiftedAppId", e)
//
//            return false
//        }
//    }
//
//    private fun downloadDepotFiles(
//        cdnPool: CDNClientPool,
//        downloadCounter: GlobalDownloadCounter,
//        depotFilesData: DepotFilesData,
//        maxDownloads: Int,
//        onDownloadProgress: ((Float) -> Unit)? = null,
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
//        onDownloadProgress: ((Float) -> Unit)? = null,
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
//                onDownloadProgress?.apply {
//                    val totalPercent =
//                        depotFilesData.depotCounter.sizeDownloaded.toFloat() / depotFilesData.depotCounter.completeDownloadSize
//                    this(totalPercent)
//                }
//
//                return@async
//            }
//
//            val sizeOnDisk = file.totalSize - neededChunks.sumOf { it.uncompressedLength.toLong() }
//            synchronized(depotDownloadCounter) {
//                depotDownloadCounter.sizeDownloaded += sizeOnDisk
//            }
//
//            onDownloadProgress?.apply {
//                val totalPercent =
//                    depotFilesData.depotCounter.sizeDownloaded.toFloat() / depotFilesData.depotCounter.completeDownloadSize
//                this(totalPercent)
//            }
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
//        onDownloadProgress: ((Float) -> Unit)? = null,
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
//                )
//
//                cdnPool.returnConnection(connection)
//            } catch (e: SteamKitWebRequestException) {
//                cdnPool.returnBrokenConnection(connection)
//
//                when (e.statusCode) {
//                    HTTP_UNAUTHORIZED, HTTP_FORBIDDEN -> {
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
//                val randomAccessFile = RandomAccessFile(fileFinalPath, "rw")
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
//            --fileStreamData.chunksToDownload
//        }
//        if (remainingChunks <= 0) {
//            fileStreamData.fileStream?.close()
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
//        onDownloadProgress?.invoke(
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
//                )
//
//                cdnPool.returnConnection(connection)
//            } catch (e: CancellationException) {
//                logger.error("Connection timeout downloading depot manifest $depotId $manifestId")
//
//                return@async null
//            } catch (e: SteamKitWebRequestException) {
//                cdnPool.returnBrokenConnection(connection)
//
//                val statusName = when (e.statusCode) {
//                    HTTP_UNAUTHORIZED -> HTTP_UNAUTHORIZED::class.java.name
//                    HTTP_FORBIDDEN -> HTTP_FORBIDDEN::class.java.name
//                    HTTP_NOT_FOUND -> HTTP_NOT_FOUND::class.java.name
//                    SERVICE_UNAVAILABLE -> SERVICE_UNAVAILABLE::class.java.name
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
