package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.UGCDetailsCallback
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.types.ChunkData
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.FileData
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.SteamKitWebRequestException
import `in`.dragonbra.javasteam.util.Strings
import `in`.dragonbra.javasteam.util.Utils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.future.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.cancellation.CancellationException

@Suppress("unused")
object ContentDownloader {

    class ContentDownloaderException(value: String) : Exception(value)

    sealed class DownloadProgress {
        object Idle : DownloadProgress()
        data class Preparing(val appId: Int, val totalDepots: Int = 0) : DownloadProgress()
        data class Error(val message: String, val appId: Int = 0, val depotId: Int = 0) : DownloadProgress()

        // TODO Completed and Downloading, use Counter class?

        // TODO this may show all zero's ??
        data class Completed(
            val bytesDownloaded: Long,
            val compressed: Long,
            val uncompressed: Long,
            val depots: Int,
        ) : DownloadProgress()

        // TODO speed and eta time ??
        data class Downloading(
            val percentageComplete: Float,
            val bytesDownloaded: Long,
            val totalBytes: Long,
            val currentFile: String,
            val depotId: Int = 0,
        ) : DownloadProgress()
    }

    // State flow to use in console or android apps.
    private val _downloadProgress = MutableStateFlow<DownloadProgress>(DownloadProgress.Idle)
    val downloadProgress: StateFlow<DownloadProgress> = _downloadProgress

    const val INVALID_APP_ID = Int.MAX_VALUE
    const val INVALID_DEPOT_ID = Int.MAX_VALUE
    const val INVALID_MANIFEST_ID = Long.MAX_VALUE
    const val DEFAULT_BRANCH = "public"

    val config: DownloadConfig = DownloadConfig()

    private var steam3: Steam3Session? = null
    private lateinit var cdnPool: CDNClientPool

    private const val DEFAULT_DOWNLOAD_DIR: String = "depots"
    private const val CONFIG_DIR: String = ".DepotDownloader"
    private val STAGING_DIR: Path = Paths.get(CONFIG_DIR, "staging")

    @Suppress("ArrayInDataClass")
    data class DepotDownloadInfo(
        val depotId: Int,
        val appId: Int,
        val manifestId: Long,
        val branch: String,
        val installDir: Path,
        val depotKey: ByteArray,
    ) {
        override fun toString(): String = "DepotDownloadInfo(" +
            "depotId=$depotId, " +
            "appId=$appId, " +
            "manifestId=$manifestId, " +
            "branch='$branch', " +
            "installDir='$installDir', " +
            "depotKey=${depotKey.contentToString()}" +
            ")"
    }

    @Suppress("SameParameterValue")
    private fun createDirectories(depotId: Int, depotVersion: Int, action: (Path) -> Boolean): Boolean {
        val installDir: Path
        try {
            if (config.installDirectory.isBlank()) {
                val baseDir = Paths.get(DEFAULT_DOWNLOAD_DIR)
                Files.createDirectories(baseDir)

                val depotPath = baseDir.resolve(depotId.toString())
                Files.createDirectories(depotPath)

                installDir = depotPath.resolve(depotVersion.toString())
                Files.createDirectories(installDir)
            } else {
                installDir = Paths.get(config.installDirectory)
                Files.createDirectories(installDir)
            }

            // Create common subdirectories
            Files.createDirectories(installDir.resolve(CONFIG_DIR))
            Files.createDirectories(installDir.resolve(STAGING_DIR))
        } catch (e: Exception) {
            logE(e.message)
            return false
        }

        return action(installDir)
    }

    private fun testIsFileIncluded(fileName: String): Boolean {
        var innerFileName = fileName
        if (!config.usingFileList) {
            return true
        }

        innerFileName = innerFileName.replace('\\', '/')

        if (config.filesToDownload.contains(innerFileName)) {
            return true
        }

        config.filesToDownloadRegex.forEach { rgx ->
            if (rgx.matches(innerFileName)) {
                return true
            }
        }

        return false
    }

    private suspend fun accountHasAccess(appId: Int, depotId: Int): Boolean {
        if (steam3 == null ||
            steam3!!.steamUser.steamID == null ||
            (steam3!!.licenses.isEmpty() && steam3!!.steamUser.steamID!!.accountType != EAccountType.AnonUser)
        ) {
            return false
        }

        val licenseQuery = if (steam3!!.steamUser.steamID!!.accountType == EAccountType.AnonUser) {
            listOf(17906)
        } else {
            steam3!!.licenses.map { it.packageID }.distinct()
        }

        steam3!!.requestPackageInfo(licenseQuery)

        licenseQuery.forEach { license ->
            val pkg = steam3!!.packageInfo[license]
            if (pkg != null) {
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

    @Suppress("SameParameterValue")
    private fun getSteam3AppSection(appId: Int, section: EAppInfoSection): KeyValue? {
        if (steam3 == null || steam3!!.appInfo.isEmpty()) {
            return null
        }

        val app = steam3!!.appInfo[appId] ?: return null

        val appInfo = app.keyValues
        val sectionKey = when (section) {
            EAppInfoSection.Common -> "common"
            EAppInfoSection.Extended -> "extended"
            EAppInfoSection.Config -> "config"
            EAppInfoSection.Depots -> "depots"
            else -> throw Exception("Not implemented")
        }
        val sectionKV = appInfo.children.firstOrNull { it.name == sectionKey }
        return sectionKV
    }

    fun getSteam3AppBuildNumber(appId: Int, branch: String): Int {
        if (appId == INVALID_APP_ID) {
            return 0
        }

        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)
        val branches = depots?.get("branches")
        val node = branches?.get(branch) ?: KeyValue.INVALID

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
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)
        val depotChild = depots?.get(depotId.toString()) ?: KeyValue.INVALID

        if (depotChild == KeyValue.INVALID) {
            return INVALID_APP_ID
        }

        if (depotChild["depotfromapp"] == KeyValue.INVALID) {
            return INVALID_APP_ID
        }

        return depotChild["depotfromapp"].asInteger()
    }

    suspend fun getSteam3DepotManifest(depotId: Int, appId: Int, branch: String): Long {
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)
        val depotChild = depots?.get(depotId.toString()) ?: KeyValue.INVALID

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
                logE("App $appId, Depot $depotId has depotfromapp of $otherAppId!")
                return INVALID_MANIFEST_ID
            }

