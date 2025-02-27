package `in`.dragonbra.javasteam.contentdownloader

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
import `in`.dragonbra.javasteam.util.compat.readNBytesCompat
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import `in`.dragonbra.javasteam.util.log.LogManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.CancellationException
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.FileWriter
import java.io.IOException
import java.io.RandomAccessFile
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.*

@Suppress("MemberVisibilityCanBePrivate")
object ContentDownloader {

    const val INVALID_APP_ID = Int.MAX_VALUE

    // const val INVALID_DEPOT_ID = Int.MAX_VALUE
    const val INVALID_MANIFEST_ID = Long.MAX_VALUE
    const val DEFAULT_BRANCH = "public"

    var config: DownloadConfig = DownloadConfig()

    private var steam3: Steam3Session? = null
    private var cdnPool: CDNClientPool? = null

    const val DEFAULT_DOWNLOAD_DIR = "depots"
    const val CONFIG_DIR = ".DepotDownloader"
    private val STAGING_DIR = Paths.get(CONFIG_DIR, "staging").toString()

    private val logger = LogManager.getLogger(ContentDownloader::class.java)

    private val mutex = Mutex()

    fun createDirectories(depotId: Int, depotVersion: Int): DirectoryResult = runCatching {
        val installDir: String?

        if (config.installDirectory.isNullOrBlank()) {
            File(DEFAULT_DOWNLOAD_DIR).mkdirs()
            val depotPath = File(DEFAULT_DOWNLOAD_DIR, depotId.toString()).path
            File(depotPath).mkdirs()
            installDir = File(depotPath, depotVersion.toString()).path
            File(installDir!!).mkdirs()
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
        if (steam3 == null || steam3?.steamUser?.steamID == null || (steam3?.licenses == null && steam3?.steamUser?.steamID?.accountType != EAccountType.AnonUser)) {
            return false
        }

        val licenseQuery: Iterable<Int> = if (steam3!!.steamUser.steamID.accountType == EAccountType.AnonUser) {
            listOf(17906)
        } else {
            steam3!!.licenses.map { it.packageID }.distinct()
        }

        steam3!!.requestPackageInfo(licenseQuery)

        licenseQuery.forEach { license ->
            val pkg = steam3!!.packageInfo[license]

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

            steam3!!.requestAppInfo(otherAppId)

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
                    steam3!!.checkAppBetaPassword(appId, password.orEmpty())

                    val appBetaPassword = steam3!!.appBetaPasswords[branch]
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

    fun initializeSteam3(username: String?, password: String?): Boolean {
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
                this.loginID = config.loginID?.takeIf { it > 0 } ?: 0x4A564D53 // "JVMS"
            }
        )

        if (!steam3!!.waitForCredentials()) {
            logger.error("Unable to get steam3 credentials.")
            return false
        }

        CoroutineScope(Dispatchers.Default).launch {
            steam3!!.tickCallbacks(this)
        }

        return true
    }

    fun shutdownSteam3() {
        cdnPool?.shutdown()
        cdnPool = null

        steam3?.disconnect()
    }

    suspend fun downloadPubfile(appId: Int, publishedFileId: Long) {
        val details = steam3!!.getPublishedFileDetails(appId, PublishedFileID(publishedFileId))

        if (!details.fileUrl.isNullOrEmpty()) {
            downloadWebFile(appId, details.filename, details.filename)
        } else if (details.hcontentFile > 0) {
            downloadApp(
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
            logger.error("Unable to locate manifest ID for published file $publishedFileId")
        }
    }

    suspend fun downloadUGC(appId: Int, ugcId: Long) {
        var details: UGCDetailsCallback? = null

        if (steam3!!.steamUser.steamID.accountType != EAccountType.AnonUser) {
            details = steam3!!.getUGCDetails(UGCHandle(ugcId))
        } else {
            logger.error("Unable to query UGC details for $ugcId from anonymous account.")
        }

        if (!details?.url.isNullOrEmpty()) {
            downloadWebFile(appId, details.fileName, details.url)
        } else {
            downloadApp(
                appId = appId,
                depotManifestIds = listOf(DepotManifestIds(appId, ugcId)),
                branch = DEFAULT_BRANCH,
                os = null,
                arch = null,
                language = null,
                lv = false,
                isUgc = true
            )
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

    suspend fun downloadApp(
        appId: Int,
        depotManifestIds: List<DepotManifestIds>,
        branch: String,
        os: String? = null,
        arch: String? = null,
        language: String? = null,
        lv: Boolean,
        isUgc: Boolean,
    ): Unit = withContext(Dispatchers.IO) {
        var ids = depotManifestIds.toMutableList()

        cdnPool = CDNClientPool(steam3!!, appId, this)

        // Load our configuration data containing the depots currently installed
        var configPath = config.installDirectory
        if (configPath.isNullOrEmpty()) {
            configPath = DEFAULT_DOWNLOAD_DIR
        }

        File(Paths.get(configPath, CONFIG_DIR).toString()).mkdirs()
        DepotConfigStore.loadFromFile(Paths.get(configPath, CONFIG_DIR, "depot.config").toString())

        steam3!!.requestAppInfo(appId)

        if (!accountHasAccess(appId, appId)) {
            if (steam3!!.requestFreeAppLicense(appId)) {
                logger.debug("Obtained FreeOnDemand license for app $appId")

                // Fetch app info again in case we didn't get it fully without a license.
                steam3!!.requestAppInfo(appId, true)
            } else {
                val contentName = getAppName(appId)
                throw ContentDownloaderException("App $appId ($contentName) is not available from this account.")
            }
        }
        val hasSpecificDepots = ids.isNotEmpty()
        val depotIdsFound = mutableListOf<Int>()
        val depotIdsExpected = ids.map { it.depotId }.toMutableList()
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)

        if (isUgc) {
            val workshopDepot = depots?.get("workshopdepot")?.asInteger() ?: throw Exception("depots was null") // ??
            if (workshopDepot != 0 && !depotIdsExpected.contains(workshopDepot)) {
                depotIdsExpected.add(workshopDepot)
                ids = ids.map {
                    DepotManifestIds(workshopDepot, it.manifestId)
                }.toMutableList()
            }

            depotIdsFound.addAll(depotIdsExpected)
        } else {
            logger.debug("Using app branch: $branch")

            depots?.children?.forEach { depotSection ->
                if (depotSection.children.isEmpty()) {
                    return@forEach
                }

                val id: Int = depotSection.name.toIntOrNull() ?: return@forEach
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
                            if (targetOs !in oslist) {
                                return@forEach
                            }
                        }

                        if (!config.downloadAllArchs &&
                            depotConfig["osarch"] != KeyValue.INVALID &&
                            !depotConfig["osarch"].value.isNullOrBlank()
                        ) {
                            val depotArch = depotConfig["osarch"].value
                            val targetArch = arch ?: Util.getSteamArch()
                            if (depotArch != targetArch) {
                                return@forEach
                            }
                        }

                        if (!config.downloadAllLanguages &&
                            depotConfig["language"] != KeyValue.INVALID &&
                            !depotConfig["language"].value.isNullOrBlank()
                        ) {
                            val depotLang = depotConfig["language"].value
                            val targetLang = language ?: "english"
                            if (depotLang != targetLang) {
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
                    ids.add((DepotManifestIds(id, INVALID_MANIFEST_ID)))
                }
            }

            if (ids.isEmpty() && !hasSpecificDepots) {
                throw ContentDownloaderException("Couldn't find any depots to download for app $appId")
            }
        }

        val infos = mutableListOf<DepotDownloadInfo>()

        ids.forEach { (depotId, manifestId) ->
            val info = getDepotInfo(depotId, appId, manifestId, branch)
            if (info != null) {
                infos.add(info)
            }
        }

        try {
            downloadSteam3(infos)
        } catch (e: CancellationException) {
            logger.error("App $appId was not completely downloaded.", e)
            throw e
        } catch (e: Exception) {
            logger.error("downloadSteam3 error", e)
        }
    }

    suspend fun getDepotInfo(depotId: Int, appId: Int, manifestId: Long, branch: String): DepotDownloadInfo? {
        var id = manifestId
        var depotBranch = branch
        if (steam3 != null && appId != INVALID_APP_ID) {
            steam3!!.requestAppInfo(appId)
        }

        if (!accountHasAccess(appId, depotId)) {
            logger.error("Depot $depotId is not available from this account.")

            return null
        }

        if (id == INVALID_MANIFEST_ID) {
            id = getSteam3DepotManifest(depotId, appId, depotBranch)

            if (id == INVALID_MANIFEST_ID &&
                depotBranch.equals(
                    DEFAULT_BRANCH,
                    ignoreCase = true
                )
            ) {
                logger.error("Warning: Depot $depotId does not have branch named \"$depotBranch\". Trying $DEFAULT_BRANCH branch.")
                depotBranch = DEFAULT_BRANCH
                id = getSteam3DepotManifest(depotId, appId, depotBranch)
            }

            if (id == INVALID_MANIFEST_ID) {
                logger.error("Depot $depotId missing public subsection or manifest section.")
                return null
            }
        }

        steam3!!.requestDepotKey(depotId, appId)
        val depotKey = steam3!!.depotKeys[depotId]
        if (depotKey == null) {
            logger.error("No valid depot key for $depotId, unable to download.")
            return null
        }

        val uVersion = getSteam3AppBuildNumber(appId, depotBranch)

        when (val installDir = createDirectories(depotId, uVersion)) {
            is DirectoryResult.Success -> {
                // For depots that are proxied through depotfromapp, we still need to resolve the proxy app id
                var containingAppId = appId
                val proxyAppId = getSteam3DepotProxyAppId(depotId, appId)
                if (proxyAppId != INVALID_APP_ID) {
                    containingAppId = proxyAppId
                }

                return DepotDownloadInfo(depotId, containingAppId, id, depotBranch, installDir.installDir, depotKey)
            }

            DirectoryResult.Failed -> {
                logger.error("Error: Unable to create install directories!")
                return null
            }
        }
    }

    private suspend fun downloadSteam3(depots: List<DepotDownloadInfo>): Unit = withContext(Dispatchers.IO) {
        val downloadCounter = GlobalDownloadCounter()
        val depotsToDownload = ArrayList<DepotFilesData>(depots.size)
        val allFileNamesAllDepots = hashSetOf<String>()

        // First, fetch all the manifests for each depot (including previous manifests) and perform the initial setup
        depots.forEach { depot ->
            val depotFileData = processDepotManifestAndFiles(depot, downloadCounter)

            if (depotFileData != null) {
                depotsToDownload.add(depotFileData)
                allFileNamesAllDepots.addAll(depotFileData.allFileNames)
            }
        }

        // If we're about to write all the files to the same directory, we will need to first de-duplicate any files by path
        // This is in last-depot-wins order, from Steam or the list of depots supplied by the user
        if (!config.installDirectory.isNullOrBlank() && depotsToDownload.isNotEmpty()) {
            val claimedFileNames = mutableSetOf<String>()

            for (i in depotsToDownload.indices.reversed()) {
                // For each depot, remove all files from the list that have been claimed by a later depot
                depotsToDownload[i].filteredFiles.removeAll { file ->
                    claimedFileNames.contains(file.fileName)
                }
                claimedFileNames.addAll(depotsToDownload[i].allFileNames)
            }
        }

        depotsToDownload.forEach { depotFileData ->
            downloadSteam3DepotFiles(downloadCounter, depotFileData, allFileNamesAllDepots)
        }

        logger.debug(
            "Total downloaded: ${downloadCounter.totalBytesCompressed} bytes " +
                "(${downloadCounter.totalBytesUncompressed} bytes uncompressed) from ${depots.size} depots"
        )
    }

    private suspend fun processDepotManifestAndFiles(
        depot: DepotDownloadInfo,
        downloadCounter: GlobalDownloadCounter,
    ): DepotFilesData? = withContext(Dispatchers.IO) {
        val depotCounter = DepotDownloadCounter()

        logger.debug("Processing depot ${depot.depotId}")

        var oldManifest: DepotManifest? = null
        var newManifest: DepotManifest?

        val configDir = Paths.get(depot.installDir, CONFIG_DIR)

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
            oldManifest = Util.loadManifestFromFile(configDir.toString(), depot.depotId, lastManifestId, badHashWarning)
        }

        if (lastManifestId == depot.manifestId && oldManifest != null) {
            newManifest = oldManifest
            logger.debug("Already have manifest ${depot.manifestId} for depot ${depot.depotId}")
        } else {
            newManifest = Util.loadManifestFromFile(configDir.toString(), depot.depotId, depot.manifestId, true)

            if (newManifest != null) {
                logger.debug("Already have manifest ${depot.manifestId} for depot ${depot.depotId}")
            } else {
                logger.debug("Downloading depot ${depot.depotId} manifest")

                var manifestRequestCode = 0UL
                var manifestRequestCodeExpiration = LocalDateTime.MIN

                do {
                    var connection: Server? = null

                    try {
                        connection = cdnPool!!.getConnection().await()

                        var cdnToken: String? = null
                        val authTokenCallbackPromise = steam3!!.cdnAuthTokens[Pair(depot.depotId, connection!!.host)]
                        if (authTokenCallbackPromise != null) {
                            val result = authTokenCallbackPromise.await()
                            cdnToken = result.token
                        }

                        val now = LocalDateTime.now()

                        // In order to download this manifest, we need the current manifest request code
                        // The manifest request code is only valid for a specific period in time
                        if (manifestRequestCode == 0UL || now >= manifestRequestCodeExpiration) {
                            manifestRequestCode = steam3!!.getDepotManifestRequestCode(
                                depot.depotId,
                                depot.appId,
                                depot.manifestId,
                                depot.branch,
                            )

                            // This code will hopefully be valid for one period following the issuing period
                            manifestRequestCodeExpiration = now.plusMinutes(5)

                            // If we could not get the manifest code, this is a fatal error
                            if (manifestRequestCode == 0UL) {
                                cancel()
                            }
                        }

                        logger.debug(
                            "Downloading manifest ${depot.manifestId} from $connection with " +
                                if (cdnPool!!.proxyServer != null) cdnPool!!.proxyServer.toString() else "no proxy"
                        )

                        newManifest = cdnPool!!.cdnClient.downloadManifest(
                            depotId = depot.depotId,
                            manifestId = depot.manifestId,
                            manifestRequestCode = manifestRequestCode,
                            server = connection,
                            depotKey = depot.depotKey,
                            proxyServer = cdnPool!!.proxyServer,
                            cdnAuthToken = cdnToken,
                        )

                        cdnPool!!.returnConnection(connection)
                    } catch (e: CancellationException) {
                        logger.error(
                            "Connection timeout downloading depot manifest " +
                                "${depot.depotId} ${depot.manifestId}. Retrying.",
                            e
                        )
                    } catch (e: SteamKitWebRequestException) {
                        // If the CDN returned 403, attempt to get a cdn auth if we didn't yet
                        if (e.statusCode == 403 && !steam3!!.cdnAuthTokens.containsKey(depot.depotId to connection!!.host)) {
                            steam3!!.requestCDNAuthToken(depot.appId, depot.depotId, connection)

                            continue
                        }

                        cdnPool!!.returnBrokenConnection(connection)

                        if (e.statusCode == 401 || e.statusCode == 403) {
                            logger.error("Encountered ${depot.depotId} for depot manifest ${depot.manifestId} ${e.statusCode}. Aborting.")
                            break
                        }

                        if (e.statusCode == 404) {
                            logger.error("Encountered 404 for depot manifest ${depot.depotId} ${depot.manifestId}. Aborting.")
                            break
                        }

                        logger.error("Encountered error downloading depot manifest ${depot.depotId} ${depot.manifestId}: ${e.statusCode}")
                    }
                } while (newManifest == null)

                if (newManifest == null) {
                    logger.error("\nUnable to download manifest ${depot.manifestId} for depot ${depot.depotId}")
                    // TODO cts cancel
                }

                // Throw the cancellation exception if requested so that this task is marked failed
                // TODO ?? cts.Token.ThrowIfCancellationRequested()

                Util.saveManifestToFile(configDir.toString(), newManifest!!)
            }
        }

        logger.debug("Manifest $depot ($newManifest)")

        if (config.downloadManifestOnly) {
            dumpManifestToTextFile(depot, newManifest)
            return@withContext null
        }

        val stagingDir = Paths.get(depot.installDir, STAGING_DIR)

        val filesAfterExclusions = newManifest.files.asSequence()
            .filter { testIsFileIncluded(it.fileName) }
            .toList()
        val allFileNames = hashSetOf<String>()

        // Pre-process
        filesAfterExclusions.forEach { file ->
            allFileNames.add(file.fileName)

            val fileFinalPath = Paths.get(depot.installDir, file.fileName)
            val fileStagingPath = Paths.get(stagingDir.toString(), file.fileName)

            if (file.flags.contains(EDepotFileFlag.Directory)) {
                Files.createDirectories(fileFinalPath.parent)
                Files.createDirectories(fileStagingPath.parent)
            } else {
                // Some manifests don't explicitly include all necessary directories
                Files.createDirectories(fileFinalPath.parent)
                Files.createDirectories(fileStagingPath.parent)

                downloadCounter.completeDownloadSize += file.totalSize
                depotCounter.completeDownloadSize += file.totalSize
            }
        }

        return@withContext DepotFilesData(
            depotDownloadInfo = depot,
            depotCounter = depotCounter,
            stagingDir = stagingDir.toString(),
            manifest = newManifest,
            previousManifest = oldManifest,
            filteredFiles = filesAfterExclusions.toMutableList(),
            allFileNames = allFileNames
        )
    }

    private suspend fun downloadSteam3DepotFiles(
        downloadCounter: GlobalDownloadCounter,
        depotFilesData: DepotFilesData,
        allFileNamesAllDepots: HashSet<String>,
    ) {
        val depot = depotFilesData.depotDownloadInfo
        val depotCounter = depotFilesData.depotCounter

        logger.debug("Downloading depot ${depot.depotId}")

        val files = depotFilesData.filteredFiles
            .filter { !it.flags.contains(EDepotFileFlag.Directory) }
            .toList()

        val networkChunkQueue = ConcurrentLinkedQueue<Triple<FileStreamData, FileData, ChunkData>>()

        // TODO maybe properly do coroutines here.

        // Download files
        Util.invokeAsync(
            files.map { file ->
                suspend {
                    downloadSteam3DepotFile(downloadCounter, depotFilesData, file, networkChunkQueue)
                }
            },
            maxDegreeOfParallelism = config.maxDownloads
        )

        // Download chunks
        Util.invokeAsync(
            networkChunkQueue.map { (fileStreamData, fileData, chunk) ->
                suspend {
                    downloadSteam3DepotFileChunk(
                        downloadCounter = downloadCounter,
                        depotFilesData = depotFilesData,
                        file = fileData,
                        fileStreamData = fileStreamData,
                        chunk = chunk,
                    )
                }
            },
            maxDegreeOfParallelism = config.maxDownloads
        )

        // Check for deleted files if updating the depot.
        if (depotFilesData.previousManifest != null) {
            val previousFilteredFiles = depotFilesData.previousManifest.files
                .asSequence()
                .filter { testIsFileIncluded(it.fileName) }
                .map { it.fileName }
                .toHashSet()

            // Check if we are writing to a single output directory. If not, each depot folder is managed independently
            if (config.installDirectory.isNullOrBlank()) {
                // Of the list of files in the previous manifest, remove any file names that exist in the current set of all file names
                previousFilteredFiles.removeAll(depotFilesData.allFileNames)
            } else {
                // Of the list of files in the previous manifest, remove any file names that exist in the current set of all file names across all depots being downloaded
                previousFilteredFiles.removeAll(allFileNamesAllDepots)
            }

            previousFilteredFiles.forEach { existingFileName ->
                val fileFinalPath = Paths.get(depot.installDir, existingFileName)

                if (!Files.exists(fileFinalPath)) {
                    return@forEach
                }

                Files.delete(fileFinalPath)
                logger.debug("Deleted $fileFinalPath")
            }
        }

        DepotConfigStore.instance!!.installedManifestIDs[depot.depotId] = depot.manifestId
        DepotConfigStore.save()

        logger.debug(
            "Depot ${depot.depotId} - Downloaded ${depotCounter.depotBytesCompressed} " +
                "bytes (${depotCounter.depotBytesUncompressed} bytes uncompressed)",
        )
    }

    private suspend fun downloadSteam3DepotFile(
        downloadCounter: GlobalDownloadCounter,
        depotFilesData: DepotFilesData,
        file: FileData,
        networkChunkQueue: ConcurrentLinkedQueue<Triple<FileStreamData, FileData, ChunkData>>,
    ) = withContext(Dispatchers.IO) {
        if (!isActive) {
            throw CancellationException()
        }

        val depot = depotFilesData.depotDownloadInfo
        val stagingDir = depotFilesData.stagingDir
        val depotDownloadCounter = depotFilesData.depotCounter
        val oldProtoManifest = depotFilesData.previousManifest
        var oldManifestFile: FileData? = null
        if (oldProtoManifest != null) {
            oldManifestFile = oldProtoManifest.files.singleOrNull { it.fileName == file.fileName }
        }

        val fileFinalPath = Paths.get(depot.installDir, file.fileName)
        val fileStagingPath = Paths.get(stagingDir, file.fileName)

        // This may still exist if the previous run exited before cleanup
        if (Files.exists(fileStagingPath)) {
            Files.delete(fileStagingPath)
        }

        var neededChunks: MutableList<ChunkData>
        val fi = Paths.get(fileFinalPath.toString()).toFile()
        val fileDidExist = fi.exists()
        if (!fileDidExist) {
            logger.debug("Pre-allocating $fileFinalPath")

            // create new file. need all chunks
            FileOutputStream(fileFinalPath.toString()).use { fs ->
                try {
                    fs.channel.truncate(file.totalSize)
                } catch (ex: IOException) {
                    throw ContentDownloaderException("Failed to allocate file $fileFinalPath: ${ex.message}")
                }
            }

            neededChunks = file.chunks
        } else {
            // open existing
            if (oldManifestFile != null) {
                neededChunks = mutableListOf()

                val hashMatches = oldManifestFile.fileHash.contentEquals(file.fileHash)
                if (config.verifyAll || !hashMatches) {
                    // we have a version of this file, but it doesn't fully match what we want
                    if (config.verifyAll) {
                        logger.debug("Validating $fileFinalPath")
                    }

                    val matchingChunks = mutableListOf<ChunkMatch>()

                    file.chunks.forEach { chunk ->
                        val oldChunk = oldManifestFile.chunks.firstOrNull { it.chunkID.contentEquals(chunk.chunkID) }
                        if (oldChunk != null) {
                            matchingChunks.add(ChunkMatch(oldChunk, chunk))
                        } else {
                            neededChunks.add(chunk)
                        }
                    }

                    val orderedChunks = matchingChunks.sortedBy { it.oldChunk.offset }

                    val copyChunks = mutableListOf<ChunkMatch>()

                    FileInputStream(fileFinalPath.toString()).use { fsOld ->
                        for (match in orderedChunks) {
                            fsOld.channel.position(match.oldChunk.offset)

                            val tmp = ByteArray(match.oldChunk.uncompressedLength)
                            fsOld.readNBytesCompat(tmp, 0, tmp.size)

                            val adler = Utils.adlerHash(tmp)
                            if (adler != match.oldChunk.checksum) {
                                neededChunks.add(match.newChunk)
                            } else {
                                copyChunks.add(match)
                            }
                        }
                    }

                    if (!hashMatches || neededChunks.isNotEmpty()) {
                        // Move file to staging path
                        Files.move(
                            Paths.get(fileFinalPath.toString()),
                            Paths.get(fileStagingPath.toString()),
                            StandardCopyOption.REPLACE_EXISTING
                        )

                        // Open both files with try-with-resources pattern
                        FileInputStream(fileStagingPath.toString()).use { fsOld ->
                            FileOutputStream(fileFinalPath.toString()).use { fs ->
                                fs.channel.truncate(file.totalSize)

                                // Copy chunks from old file to new file
                                for (match in copyChunks) {
                                    fsOld.channel.position(match.oldChunk.offset)

                                    val tmp = ByteArray(match.oldChunk.uncompressedLength)
                                    fsOld.readNBytesCompat(tmp, 0, tmp.size)

                                    // Write to the new file at the specified position
                                    fs.channel.position(match.newChunk.offset)
                                    fs.write(tmp)
                                }
                            }
                        }

                        // Delete staging file
                        Files.delete(fileStagingPath)
                    }
                }
            } else {
                // No old manifest or file not in old manifest. We must validate.
                RandomAccessFile(fileFinalPath.toString(), "rw").use { fs ->
                    if (fi.length() != file.totalSize) {
                        fs.channel.truncate(file.totalSize)
                    }
                    logger.debug("Validating $fileFinalPath")
                    neededChunks = Utils.validateSteam3FileChecksums(
                        fs,
                        file.chunks.sortedBy { it.offset }.toTypedArray()
                    )
                }
            }

            if (neededChunks.size == 0) {
                synchronized(depotDownloadCounter) {
                    depotDownloadCounter.sizeDownloaded += file.totalSize
                    logger.debug(
                        "%6.2f%% %s".format(
                            (depotDownloadCounter.sizeDownloaded.toFloat() / depotDownloadCounter.completeDownloadSize.toFloat()) * 100.0f,
                            fileFinalPath
                        )
                    )
                }

                synchronized(downloadCounter) {
                    downloadCounter.completeDownloadSize -= file.totalSize
                }

                return@withContext
            }

            val sizeOnDisk = file.totalSize - neededChunks.sumOf { it.uncompressedLength.toLong() }
            synchronized(depotDownloadCounter) {
                depotDownloadCounter.sizeDownloaded += sizeOnDisk
            }

            synchronized(downloadCounter) {
                downloadCounter.completeDownloadSize -= sizeOnDisk
            }
        }

        val fileIsExecutable = file.flags.contains(EDepotFileFlag.Executable)
        if (fileIsExecutable &&
            (!fileDidExist || oldManifestFile == null || !oldManifestFile.flags.contains(EDepotFileFlag.Executable))
        ) {
            fileFinalPath.toFile().setExecutable(true)
        } else if (!fileIsExecutable &&
            oldManifestFile != null &&
            oldManifestFile.flags.contains(EDepotFileFlag.Executable)
        ) {
            fileFinalPath.toFile().setExecutable(false)
        }

        val fileStreamData = FileStreamData(
            fileStream = null,
            fileLock = Semaphore(1),
            chunksToDownload = AtomicInteger(neededChunks.size)
        )

        neededChunks.forEach { chunk ->
            networkChunkQueue.add(Triple(fileStreamData, file, chunk))
        }
    }

    private suspend fun downloadSteam3DepotFileChunk(
        downloadCounter: GlobalDownloadCounter,
        depotFilesData: DepotFilesData,
        file: FileData,
        fileStreamData: FileStreamData,
        chunk: ChunkData,
    ) = withContext(Dispatchers.IO) {
        if (!isActive) {
            throw CancellationException()
        }

        val depot = depotFilesData.depotDownloadInfo
        val depotDownloadCounter = depotFilesData.depotCounter

        val chunkID = chunk.chunkID!!.joinToString("") { "%02x".format(it) }

        var written = 0
        val chunkBuffer = ByteArray(chunk.uncompressedLength)

        try {
            do {
                ensureActive()

                var connection: Server? = null

                try {
                    connection = cdnPool!!.getConnection().await()

                    var cdnToken: String? = null
                    val authTokenCallbackPromise = steam3!!.cdnAuthTokens[depot.depotId to connection!!.host]
                    if (authTokenCallbackPromise != null) {
                        val result = authTokenCallbackPromise.await()
                        cdnToken = result.token
                    }

                    logger.debug(
                        "Downloading chunk $chunkID from $connection with " +
                            if (cdnPool!!.proxyServer != null) cdnPool!!.proxyServer.toString() else "no proxy"
                    )

                    written = cdnPool!!.cdnClient.downloadDepotChunk(
                        depotId = depot.depotId,
                        chunk = chunk,
                        server = connection,
                        destination = chunkBuffer,
                        depotKey = depot.depotKey,
                        proxyServer = cdnPool!!.proxyServer,
                        cdnAuthToken = cdnToken
                    )

                    cdnPool!!.returnConnection(connection)

                    break
                } catch (e: CancellationException) {
                    logger.error("Connection timeout downloading chunk $chunkID", e)
                } catch (e: SteamKitWebRequestException) {
                    // If the CDN returned 403, attempt to get a cdn auth if we didn't yet,
                    // if auth task already exists, make sure it didn't complete yet, so that it gets awaited above
                    val authTokenCallbackPromise = steam3!!.cdnAuthTokens[depot.depotId to connection!!.host]
                    if (e.statusCode == 403 && authTokenCallbackPromise != null || !authTokenCallbackPromise!!.isCompleted) {
                        steam3!!.requestCDNAuthToken(depot.appId, depot.depotId, connection)

                        cdnPool!!.returnConnection(connection)

                        continue
                    }

                    cdnPool!!.returnBrokenConnection(connection)

                    if (e.statusCode == 401 || e.statusCode == 43) {
                        logger.error("Encountered ${e.statusCode} for chunk $chunkID. Aborting.")
                        break
                    }

                    logger.error("Encountered error downloading chunk $chunkID: ${e.statusCode}")
                } catch (e: Exception) {
                    cdnPool!!.returnBrokenConnection(connection)
                    logger.error("Encountered unexpected error downloading chunk $chunkID: ${e.message}")
                }
            } while (written == 0)

            if (written == 0) {
                logger.error("Failed to find any server with chunk $chunkID for depot ${depot.depotId}. Aborting.")
                cancel()
            }

            // Throw the cancellation exception if requested so that this task is marked failed
            ensureActive()

            try {
                fileStreamData.fileLock.acquire()

                if (fileStreamData.fileStream == null) {
                    val fileFinalPath = Paths.get(depot.installDir, file.fileName).toString()
                    fileStreamData.fileStream = FileChannel.open(Paths.get(fileFinalPath), StandardOpenOption.WRITE)
                }

                val buffer = ByteBuffer.wrap(chunkBuffer, 0, written)
                withContext(Dispatchers.IO) {
                    while (buffer.hasRemaining()) {
                        fileStreamData.fileStream?.write(buffer)
                    }
                }
            } finally {
                fileStreamData.fileLock.release()
            }
        } finally {
            chunkBuffer.fill(0)
        }

        val remainingChunks = fileStreamData.chunksToDownload.decrementAndGet()
        if (remainingChunks == 0) {
            fileStreamData.fileStream?.close()
            // fileStreamData.fileLock.()
        }

        var sizeDownloaded = 0L
        synchronized(depotDownloadCounter) {
            sizeDownloaded = depotDownloadCounter.sizeDownloaded + written
            depotDownloadCounter.sizeDownloaded = sizeDownloaded
            depotDownloadCounter.depotBytesCompressed += chunk.compressedLength
            depotDownloadCounter.depotBytesUncompressed += chunk.uncompressedLength
        }

        synchronized(downloadCounter) {
            downloadCounter.totalBytesCompressed += chunk.compressedLength
            downloadCounter.totalBytesUncompressed += chunk.uncompressedLength

            // TODO Callback
            // Ansi.Progress(downloadCounter.totalBytesUncompressed, downloadCounter.completeDownloadSize)
            logger.debug("${downloadCounter.totalBytesUncompressed} -> ${downloadCounter.completeDownloadSize}")
        }

        if (remainingChunks == 0) {
            val fileFinalPath = Paths.get(depot.installDir, file.fileName)

            // TODO probably not good for logging
            logger.debug(
                "%6.2f%% %s".format(
                    (sizeDownloaded.toFloat() / depotDownloadCounter.completeDownloadSize.toFloat()) * 100.0f,
                    fileFinalPath
                )
            )
        }
    }

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
