package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.depotdownloader.data.DepotDownloadCounter
import `in`.dragonbra.javasteam.depotdownloader.data.DepotFilesData
import `in`.dragonbra.javasteam.depotdownloader.data.GlobalDownloadCounter
import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.UGCDetailsCallback
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.SteamKitWebRequestException
import `in`.dragonbra.javasteam.util.log.LogManager
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.*
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.apache.commons.lang3.SystemUtils
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Date
import kotlin.concurrent.atomics.ExperimentalAtomicApi
import kotlin.math.log


// TODO kotlin support first, java compat after.
// TODO remove suppression
@Suppress("unused")
class ContentDownloader(private val steam3: Steam3Session) : AutoCloseable {

    private val config: DownloadConfig
        get() = steam3.config

    private var cdnPool: CDNClientPool? = null

    override fun close() {
        HttpClientFactory.close()
        cdnPool?.close()
    }

    /**
     * @return a [Pair] if successful and install directory.
     */
    private fun createDirectories(depotId: Int, depotVersion: Int): Pair<Boolean, String> = try {
        val installDir = if (config.installDirectory.isBlank()) {
            if (SystemUtils.IS_OS_ANDROID) {
                // Android should have install dir.
                throw IllegalArgumentException("installDirectory shouldn't be blank on android")
            }

            // Create default directory structure
            val defaultDownloadDir = DEFAULT_DOWNLOAD_DIR.toPath()
            FileSystem.SYSTEM.createDirectories(defaultDownloadDir)

            val depotPath = defaultDownloadDir / depotId.toString()
            FileSystem.SYSTEM.createDirectories(depotPath)

            val versionPath = depotPath / depotVersion.toString()
            FileSystem.SYSTEM.createDirectories(versionPath)
            FileSystem.SYSTEM.createDirectories(versionPath / CONFIG_DIR)
            FileSystem.SYSTEM.createDirectories(versionPath / STAGING_DIR)

            versionPath.toString()
        } else {
            val configDir = steam3.config.installDirectory.toPath()
            FileSystem.SYSTEM.createDirectories(configDir)
            FileSystem.SYSTEM.createDirectories(configDir / CONFIG_DIR)
            FileSystem.SYSTEM.createDirectories(configDir / STAGING_DIR)

            configDir.toString()
        }

        Pair(true, installDir)
    } catch (e: Exception) {
        logger.error(e)
        Pair(false, "")
    }

    private fun testIsFileIncluded(filename: String): Boolean {
        if (!config.usingFileList) return true

        val newFileName = filename.replace('\\', '/')

        if (config.filesToDownload.contains(newFileName)) {
            return true
        }

        config.filesToDownloadRegex.forEach { rgx ->
            if (rgx.matches(newFileName)) {
                return true
            }
        }

        return false
    }