            steam3!!.requestAppInfo(otherAppId)

            return getSteam3DepotManifest(depotId, otherAppId, branch)
        }

        val manifests = depotChild["manifests"]
        val manifestsEncrypted = depotChild["encryptedmanifests"]

        if (manifests.children.isEmpty() && manifestsEncrypted.children.isEmpty()) {
            return INVALID_MANIFEST_ID
        }

        val node = manifests[branch]["gid"]

        if (node == KeyValue.INVALID && !branch.equals(DEFAULT_BRANCH, true)) {
            val nodeEncrypted = manifestsEncrypted[branch] ?: KeyValue.INVALID
            if (nodeEncrypted != KeyValue.INVALID) {
                var password = config.betaPassword
                while (password.isEmpty()) {
                    print("Please enter the password for branch $branch: ")
                    config.betaPassword = readln().also { password = it }
                }

                val encryptedGid = nodeEncrypted["gid"] ?: KeyValue.INVALID

                if (encryptedGid != KeyValue.INVALID) {
                    // Submit the password to Steam now to get encryption keys
                    steam3!!.checkAppBetaPassword(appId, config.betaPassword)

                    val appBetaPassword = steam3!!.appBetaPasswords[branch]
                    if (appBetaPassword == null) {
                        logI("Password was invalid for branch $branch")
                        return INVALID_MANIFEST_ID
                    }

                    val input = decodeHexString(encryptedGid.value)
                    val manifestBytes = try {
                        symmetricDecryptECB(input, appBetaPassword)
                    } catch (e: Exception) {
                        logE("Failed to decrypt branch $branch: ${e.message}")
                        return INVALID_MANIFEST_ID
                    }

                    return ByteBuffer.wrap(manifestBytes).long
                }

                logI("Unhandled depot encryption for depotId $depotId")
                return INVALID_MANIFEST_ID
            }

            return INVALID_MANIFEST_ID
        }

        if (node.value == null) {
            return INVALID_MANIFEST_ID
        }

        return node.value.toLong()
    }

    private fun getAppName(appId: Int): String? {
        val info = getSteam3AppSection(appId, EAppInfoSection.Common) ?: return ""

        return info["name"].asString()
    }

    suspend fun initializeSteam3(username: String?, password: String?): Boolean {
        var loginToken: String? = null

        if (username != null && config.rememberPassword) {
            loginToken = AccountSettingsStore.instance!!.loginTokens[username]
        }

        steam3 = Steam3Session(
            details = LogOnDetails().apply {
                this.username = username!!
                this.password = if (loginToken == null) password else null
                this.shouldRememberPassword = config.rememberPassword
                this.accessToken = loginToken
                this.loginID = config.loginID ?: 0x4A53 // JS (JavaSteam)
            },
        )

        if (!steam3!!.waitForCredentials()) {
            logI("Unable to get steam3 credentials.")
            return false
        }

        CoroutineScope(Dispatchers.IO).launch {
            steam3!!.tickCallbacks()
        }

        return true
    }

    fun shutdownSteam3() {
        if (steam3 == null) return

        steam3!!.disconnect()
        BufferPool.clear()
        _downloadProgress.value = DownloadProgress.Idle
    }

    suspend fun downloadPubfileAsync(appId: Int, publishedFileId: Long) {
        val details = steam3!!.getPublishedFileDetails(appId, PublishedFileID.fromLong(publishedFileId))

        if (!details!!.fileUrl.isNullOrEmpty()) {
            downloadWebFile(appId, details.filename, details.fileUrl)
        } else if (details.hcontentFile > 0) {
            downloadAppAsync(
                appId = appId,
                depotManifestIds = listOf((appId to details.hcontentFile)),
                branch = DEFAULT_BRANCH,
                os = null,
                arch = null,
                language = null,
                lv = false,
                isUgc = true,
            )
        } else {
            logI("Unable to locate manifest ID for published file $publishedFileId")
        }
    }

    suspend fun downloadUGCAsync(appId: Int, ugcId: Long) {
        var details: UGCDetailsCallback? = null

        if (steam3!!.steamUser.steamID!!.accountType != EAccountType.AnonUser) {
            details = steam3!!.getUGCDetails(UGCHandle(ugcId))
        } else {
            logI("Unable to query UGC details for $ugcId from an anonymous account")
        }

        if (details != null && details.url.isNotEmpty()) {
            downloadWebFile(appId = appId, fileName = details.fileName, url = details.url)
        } else {
            downloadAppAsync(
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

    private suspend fun downloadWebFile(appId: Int, fileName: String, url: String) = withContext(Dispatchers.IO) {
        if (!createDirectories(appId, 0) { installDir ->
                val stagingDir = installDir.resolve(STAGING_DIR)
                val fileStagingPath = stagingDir.resolve(fileName)
                val fileFinalPath = installDir.resolve(fileName)

                // Create parent directories
                Files.createDirectories(fileStagingPath.parent)
                Files.createDirectories(fileFinalPath.parent)

                // Create OkHttp client
                val client = OkHttpClient
                    .Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .readTimeout(30, TimeUnit.SECONDS)
                    .build()

                val request = Request.Builder().url(url).build()

                logI("Downloading $fileName")

                client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        throw IOException("Failed to download file: ${response.code}")
                    }

                    Files.newOutputStream(fileStagingPath).use { outputStream ->
                        response.body.byteStream().use { inputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }
                }

                if (Files.exists(fileFinalPath)) {
                    Files.delete(fileFinalPath)
                }

                Files.move(fileStagingPath, fileFinalPath)

                true
            }
        ) {
            logI("Error: Unable to create install directories!")
            return@withContext
        }
    }

    suspend fun downloadAppAsync(
        appId: Int,
        depotManifestIds: List<Pair<Int, Long>>,
        branch: String,
        os: String?,
        arch: String?,
        language: String?,
        lv: Boolean,
        isUgc: Boolean,
    ) = withContext(Dispatchers.IO) {
        var innerDepotManifestIds = depotManifestIds.toMutableList()
        cdnPool = CDNClientPool(steam3!!, appId)

        // Load our configuration data containing the depots currently installed
        val configPath = config.installDirectory.ifBlank { DEFAULT_DOWNLOAD_DIR }

        Files.createDirectories(Paths.get(configPath, CONFIG_DIR))
        DepotConfigStore.loadFromFile(Paths.get(configPath, CONFIG_DIR, "depot.config").toString())

        steam3!!.requestAppInfo(appId)

        if (!accountHasAccess(appId, appId)) {
            if (steam3!!.requestFreeAppLicense(appId)) {
                logI("Obtained FreeOnDemand license for app $appId")

                // Fetch app info again in case we didn't get it fully without a license.
                steam3!!.requestAppInfo(appId, true)
            } else {
                val contentName = getAppName(appId)
                throw ContentDownloaderException("App $appId ($contentName) is not available from this account.")
            }
        }

        val hasSpecificDepots = innerDepotManifestIds.isNotEmpty()
        val depotIdsFound = mutableListOf<Int>()
        val depotIdsExpected = innerDepotManifestIds.map { it.first }.toMutableList()
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)

        if (isUgc) {
            val workshopDepot = depots?.get("workshopdepot")?.asInteger() ?: 0
            if (workshopDepot != 0 && !depotIdsExpected.contains(workshopDepot)) {
                depotIdsExpected.add(workshopDepot)
                innerDepotManifestIds = innerDepotManifestIds
                    .map { (_, manifestId) -> workshopDepot to manifestId }
                    .toMutableList()
            }

            depotIdsFound.addAll(depotIdsExpected)
        } else {
            logI("Using app branch: '$branch'.")

            if (depots != null) {
                for (depotSection in depots.children) {
                    if (depotSection.children.isEmpty()) {
                        continue
                    }

                    val id: Int = depotSection.name.toIntOrNull() ?: continue

                    if (hasSpecificDepots && !depotIdsExpected.contains(id)) {
                        continue
                    }

                    if (!hasSpecificDepots) {
                        val depotConfig = depotSection["config"]
                        if (depotConfig != KeyValue.INVALID) {
                            if (!config.downloadAllPlatforms &&
                                depotConfig["oslist"] != KeyValue.INVALID &&
                                !depotConfig["oslist"].value.isNullOrBlank()
                            ) {
                                val oslist = depotConfig["oslist"].value.split(',')
                                if ((os ?: getSteamOS()) !in oslist) {
                                    continue
                                }
                            }

                            if (!config.downloadAllArchs &&
                                depotConfig["osarch"] != KeyValue.INVALID &&
                                !depotConfig["osarch"].value.isNullOrBlank()
                            ) {
                                val depotArch = depotConfig["osarch"].value
                                if (depotArch != (arch ?: getSteamArch())) {
                                    continue
                                }
                            }

                            if (!config.downloadAllLanguages &&
                                depotConfig["language"] != KeyValue.INVALID &&
                                !depotConfig["language"].value.isNullOrBlank()
                            ) {
                                val depotLang = depotConfig["language"].value
                                if (depotLang != (language ?: "english")) {
                                    continue
                                }
                            }

                            if (!lv &&
                                depotConfig["lowviolence"] != KeyValue.INVALID &&
                                depotConfig["lowviolence"].asBoolean()
                            ) {
                                continue
                            }
                        }
                    }

                    depotIdsFound.add(id)

                    if (!hasSpecificDepots) {
                        innerDepotManifestIds.add(id to INVALID_MANIFEST_ID)
                    }
                }
            }
        }

        if (innerDepotManifestIds.isEmpty() && !hasSpecificDepots) {
            throw ContentDownloaderException("Couldn't find any depots to download for app $appId")
        }

        if (depotIdsFound.size < depotIdsExpected.size) {
            val remainingDepotIds = depotIdsExpected.filterNot { it in depotIdsFound }
            throw ContentDownloaderException("Depot ${remainingDepotIds.joinToString(", ")} not listed for app $appId")
        }

        val infos = mutableListOf<DepotDownloadInfo>()

        innerDepotManifestIds.forEach {
            val info = getDepotInfo(it.first, appId, it.second, branch)
            if (info != null) {
                infos.add(info)
            }
        }

        try {
            downloadSteam3Async(scope = this, depots = infos)
        } catch (e: CancellationException) {
            logE("App $appId was not completely downloaded.", e)
            throw e
        }
    }

    private suspend fun getDepotInfo(depotId: Int, appId: Int, manifestId: Long, branch: String): DepotDownloadInfo? {
        var innerManifestId = manifestId
        var innerBranch = branch
        if (steam3 != null && appId != INVALID_APP_ID) {
            steam3!!.requestAppInfo(appId)
        }

        if (!accountHasAccess(appId, depotId)) {
            logI("Depot $depotId is not available from this account.")
            return null
        }

        if (innerManifestId == INVALID_MANIFEST_ID) {
            innerManifestId = getSteam3DepotManifest(depotId, appId, innerBranch)
            if (innerManifestId == INVALID_MANIFEST_ID && innerBranch.equals(DEFAULT_BRANCH, ignoreCase = true)) {
                logI("Warning: Depot $depotId does not have branch named \"$innerBranch\". Trying $DEFAULT_BRANCH branch.")
                innerBranch = DEFAULT_BRANCH
                innerManifestId = getSteam3DepotManifest(depotId, appId, innerBranch)
            }

            if (innerManifestId == INVALID_MANIFEST_ID) {
                logI("Depot $depotId missing public subsection or manifest section.")
                return null
            }
        }

        steam3!!.requestDepotKey(depotId, appId)
        val depotKey = steam3!!.depotKeys[depotId]
        if (depotKey == null) {
            logI("No valid depot key for $depotId, unable to download.")
            return null
        }

        val uVersion = getSteam3AppBuildNumber(appId, innerBranch)

        var containingAppId = 0
        var installDirectory = Paths.get("")
        if (!createDirectories(depotId, uVersion) { installDir ->
                // For depots that are proxied through depotfromapp,
                // we still need to resolve the proxy app id, unless the app is freetodownload
                containingAppId = appId
                val proxyAppId = getSteam3DepotProxyAppId(depotId, appId)
                if (proxyAppId != INVALID_APP_ID) {
                    val common = getSteam3AppSection(appId, EAppInfoSection.Common)
                    if (common == null || !common["FreeToDownload"].asBoolean()) {
                        containingAppId = proxyAppId
                    }
                }
                installDirectory = installDir

                true
            }
        ) {
            logI("Error: Unable to create install directories!")
            return null
        }

        return DepotDownloadInfo(depotId, containingAppId, innerManifestId, innerBranch, installDirectory, depotKey)
    }

    private data class ChunkMatch(
        val oldChunk: ChunkData,
        val newChunk: ChunkData,
    )

    private data class DepotFilesData(
        val depotDownloadInfo: DepotDownloadInfo,
        val depotCounter: DepotDownloadCounter,
        val stagingDir: Path,
        val manifest: DepotManifest,
        val previousManifest: DepotManifest?,
        val filteredFiles: MutableList<FileData>,
        val allFileNames: MutableSet<String>,
    ) {
        override fun toString(): String = "DepotFilesData(" +
            "depotDownloadInfo=$depotDownloadInfo, " +
            "depotCounter=$depotCounter, " +
            "stagingDir='$stagingDir', " +
            "manifest=$manifest," +
            " previousManifest=$previousManifest," +
            " filteredFiles=$filteredFiles," +
            " allFileNames=$allFileNames" +
            ")"
    }

    private data class FileStreamData(
        var fileStream: FileChannel? = null,
        val fileLock: Semaphore,
        val chunksToDownload: AtomicInteger,
    )

    private data class GlobalDownloadCounter(
        var completeDownloadSize: Long = 0,
        var totalBytesCompressed: Long = 0,
        var totalBytesUncompressed: Long = 0,
    )

    private class DepotDownloadCounter(
        var completeDownloadSize: Long = 0,
        var sizeDownloaded: Long = 0,
        var depotBytesCompressed: Long = 0,
        var depotBytesUncompressed: Long = 0,
    )

    private suspend fun downloadSteam3Async(
        scope: CoroutineScope,
        depots: List<DepotDownloadInfo>,
    ) = withContext(scope.coroutineContext) {
        _downloadProgress.value = DownloadProgress.Preparing(
            appId = depots.firstOrNull()?.appId ?: 0,
            totalDepots = depots.size
        )

        cdnPool.updateServerList()

        val downloadCounter = GlobalDownloadCounter()
        val depotsToDownload = ArrayList<DepotFilesData>(depots.size)
        val allFileNamesAllDepots = HashSet<String>()

        // First, fetch all the manifests for each depot (including previous manifests) and perform the initial setup
        depots.forEach { depot ->
            val depotFileData = processDepotManifestAndFiles(this, depot, downloadCounter)

            if (depotFileData != null) {
                depotsToDownload.add(depotFileData)
                allFileNamesAllDepots.addAll(depotFileData.allFileNames)
            }

            ensureActive()
        }

        // If we're about to write all the files to the same directory, we will need to first de-duplicate any files by path
        // This is in last-depot-wins order, from Steam or the list of depots supplied by the user
        if (config.installDirectory.isNotBlank() && depotsToDownload.isNotEmpty()) {
            val claimedFileNames = HashSet<String>()

            for (i in depotsToDownload.indices.reversed()) {
                // For each depot, remove all files from the list that have been claimed by a later depot
                depotsToDownload[i].filteredFiles.removeAll { file -> claimedFileNames.contains(file.fileName) }

                // Add all file names from this depot to the claimed files set
                claimedFileNames.addAll(depotsToDownload[i].allFileNames)
            }
        }

        try {
            depotsToDownload.forEach { depotFileData ->
                downloadSteam3AsyncDepotFiles(this, downloadCounter, depotFileData, allFileNamesAllDepots)
            }

            // logI(
            //     "Total downloaded: ${downloadCounter.totalBytesCompressed} " +
            //         "bytes (${downloadCounter.totalBytesUncompressed} bytes uncompressed) " +
            //         "from ${depots.size} depots",
            // )

            _downloadProgress.value = DownloadProgress.Completed(
                bytesDownloaded = downloadCounter.totalBytesCompressed,
                compressed = downloadCounter.totalBytesCompressed,
                uncompressed = downloadCounter.totalBytesUncompressed,
                depots = depots.size
            )
        } catch (e: Exception) {
            // Set error state
            _downloadProgress.value = DownloadProgress.Error(
                message = e.message ?: "Unknown error occurred during download",
                appId = depots.firstOrNull()?.appId ?: 0
            )
            throw e
        } finally {
            delay(100) // Artificial delay to allow state flows to complete.
            logI("Clearing buffer pool")
            BufferPool.clear()
            _downloadProgress.value = DownloadProgress.Idle
        }
    }

    @Suppress("VARIABLE_WITH_REDUNDANT_INITIALIZER")
    private suspend fun processDepotManifestAndFiles(
        scope: CoroutineScope,
        depot: DepotDownloadInfo,
        downloadCounter: GlobalDownloadCounter,
    ): DepotFilesData? {
        val depotCounter = DepotDownloadCounter()

        logI("Processing depot ${depot.depotId}")

        var oldManifest: DepotManifest? = null
        var newManifest: DepotManifest? = null
        val configDir = depot.installDir.resolve(CONFIG_DIR)

        var lastManifestId = INVALID_MANIFEST_ID
        DepotConfigStore.instance!!.installedManifestIDs[depot.depotId]?.let {
            lastManifestId = it
        }

        // In case we have an early exit, this will force equiv of verifyall next run.
        DepotConfigStore.instance!!.installedManifestIDs[depot.depotId] = INVALID_MANIFEST_ID
        DepotConfigStore.save()

        if (lastManifestId != INVALID_MANIFEST_ID) {
            // We only have to show this warning if the old manifest ID was different
            val badHashWarning = (lastManifestId != depot.manifestId)
            oldManifest = loadManifestFromFile(configDir, depot.depotId, lastManifestId, badHashWarning)
        }

        if (lastManifestId == depot.manifestId && oldManifest != null) {
            newManifest = oldManifest
            logI("Already have manifest ${depot.manifestId} for depot ${depot.depotId}.")
        } else {
            newManifest = loadManifestFromFile(configDir, depot.depotId, depot.manifestId, true)

            if (newManifest != null) {
                logI("Already have manifest ${depot.manifestId} for depot ${depot.depotId}.")
            } else {
                logI("Downloading depot ${depot.depotId} manifest")

                var manifestRequestCode = 0L
                var manifestRequestCodeExpiration = LocalDateTime.MIN

                do {
                    scope.ensureActive()

                    var connection: Server? = null

                    try {
                        connection = cdnPool.getConnection()

                        var cdnToken: String? = null
                        val authTokenCallbackPromise = steam3!!.cdnAuthTokens[depot.depotId to connection.host]
                        if (authTokenCallbackPromise != null) {
                            val result = authTokenCallbackPromise.await()
                            cdnToken = result.token
                        }

                        val now = LocalDateTime.now()

                        // In order to download this manifest, we need the current manifest request code
                        // The manifest request code is only valid for a specific period in time
                        if (manifestRequestCode == 0L || now >= manifestRequestCodeExpiration) {
                            manifestRequestCode = steam3!!.getDepotManifestRequestCodeAsync(
                                depotId = depot.depotId,
                                appId = depot.appId,
                                manifestId = depot.manifestId,
                                branch = depot.branch,
                            )
                            // This code will hopefully be valid for one period following the issuing period
                            manifestRequestCodeExpiration = now.plusMinutes(5)

                            // If we could not get the manifest code, this is a fatal error
                            if (manifestRequestCode == 0L) {
                                scope.cancel("Could not get the manifest code")
                            }
                        }

                        logI(
                            "Downloading manifest ${depot.manifestId} from $connection with " +
                                if (cdnPool.proxyServer != null) cdnPool.proxyServer.toString() else "no proxy",
                        )

                        newManifest = cdnPool.cdnClient.downloadManifest(
                            depotId = depot.depotId,
                            manifestId = depot.manifestId,
                            manifestRequestCode = manifestRequestCode.toULong(),
                            server = connection,
                            depotKey = depot.depotKey,
                            proxyServer = cdnPool.proxyServer,
                            cdnAuthToken = cdnToken,
                        )

                        cdnPool.returnConnection(connection)
                    } catch (ex: CancellationException) {
                        logI("Connection timeout downloading depot manifest ${depot.depotId} ${depot.manifestId}. Retrying.")
                    } catch (e: SteamKitWebRequestException) {
                        logE(null, e)

                        // If the CDN returned 403, attempt to get a cdn auth if we didn't yet
                        if (e.statusCode == 403 &&
                            !steam3!!.cdnAuthTokens.containsKey(depot.depotId to connection!!.host)
                        ) {
                            steam3!!.requestCDNAuthToken(depot.appId, depot.depotId, connection)

                            cdnPool.returnConnection(connection)

                            continue
                        }

                        cdnPool.returnBrokenConnection(connection)

                        if (e.statusCode == 401 || e.statusCode == 403) {
                            logI("Encountered ${e.statusCode} for depot manifest ${depot.depotId} ${depot.manifestId}. Aborting.")
                            break
                        }

                        if (e.statusCode == 404) {
                            logI("Encountered 404 for depot manifest ${depot.depotId} ${depot.manifestId}. Aborting.")
                            break
                        }

                        logI("Encountered error downloading depot manifest ${depot.depotId} ${depot.manifestId}: ${e.statusCode}")
                    } catch (e: Exception) {
                        cdnPool.returnBrokenConnection(connection)
                        logI("Encountered error downloading manifest for depot ${depot.depotId} ${depot.manifestId}: ${e.message}")
                    }
                } while (newManifest == null)

                if (newManifest == null) {
                    val msg = "Unable to download manifest ${depot.manifestId} for depot ${depot.depotId}."
                    logE(msg)
                    scope.cancel(msg)
                }

                saveManifestToFile(configDir, newManifest!!)
            }
        }

        logI("Manifest ${depot.manifestId} (${newManifest.creationTime})")

        if (config.downloadManifestOnly) {
            dumpManifestToTextFile(depot, newManifest)
            return null
        }

        val stagingDir = depot.installDir.resolve(STAGING_DIR)

        val filesAfterExclusions = newManifest.files
            .chunked(1000) // Split into chunks for processing
            .flatMap { chunk -> chunk.filter { f -> testIsFileIncluded(f.fileName) } }
            .toMutableList()
        val allFileNames = HashSet<String>(filesAfterExclusions.size)

        // Pre-process
        filesAfterExclusions.forEach { file ->
            allFileNames.add(file.fileName)

            val fileFinalPath = depot.installDir.resolve(file.fileName)
            val fileStagingPath = stagingDir.resolve(file.fileName)

            if (file.flags.contains(EDepotFileFlag.Directory)) {
                Files.createDirectories(fileFinalPath)
                Files.createDirectories(fileStagingPath)
            } else {
                // Some manifests don't explicitly include all necessary directories
                Files.createDirectories(fileFinalPath.parent)
                Files.createDirectories(fileStagingPath.parent)

                downloadCounter.completeDownloadSize += file.totalSize
                depotCounter.completeDownloadSize += file.totalSize
            }
        }

        return DepotFilesData(
            depotDownloadInfo = depot,
            depotCounter = depotCounter,
            stagingDir = stagingDir,
            manifest = newManifest,
            previousManifest = oldManifest,
            filteredFiles = filesAfterExclusions,
            allFileNames = allFileNames,
        )
    }

    private suspend fun downloadSteam3AsyncDepotFiles(
        scope: CoroutineScope,
        downloadCounter: GlobalDownloadCounter,
        depotFilesData: DepotFilesData,
        allFileNamesAllDepots: HashSet<String>,
    ) = withContext(scope.coroutineContext) {
        val depot = depotFilesData.depotDownloadInfo
        val depotCounter = depotFilesData.depotCounter

        logI("Downloading depot ${depot.depotId}")

        val files = depotFilesData.filteredFiles.filter { !it.flags.contains(EDepotFileFlag.Directory) }.toTypedArray()
        val networkChunkQueue = ConcurrentLinkedQueue<Triple<FileStreamData, FileData, ChunkData>>()

        // Process files in parallel with limited concurrency
        val limitedDispatcher = Dispatchers.IO.limitedParallelism(config.maxDownloads)

        // First process all files to collect chunks in parallel (equivalent to C#'s first Parallel.ForEachAsync)
        withContext(limitedDispatcher) {
            files.map { file ->
                async {
                    downloadSteam3AsyncDepotFile(
                        scope = this,
                        downloadCounter = downloadCounter,
                        depotFilesData = depotFilesData,
                        file = file,
                        networkChunkQueue = networkChunkQueue,
                    )
                }
            }.awaitAll() // Wait for all file processing tasks to complete
        }

        // Then download all collected chunks in parallel (equivalent to C#'s second Parallel.ForEachAsync)
        withContext(limitedDispatcher) {
            val chunks = networkChunkQueue.toList() // Create a snapshot of the queue
            chunks.map { (fileStreamData, fileData, chunk) ->
                async {
                    downloadSteam3AsyncDepotFileChunk(
                        scope = this,
                        downloadCounter = downloadCounter,
                        depotFilesData = depotFilesData,
                        fileData = fileData,
                        fileStreamData = fileStreamData,
                        chunk = chunk,
                    )
                }
            }.awaitAll() // Wait for all chunk download tasks to complete
        }

        // Check for deleted files if updating the depot
        depotFilesData.previousManifest.let { previousManifest ->
            val previousFilteredFiles = previousManifest?.files
                ?.asSequence()
                ?.filter { testIsFileIncluded(it.fileName) }
                ?.map { it.fileName }
                ?.toMutableSet()
                ?: mutableSetOf()

            // Check if we are writing to a single output directory. If not, each depot folder is managed independently
            if (config.installDirectory.isBlank()) {
                // Of the list of files in the previous manifest, remove any file names that exist in the current set of all file names
                previousFilteredFiles.removeAll(depotFilesData.allFileNames)
            } else {
                // Of the list of files in the previous manifest, remove any file names that exist in the current set of all file names across all depots being downloaded
                previousFilteredFiles.removeAll(allFileNamesAllDepots)
            }

            previousFilteredFiles.forEach { existingFileName ->
                val fileFinalPath = depot.installDir.resolve(existingFileName)

                if (!Files.exists(fileFinalPath)) {
                    return@forEach
                }

                Files.delete(fileFinalPath)
                logI("Deleted $fileFinalPath")
            }
        }

        DepotConfigStore.instance!!.installedManifestIDs[depot.depotId] = depot.manifestId
        DepotConfigStore.save()

        logI(
            "Depot ${depot.depotId} - Downloaded ${depotCounter.depotBytesCompressed}" +
                " bytes (${depotCounter.depotBytesUncompressed} bytes uncompressed)",
        )
    }

    private suspend fun downloadSteam3AsyncDepotFile(
        scope: CoroutineScope,
        downloadCounter: GlobalDownloadCounter,
        depotFilesData: DepotFilesData,
        file: FileData,
        networkChunkQueue: ConcurrentLinkedQueue<Triple<FileStreamData, FileData, ChunkData>>,
    ) = withContext(scope.coroutineContext) {
        scope.ensureActive() // Check if the coroutine is still active

        val depot = depotFilesData.depotDownloadInfo
        val stagingDir = depotFilesData.stagingDir
        val depotDownloadCounter = depotFilesData.depotCounter
        val oldProtoManifest = depotFilesData.previousManifest
        val oldManifestFile = oldProtoManifest?.files?.find { f -> f.fileName == file.fileName }

        val fileFinalPath = depot.installDir.resolve(file.fileName)
        val fileStagingPath = stagingDir.resolve(file.fileName)

        // This may still exist if the previous run exited before cleanup
        if (Files.exists(fileStagingPath)) {
            Files.delete(fileStagingPath)
        }

        val neededChunks: MutableList<ChunkData>
        val fileExists = Files.exists(fileFinalPath)

        if (!fileExists) {
            logI("Pre-allocating $fileFinalPath")

            // Create new file. Need all chunks
            try {
                Files.createFile(fileFinalPath)
                FileChannel.open(fileFinalPath, StandardOpenOption.WRITE).use { channel ->
                    channel.truncate(file.totalSize)
                }
            } catch (ex: IOException) {
                throw ContentDownloaderException("Failed to allocate file $fileFinalPath: ${ex.message}")
            }

            neededChunks = file.chunks.toMutableList()
        } else {
            // Open existing
            if (oldManifestFile != null) {
                neededChunks = mutableListOf()

                val hashMatches = oldManifestFile.fileHash.contentEquals(file.fileHash)
                if (config.verifyAll || !hashMatches) {
                    // We have a version of this file, but it doesn't fully match what we want
                    if (config.verifyAll) {
                        logI("Validating $fileFinalPath")
                    }

                    val matchingChunks = mutableListOf<ChunkMatch>()

                    for (chunk in file.chunks) {
                        val oldChunk = oldManifestFile.chunks.find { c -> c.chunkID.contentEquals(chunk.chunkID) }
                        if (oldChunk != null) {
                            matchingChunks.add(ChunkMatch(oldChunk, chunk))
                        } else {
                            neededChunks.add(chunk)
                        }
                    }

                    val orderedChunks = matchingChunks.sortedBy { it.oldChunk.offset }
                    val copyChunks = mutableListOf<ChunkMatch>()

                    FileChannel.open(fileFinalPath, StandardOpenOption.READ).use { channel ->
                        // Find the largest chunk size to allocate buffer once
                        val maxChunkSize = orderedChunks.maxOfOrNull { it.oldChunk.uncompressedLength } ?: 0
                        val buffer = ByteBuffer.allocate(maxChunkSize)

                        for (match in orderedChunks) {
                            channel.position(match.oldChunk.offset)
                            buffer.clear()
                            buffer.limit(match.oldChunk.uncompressedLength)

                            // Read the exact amount needed
                            var bytesRead = 0
                            while (bytesRead < match.oldChunk.uncompressedLength) {
                                val read = channel.read(buffer)
                                if (read == -1) break // End of file
                                bytesRead += read
                            }

                            buffer.flip()
                            val tmp = ByteArray(buffer.remaining())
                            buffer.get(tmp)

                            val adler = Utils.adlerHash(tmp)
                            if (adler != match.oldChunk.checksum) {
                                neededChunks.add(match.newChunk)
                            } else {
                                copyChunks.add(match)
                            }
                        }
                    }

                    if (!hashMatches || neededChunks.isNotEmpty()) {
                        Files.move(fileFinalPath, fileStagingPath)

                        FileChannel.open(fileStagingPath, StandardOpenOption.READ).use { sourceChannel ->
                            FileChannel.open(fileFinalPath, StandardOpenOption.WRITE, StandardOpenOption.CREATE)
                                .use { destChannel ->
                                    try {
                                        destChannel.truncate(file.totalSize)
                                    } catch (ex: IOException) {
                                        throw ContentDownloaderException(
                                            "Failed to resize file to expected size $fileFinalPath: ${ex.message}",
                                        )
                                    }

                                    // Buffer that will be reused for all chunks
                                    val largestChunkSize =
                                        copyChunks.maxOfOrNull { it.oldChunk.uncompressedLength } ?: 0
                                    val buffer = ByteBuffer.allocate(largestChunkSize)

                                    for (match in copyChunks) {
                                        sourceChannel.position(match.oldChunk.offset)
                                        buffer.clear()
                                        buffer.limit(match.oldChunk.uncompressedLength)

                                        // Read the chunk data
                                        var bytesRead = 0
                                        while (bytesRead < match.oldChunk.uncompressedLength) {
                                            val read = sourceChannel.read(buffer)
                                            if (read == -1) break // End of file
                                            bytesRead += read
                                        }

                                        // Prepare buffer for reading and write to destination
                                        buffer.flip()
                                        destChannel.position(match.newChunk.offset)

                                        while (buffer.hasRemaining()) {
                                            destChannel.write(buffer)
                                        }
                                    }
                                }
                        }

                        Files.delete(fileStagingPath)
                    }
                }
            } else {
                // No old manifest or file not in old manifest. We must validate.
                FileChannel.open(fileFinalPath, StandardOpenOption.READ, StandardOpenOption.WRITE).use { channel ->
                    val fileSize = Files.size(fileFinalPath)
                    if (fileSize != file.totalSize) {
                        try {
                            channel.truncate(file.totalSize)
                        } catch (ex: IOException) {
                            throw ContentDownloaderException("Failed to allocate file $fileFinalPath: ${ex.message}")
                        }
                    }

                    logI("Validating $fileFinalPath")
                    neededChunks = Utils.validateSteam3FileChecksums(
                        channel,
                        file.chunks.sortedBy { it.offset }.toTypedArray(),
                    ).toMutableList()
                }
            }

            if (neededChunks.isEmpty()) {
                synchronized(depotDownloadCounter) {
                    depotDownloadCounter.sizeDownloaded += file.totalSize

                    val percentage =
                        (depotDownloadCounter.sizeDownloaded.toFloat() / depotDownloadCounter.completeDownloadSize.toFloat()) * 100.0f
                    _downloadProgress.value = DownloadProgress.Downloading(
                        percentageComplete = percentage,
                        bytesDownloaded = depotDownloadCounter.sizeDownloaded,
                        totalBytes = depotDownloadCounter.completeDownloadSize,
                        currentFile = file.fileName,
                        depotId = depot.depotId
                    )
                }

                synchronized(downloadCounter) {
                    downloadCounter.completeDownloadSize -= file.totalSize
                }

                return@withContext
            }

            val sizeOnDisk = file.totalSize - neededChunks.sumOf { it.uncompressedLength }
            synchronized(depotDownloadCounter) {
                depotDownloadCounter.sizeDownloaded += sizeOnDisk
            }

            synchronized(downloadCounter) {
                downloadCounter.completeDownloadSize -= sizeOnDisk
            }
        }

        val fileIsExecutable = file.flags.contains(EDepotFileFlag.Executable)
        if (fileIsExecutable &&
            (!fileExists || oldManifestFile == null || !oldManifestFile.flags.contains(EDepotFileFlag.Executable))
        ) {
            setExecutable(fileFinalPath, true)
        } else if (!fileIsExecutable && oldManifestFile != null && oldManifestFile.flags.contains(EDepotFileFlag.Executable)) {
            setExecutable(fileFinalPath, false)
        }

        val fileStreamData = FileStreamData(
            fileStream = null,
            fileLock = Semaphore(1),
            chunksToDownload = AtomicInteger(neededChunks.size),
        )

        for (chunk in neededChunks) {
            networkChunkQueue.add(Triple(fileStreamData, file, chunk))
        }
    }

    private suspend fun downloadSteam3AsyncDepotFileChunk(
        scope: CoroutineScope,
        downloadCounter: GlobalDownloadCounter,
        depotFilesData: DepotFilesData,
        fileData: FileData,
        fileStreamData: FileStreamData,
        chunk: ChunkData,
    ) = withContext(scope.coroutineContext) {
        scope.ensureActive()

        val depot = depotFilesData.depotDownloadInfo
        val depotDownloadCounter = depotFilesData.depotCounter

        val chunkID = Strings.toHex(chunk.chunkID).lowercase()

        var written = 0

        BufferPool.useBuffer(chunk.uncompressedLength) { chunkBuffer ->
            try {
                do {
                    scope.ensureActive()

                    var connection: Server? = null

                    try {
                        connection = cdnPool.getConnection()

                        var cdnToken: String? = null
                        steam3!!.cdnAuthTokens[depot.depotId to connection.host]?.let { authTokenCallbackPromise ->
                            val result = authTokenCallbackPromise.await()
                            cdnToken = result.token
                        }

                        // TODO state flow this?
                        // logI("Downloading chunk $chunkID from $connection with ${cdnPool.proxyServer ?: "no proxy"}")

                        written = cdnPool.cdnClient.downloadDepotChunk(
                            depotId = depot.depotId,
                            chunk = chunk,
                            server = connection,
                            destination = chunkBuffer,
                            depotKey = depot.depotKey,
                            proxyServer = cdnPool.proxyServer,
                            cdnAuthToken = cdnToken,
                        )

                        cdnPool.returnConnection(connection)
                        break
                    } catch (e: CancellationException) {
                        logE("Connection timeout downloading chunk $chunkID", e)
                        cdnPool.returnBrokenConnection(connection)
                        throw e
                    } catch (e: SteamKitWebRequestException) {
                        logE("Beans", e)
                        // If the CDN returned 403, attempt to get a cdn auth if we didn't yet,
                        // if auth task already exists, make sure it didn't complete yet, so that it gets awaited above
                        if (e.statusCode == 403 &&
                            (
                                !steam3!!.cdnAuthTokens.containsKey(depot.depotId to connection?.host) ||
                                    !steam3!!.cdnAuthTokens[depot.depotId to connection?.host]!!.isDone
                                )
                        ) {
                            steam3!!.requestCDNAuthToken(depot.appId, depot.depotId, connection!!)
                            cdnPool.returnConnection(connection)
                            continue
                        }

                        cdnPool.returnBrokenConnection(connection)

                        if (e.statusCode == 401 || e.statusCode == 403) {
                            logI("Encountered ${e.statusCode} for chunk $chunkID. Aborting.")
                            break
                        }

                        logI("Encountered error downloading chunk $chunkID: ${e.statusCode}")
                    } catch (e: Exception) {
                        cdnPool.returnBrokenConnection(connection)
                        logI("Encountered unexpected error downloading chunk $chunkID: ${e.message}")
                    }
                } while (written == 0)

                if (written == 0) {
                    val msg = "Failed to find any server with chunk $chunkID for depot ${depot.depotId}. Aborting."
                    logI(msg)
                    scope.cancel(msg)
                    return@withContext
                }

                scope.ensureActive()

                try {
                    fileStreamData.fileLock.acquire()

                    if (fileStreamData.fileStream == null) {
                        val fileFinalPath = depot.installDir.resolve(fileData.fileName)
                        fileStreamData.fileStream = FileChannel.open(
                            fileFinalPath,
                            StandardOpenOption.READ,
                            StandardOpenOption.WRITE,
                        )
                    }

                    val buffer = ByteBuffer.wrap(chunkBuffer, 0, written)
                    fileStreamData.fileStream?.position(chunk.offset)
                    fileStreamData.fileStream?.write(buffer)
                } finally {
                    fileStreamData.fileLock.release()
                }
            } catch (e: Exception) {
                throw e
            }
        }

        val remainingChunks = fileStreamData.chunksToDownload.decrementAndGet()
        if (remainingChunks == 0) {
            fileStreamData.fileStream?.close()
            fileStreamData.fileLock.release()
        }

        var sizeDownloaded: Long
        synchronized(depotDownloadCounter) {
            sizeDownloaded = depotDownloadCounter.sizeDownloaded + written
            depotDownloadCounter.sizeDownloaded = sizeDownloaded
            depotDownloadCounter.depotBytesCompressed += chunk.compressedLength
            depotDownloadCounter.depotBytesUncompressed += chunk.uncompressedLength
        }

        synchronized(downloadCounter) {
            downloadCounter.totalBytesCompressed += chunk.compressedLength
            downloadCounter.totalBytesUncompressed += chunk.uncompressedLength
        }

        if (remainingChunks == 0) {
            val fileFinalPath = depot.installDir.resolve(fileData.fileName)
            val percentage = (sizeDownloaded.toFloat() / depotDownloadCounter.completeDownloadSize.toFloat()) * 100.0f
            val msg = String.format("%6.2f", percentage)

            // TODO stateflow this?
            // logI("$msg% $fileFinalPath")

            _downloadProgress.value = DownloadProgress.Downloading(
                percentageComplete = percentage,
                bytesDownloaded = depotDownloadCounter.sizeDownloaded,
                totalBytes = depotDownloadCounter.completeDownloadSize,
                currentFile = fileData.fileName,
                depotId = depot.depotId
            )
        }
    }

    private fun dumpManifestToTextFile(depot: DepotDownloadInfo, manifest: DepotManifest) {
        logI("Dumping manifest to file")

        val txtManifest = depot.installDir.resolve("manifest_${depot.depotId}_${depot.manifestId}.txt")
        Files.newBufferedWriter(txtManifest, StandardCharsets.UTF_8).use { writer ->
            writer.write("Content Manifest for Depot ${depot.depotId} \n")
            writer.write("\n")
            writer.write("Manifest ID / date     : ${depot.manifestId} / ${manifest.creationTime} \n")

            val uniqueChunks = mutableSetOf<ByteArray>().apply {
                val chunkCompare = object : (ByteArray, ByteArray) -> Boolean {
                    override fun invoke(a: ByteArray, b: ByteArray): Boolean = a.contentEquals(b)
                }
                manifest.files.flatMap { it.chunks }.forEach { chunk ->
                    if (none { chunkCompare(it, chunk.chunkID!!) }) {
                        add(chunk.chunkID!!)
                    }
                }
            }

            writer.write("Total number of files  : ${manifest.files.size} \n")
            writer.write("Total number of chunks : ${uniqueChunks.size} \n")
            writer.write("Total bytes on disk    : ${manifest.totalUncompressedSize} \n")
            writer.write("Total bytes compressed : ${manifest.totalCompressedSize} \n")
            writer.write("\n\n")
            writer.write("          Size Chunks File SHA                                 Flags Name\n")

            for (file in manifest.files) {
                val sha1Hash = Strings.toHex(file.fileHash).lowercase()
                writer.write(
                    String.format(
                        "%14d %6d %s %5d %s\n",
                        file.totalSize,
                        file.chunks.size,
                        sha1Hash,
                        EDepotFileFlag.code(file.flags),
                        file.fileName,
                    ),
                )
            }
        }
    }
}