    private suspend fun accountHasAccess(appId: Int, depotId: Int): Boolean {
        val steamUser = requireNotNull(steam3.steamUser)
        val steamID = requireNotNull(steamUser.steamID)
        if (steam3.licenses.isEmpty() && steamID.accountType != EAccountType.AnonUser) {
            return false
        }

        val licenseQuery = arrayListOf<Int>()
        if (steamID.accountType == EAccountType.AnonUser) {
            licenseQuery.add(17906)
        } else {
            licenseQuery.addAll(steam3.licenses.map { it.packageID }.distinct())
        }

        steam3.requestPackageInfo(licenseQuery)

        licenseQuery.forEach { license ->
            steam3.packageInfo[license]?.let { pkg ->
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

    private fun getSteam3AppSection(appId: Int, section: EAppInfoSection): KeyValue? {
        if (steam3.appInfo.isEmpty()) {
            return null
        }

        val app = steam3.appInfo[appId] ?: return null

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

    private suspend fun getSteam3DepotManifest(depotId: Int, appId: Int, branch: String): Long {
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
                logger.error("App $appId, Depot $depotId has depotfromapp of $otherAppId!")
                return INVALID_MANIFEST_ID
            }

            steam3.requestAppInfo(otherAppId)

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
        if (config.betaPassword.isEmpty()) {
            logger.error("Branch $branch for depot $depotId was not found, either it does not exist or it has a password.")
            return INVALID_MANIFEST_ID
        }

        if (!steam3.appBetaPasswords.contains(branch)) {
            // Submit the password to Steam now to get encryption keys
            steam3.checkAppBetaPassword(appId, config.betaPassword)

            if (!steam3.appBetaPasswords.contains(branch)) {
                logger.error("Error: Password was invalid for branch $branch (or the branch does not exist)")
                return INVALID_MANIFEST_ID
            }
        }

        // Got the password, request private depot section
        // TODO: We're probably repeating this request for every depot?
        val privateDepotSection = steam3.getPrivateBetaDepotSection(appId, branch)

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

    private fun getAppName(appId: Int): String {
        val info = getSteam3AppSection(appId, EAppInfoSection.Common) ?: KeyValue.INVALID
        return info["name"].asString() ?: ""
    }

    suspend fun downloadPubfileAsync(appId: Int, publishedFileId: Long) {
        val details = steam3.getPublishedFileDetails(appId, PublishedFileID(publishedFileId))

        requireNotNull(details) // TODO maybe?

        if (!details.fileUrl.isNullOrEmpty()) {
            downloadWebFile(
                appId = appId,
                fileName = details.filename,
                url = details.fileUrl
            )
        } else if (details.hcontentFile > 0L) {
            downloadAppAsync(
                appId = appId,
                depotManifestIds = listOf(appId to details.hcontentFile),
                branch = DEFAULT_BRANCH,
                os = null,
                arch = null,
                language = null,
                lv = false,
                isUgc = true
            )
        } else {
            logger.error("Unable to locate manifest ID for published file $publishedFileId")
        }
    }

    suspend fun downloadUGCAsync(appId: Int, ugcId: Long) {
        var details: UGCDetailsCallback? = null

        if (steam3.steamUser!!.steamID!!.accountType != EAccountType.AnonUser) {
            details = steam3.getUGCDetails(UGCHandle(ugcId.toLong()))
        } else {
            logger.error("Unable to query UGC details for $ugcId from an anonymous account")
        }

        if (details!!.url.isNotEmpty()) {
            downloadWebFile(appId, details.fileName, details.url)
        } else {
            downloadAppAsync(appId, listOf(appId to ugcId), DEFAULT_BRANCH, null, null, null, false, true)
        }
    }

    private suspend fun downloadWebFile(
        appId: Int,
        fileName: String,
        url: String,
    ) = withContext(Dispatchers.IO) {
        val result = createDirectories(appId, 0)

        if (!result.first) {
            logger.error("Error: Unable to create install directories!")
            return@withContext
        }

        val fileSystem = FileSystem.SYSTEM

        val installDir = result.second.toPath()
        val stagingDir = installDir / STAGING_DIR
        val fileStagingPath = stagingDir / fileName
        val fileFinalPath = installDir / fileName

        fileSystem.createDirectories(fileFinalPath.parent!!)
        fileSystem.createDirectories(fileStagingPath.parent!!)

        fileSystem.write(fileStagingPath) {
            HttpClientFactory.httpClient.use { client ->
                println("Downloading $fileName")

                val response = client.get(url)
                val channel = response.body<ByteReadChannel>()

                // Use fixed buffer to avoid allocations
                val buffer = ByteArray(8192) // Fixed 8KB buffer

                while (!channel.isClosedForRead) {
                    val bytesRead = channel.readAvailable(buffer)
                    if (bytesRead > 0) {
                        write(buffer, 0, bytesRead)
                    }
                }
            }
        }

        if (fileSystem.exists(fileFinalPath)) {
            fileSystem.delete(fileFinalPath)
        }

        fileSystem.atomicMove(fileStagingPath, fileFinalPath)
    }

    private suspend fun downloadAppAsync(
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
        cdnPool = CDNClientPool(steam3, appId)

        // Load our configuration data containing the depots currently installed
        var configPath = config.installDirectory
        if (configPath.isBlank()) {
            configPath = DEFAULT_DOWNLOAD_DIR
        }

        FileSystem.SYSTEM.createDirectories(configPath.toPath() / CONFIG_DIR)
        // DepotConfigStore.LoadFromFile(Path.Combine(configPath, CONFIG_DIR, "depot.config")) // TODO

        steam3.requestAppInfo(appId)

        if (!accountHasAccess(appId, appId)) {
            if (steam3.steamUser!!.steamID!!.accountType != EAccountType.AnonUser && steam3.requestFreeAppLicense(appId)) {
                logger.debug("Obtained FreeOnDemand license for app $appId")

                // Fetch app info again in case we didn't get it fully without a license.
                steam3.requestAppInfo(appId, true)
            } else {
                val contentName = getAppName(appId)
                throw ContentDownloaderException("App $appId ($contentName) is not available from this account.")
            }
        }

        val hasSpecificDepots = depotManifestIds.isNotEmpty()
        val depotIdsFound = arrayListOf<Int>()
        val depotIdsExpected = depotManifestIds.map { x -> x.first }.toMutableList()
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)

        if (isUgc) {
            val workshopDepot = depots?.get("workshopdepot")!!.asInteger()
            if (workshopDepot != 0 && !depotIdsExpected.contains(workshopDepot)) {
                depotIdsExpected.add(workshopDepot)
                depotManifestIds = depotManifestIds.map { workshopDepot to it.second }.toMutableList()
            }

            depotIdsFound.addAll(depotIdsExpected)
        } else {
            logger.debug("Using app branch: '$branch'.")

            depots?.children?.forEach { depotSection ->
                var id: Int? = INVALID_DEPOT_ID
                if (depotSection.children.isEmpty())
                    return@forEach

                id = depotSection.name?.toIntOrNull()
                if (id == null)
                    return@forEach

                if (hasSpecificDepots && !depotIdsExpected.contains(id))
                    return@forEach

                if (!hasSpecificDepots) {
                    val depotConfig = depotSection["config"]
                    if (depotConfig != KeyValue.INVALID) {
                        if (!config.downloadAllPlatforms &&
                            depotConfig["oslist"] != KeyValue.INVALID &&
                            !depotConfig["oslist"].value.isNullOrBlank()
                        ) {
                            val oslist = depotConfig["oslist"].value?.split(',')
                            if (oslist?.indexOf(os ?: Util.getSteamOS()) == -1)
                                return@forEach
                        }

                        if (!config.downloadAllArchs &&
                            depotConfig["osarch"] != KeyValue.INVALID &&
                            !depotConfig["osarch"].value.isNullOrBlank()
                        ) {
                            val depotArch = depotConfig["osarch"].value
                            if (depotArch != (arch ?: Util.getSteamArch()))
                                return@forEach
                        }

                        if (!config.downloadAllLanguages &&
                            depotConfig["language"] != KeyValue.INVALID &&
                            !depotConfig["language"].value.isNullOrBlank()
                        ) {
                            val depotLang = depotConfig["language"].value
                            if (depotLang != (language ?: "english"))
                                return@forEach
                        }

                        if (!lv &&
                            depotConfig["lowviolence"] != KeyValue.INVALID &&
                            depotConfig["lowviolence"].asBoolean()
                        )
                            return@forEach
                    }
                }

                depotIdsFound.add(id)

                if (!hasSpecificDepots)
                    depotManifestIds.add(id to INVALID_MANIFEST_ID)
            }

            if (depotManifestIds.size == 0 && !hasSpecificDepots) {
                throw ContentDownloaderException("Couldn't find any depots to download for app $appId")
            }

            if (depotIdsFound.size < depotIdsExpected.size) {
                val remainingDepotIds = depotIdsExpected.minus(depotIdsFound)
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

        logger.debug("")

        try {
            downloadSteam3Async(infos)
        } catch (e: Exception) {
            logger.error("App $appId was not completely downloaded.", e)
            throw e
        }
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun downloadSteam3Async(depots: List<DepotDownloadInfo>) = withContext(Dispatchers.IO) {

        requireNotNull(cdnPool)

        // TODO Indeterminate loading

        cdnPool!!.updateServerList()

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
        if (config.installDirectory.isNotBlank() && depotsToDownload.isNotEmpty()) {
            val claimedFileNames = hashSetOf<String>()

            for (i in depotsToDownload.indices.reversed()) {
                // For each depot, remove all files from the list that have been claimed by a later depot
                depotsToDownload[i].filteredFiles.removeAll { file -> claimedFileNames.contains(file.fileName) }

                claimedFileNames.union(depotsToDownload[i].allFileNames)
            }
        }

        // TODO couldn't this be async?
        depotsToDownload.forEach { depotFilesData ->
            downloadSteam3depotFiles(downloadCounter, depotFilesData, allFileNamesAllDepots)
        }

        // TODO provide a callback with the info below.

        logger.debug(
            "Total downloaded: ${downloadCounter.totalBytesCompressed} bytes " +
                "(${downloadCounter.totalBytesUncompressed} bytes uncompressed) from ${depots.size} depots"
        )
    }

    @OptIn(ExperimentalAtomicApi::class)
    private suspend fun processDepotManifestAndFiles(
        depot: DepotDownloadInfo,
        downloadCounter: GlobalDownloadCounter,
    ): DepotFilesData? = withContext(Dispatchers.IO) {
        val depotCounter = DepotDownloadCounter()

        // TODO provide feedback for Android
        logger.debug("Processing depot ${depot.depotId}")

        var oldManifest: DepotManifest? = null
        var newManifest: DepotManifest? = null
        val configDir = depot.installDir.toPath() / CONFIG_DIR

        val lastManifestId = INVALID_MANIFEST_ID
        // DepotConfigStore.Instance.InstalledManifestIDs.TryGetValue(depot.DepotId, out lastManifestId)

        // TODO(s)?
        // In case we have an early exit, this will force equiv of verifyall next run.
        // DepotConfigStore.Instance.InstalledManifestIDs[depot.DepotId] = INVALID_MANIFEST_ID
        // DepotConfigStore.Save()

        if (lastManifestId != INVALID_MANIFEST_ID) {
            // We only have to show this warning if the old manifest ID was different
            val badHashWarning = (lastManifestId != depot.manifestId)
            oldManifest = Util.loadManifestFromFile(configDir, depot.depotId, lastManifestId, badHashWarning)
        }

        if (lastManifestId == depot.manifestId && oldManifest != null) {
            newManifest = oldManifest
            logger.debug("Already have manifest ${depot.manifestId} for depot ${depot.depotId}.")
        } else {
            newManifest = Util.loadManifestFromFile(configDir, depot.depotId, depot.manifestId, true)

            if (newManifest != null) {
                logger.debug("Already have manifest ${depot.manifestId} for depot ${depot.depotId}.")
            } else {
                logger.debug("Downloading depot ${depot.depotId} manifest")

                var manifestRequestCode = 0L
                var manifestRequestCodeExpiration = Instant.MIN

                do {
                    ensureActive()

                    var connection: Server? = null

                    try {
                        connection = cdnPool!!.getConnection()

                        var cdnToken: String? = null

                        val authTokenCallbackPromise = steam3.cdnAuthTokens[depot.depotId to connection.host]

                        if (authTokenCallbackPromise != null) {
                            val result = authTokenCallbackPromise.await()
                            cdnToken = result.token
                        }

                        val now = Instant.now()

                        // In order to download this manifest, we need the current manifest request code
                        // The manifest request code is only valid for a specific period in time
                        if (manifestRequestCode == 0L || now >= manifestRequestCodeExpiration) {
                            manifestRequestCode = steam3.getDepotManifestRequestCode(
                                depot.depotId,
                                depot.appId,
                                depot.manifestId,
                                depot.branch
                            )

                            // This code will hopefully be valid for one period following the issuing period
                            manifestRequestCodeExpiration = now.plus(5, ChronoUnit.MINUTES)

                            // If we could not get the manifest code, this is a fatal error
                            if (manifestRequestCode == 0L) {
                                cancel("manifestRequestCode is 0")
                            }
                        }

                        logger.debug(
                            "Downloading manifest ${depot.manifestId} from $connection" +
                                "with ${cdnPool!!.proxyServer ?: "no proxy"}"
                        )

                        newManifest = cdnPool?.cdnClient?.downloadManifest(
                            depotId = depot.depotId,
                            manifestId = depot.manifestId,
                            manifestRequestCode = manifestRequestCode,
                            server = connection,
                            depotKey = depot.depotKey,
                            proxyServer = cdnPool!!.proxyServer,
                            cdnAuthToken = cdnToken
                        )

                        cdnPool?.returnConnection(connection)
                    } catch (e: CancellationException) {
                        logger.error("Connection timeout downloading depot manifest ${depot.depotId} ${depot.manifestId}. Retrying.")
                    } catch (e: SteamKitWebRequestException) {
                        // If the CDN returned 403, attempt to get a cdn auth if we didn't yet
                        if (e.statusCode == 403 && !steam3.cdnAuthTokens.contains(depot.depotId to connection!!.host)) {
                            steam3.requestCDNAuthToken(depot.appId, depot.depotId, connection)

                            cdnPool?.returnConnection(connection)

                            continue
                        }

                        cdnPool?.returnConnection(connection)

                        // 401 & 403
                        if (e.statusCode == 401 || e.statusCode == 403) {
                            logger.error(
                                "Encountered ${depot.depotId} for depot " +
                                    "manifest ${depot.manifestId} ${e.statusCode}. Aborting.",
                            )
                            break
                        }

                        // 404
                        if (e.statusCode == 404) {
                            logger.error(
                                "Encountered 404 for depot manifest " +
                                    "${depot.depotId} ${depot.manifestId}. Aborting."
                            )
                            break
                        }

                        // Other
                        logger.error(
                            "Encountered error downloading depot manifest " +
                                "${depot.depotId} ${depot.manifestId}: ${e.statusCode}"
                        )
                    } catch (e: CancellationException) {
                        logger.error("CancellationException", e)
                        break
                    } catch (e: Exception) {
                        cdnPool?.returnConnection(connection)
                        logger.error("Encountered error downloading manifest for depot ${depot.depotId} ${depot.manifestId}: ${e.message}")
                    }
                } while (newManifest == null)

                if (newManifest == null) {
                    val msg = "Unable to download manifest ${depot.manifestId} for depot ${depot.depotId}"
                    logger.error(msg)
                    cancel(msg)
                }

                // Throw the cancellation exception if requested so that this task is marked failed
                ensureActive()

                Util.saveManifestToFile(configDir, newManifest)
            }
        }

        requireNotNull(newManifest)

        logger.debug("Manifest ${depot.manifestId} (${newManifest.creationTime})")

        if (config.downloadManifestOnly) {
            dumpManifestToTextFile(depot, newManifest)
            return@withContext null
        }

        val stagingDir = depot.installDir.toPath() / STAGING_DIR

        val filesAfterExclusions = newManifest.files.filter { testIsFileIncluded(it.fileName) }

        val allFileNames = LinkedHashSet<String>(filesAfterExclusions.size)

        // Pre-process
        filesAfterExclusions.forEach { file ->
            allFileNames.add(file.fileName)

            val fileFinalPath = depot.installDir.toPath().resolve(file.fileName)
            val fileStagingPath = stagingDir.resolve(file.fileName)

            val fileSystem = FileSystem.SYSTEM
            if (file.flags.contains(EDepotFileFlag.Directory)) {
                fileSystem.createDirectories(fileFinalPath)
                fileSystem.createDirectories(fileStagingPath)
            } else {
                // Some manifests don't explicitly include all necessary directories
                fileSystem.createDirectories(fileFinalPath.parent!!)
                fileSystem.createDirectories(fileStagingPath.parent!!)

                downloadCounter.completeDownloadSize.fetchAndAdd(file.totalSize)
                depotCounter.completeDownloadSize.fetchAndAdd(file.totalSize)
            }
        }

        return@withContext DepotFilesData(
            depotDownloadInfo = depot,
            depotCounter = depotCounter,
            stagingDir = stagingDir.toString(),
            manifest = newManifest,
            previousManifest = oldManifest,
            filteredFiles = filesAfterExclusions.toCollection(ArrayList()),
            allFileNames = allFileNames
        )
    }

    private suspend fun getDepotInfo(depotId: Int, appId: Int, manifestId: Long, branch: String): DepotDownloadInfo? {
        var manifestId = manifestId
        var branch = branch

        if (appId != INVALID_APP_ID) {
            steam3.requestAppInfo(appId)
        }

        if (!accountHasAccess(appId, depotId)) {
            logger.error("Depot $depotId is not available from this account.")
            return null
        }

        if (manifestId == INVALID_MANIFEST_ID) {
            manifestId = getSteam3DepotManifest(depotId, appId, branch)

            if (manifestId == INVALID_MANIFEST_ID && !branch.equals(DEFAULT_BRANCH, true)) {
                logger.error("Warning: Depot $depotId does not have branch named \"$branch\". Trying $DEFAULT_BRANCH branch.\"")
                branch = DEFAULT_BRANCH
                manifestId = getSteam3DepotManifest(depotId, appId, branch)
            }

            if (manifestId == INVALID_MANIFEST_ID) {
                logger.error("Depot $depotId missing public subsection or manifest section.")
                return null
            }
        }

        steam3.requestDepotKey(depotId, appId)
        val depotKey = steam3.depotKeys[depotId]
        if (depotKey == null) {
            logger.error("No valid depot key for $depotId, unable to download.")
            return null
        }

        val uVersion = getSteam3AppBuildNumber(appId, branch)

        val result = createDirectories(depotId, uVersion)
        if (!result.first) {
            logger.error("Error: Unable to create install directories!")
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

        return DepotDownloadInfo(depotId, containingAppId, manifestId, branch, result.second, depotKey)
    }

    companion object {
        private val logger = LogManager.getLogger(ContentDownloader::class.java)

        const val INVALID_APP_ID: Int = Int.MAX_VALUE
        const val INVALID_DEPOT_ID: Int = Int.MAX_VALUE
        const val INVALID_MANIFEST_ID: Long = Long.MAX_VALUE
        const val DEFAULT_BRANCH: String = "public"

        private const val DEFAULT_DOWNLOAD_DIR: String = "depots"
        private const val CONFIG_DIR: String = ".DepotDownloader"
        private const val STAGING_DIR = "$CONFIG_DIR/staging"

        @JvmStatic
        fun dumpManifestToTextFile(depot: DepotDownloadInfo, manifest: DepotManifest?) {
            if (manifest == null) {
                logger.error("dumpManifestToTextFile: manifest is null")
                return
            }

            val txtManifest = "${depot.installDir}/manifest_${depot.depotId}_${depot.manifestId}.txt".toPath()

            FileSystem.SYSTEM.sink(txtManifest).buffer().use { sink ->
                sink.writeUtf8("Content Manifest for Depot ${depot.depotId}\n")
                sink.writeUtf8("\n")
                sink.writeUtf8("Manifest ID / date     : ${depot.manifestId} / ${manifest.creationTime}\n")

                val uniqueChunkCount = manifest.files.asSequence()
                    .flatMap { it.chunks.asSequence() }
                    .mapNotNull { it.chunkID }
                    .map { ChunkId(it) }
                    .distinct()
                    .count()

                sink.writeUtf8("Total number of files  : ${manifest.files.size}\n")
                sink.writeUtf8("Total number of chunks : ${uniqueChunkCount}\n")
                sink.writeUtf8("Total bytes on disk    : ${manifest.totalUncompressedSize}\n")
                sink.writeUtf8("Total bytes compressed : ${manifest.totalCompressedSize}\n")
                sink.writeUtf8("\n")
                sink.writeUtf8("\n")
                sink.writeUtf8("          Size Chunks File SHA                                 Flags Name\n")

                manifest.files.forEach { file ->
                    sink.writeUtf8("${file.totalSize.toString().padStart(14)} ")
                    sink.writeUtf8("${file.chunks.size.toString().padStart(6)} ")
                    sink.writeUtf8("${file.fileHash.toHexString().lowercase()} ")
                    sink.writeUtf8("${EDepotFileFlag.code(file.flags).toString().padStart(5)} ")
                    sink.writeUtf8("${file.fileName}\n")
                }
            }
        }
    }
}
