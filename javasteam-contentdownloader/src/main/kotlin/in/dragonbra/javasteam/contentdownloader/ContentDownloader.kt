package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.contentdownloader.ContentDownloader
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.UGCDetailsCallback
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.Strings
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import `in`.dragonbra.javasteam.util.log.LogManager
import io.ktor.client.HttpClient
import io.ktor.client.engine.cio.CIO
import io.ktor.client.request.get
import io.ktor.client.statement.bodyAsChannel
import io.ktor.utils.io.copyTo
import kotlinx.coroutines.*
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.channels.FileChannel
import java.nio.file.Paths
import java.nio.file.StandardOpenOption
import java.time.LocalDateTime

object ContentDownloader {

    const val INVALID_APP_ID = Int.MAX_VALUE
    const val INVALID_DEPOT_ID = Int.MAX_VALUE
    const val INVALID_MANIFEST_ID = Long.MAX_VALUE
    const val DEFAULT_BRANCH = "public"

    var config: DownloadConfig = DownloadConfig()

    private var steam3: Steam3Session? = null
    private var cdnPool: CDNClientPool? = null

    const val DEFAULT_DOWNLOAD_DIR = "depots"
    const val CONFIG_DIR = ".DepotDownloader"
    private val STAGING_DIR = Paths.get(CONFIG_DIR, "staging").toString()

    private val logger = LogManager.getLogger(ContentDownloader::class.java)

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
            downloadWebFile(appId, details!!.fileName, details.url)
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
        isUgc: Boolean
    ): Unit = withContext(Dispatchers.IO) {
        var depotManifestIds = depotManifestIds.toMutableList()

        cdnPool = CDNClientPool(steam3!!.steamClient, appId, this)

        // Load our configuration data containing the depots currently installed
        var configPath = config.installDirectory
        if (configPath.isNullOrEmpty()) {
            configPath = DEFAULT_DOWNLOAD_DIR
        }

        File(Paths.get(configPath, CONFIG_DIR).toString()).mkdirs()

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

        val hasSpecificDepots = depotManifestIds.isNotEmpty()
        val depotIdsFound = mutableListOf<Int>()
        val depotIdsExpected = depotManifestIds.map { it.depotId }.toMutableList()
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots)

        if (isUgc) {
            val workshopDepot = depots?.get("workshopdepot")?.asInteger() ?: throw Exception("depots was null") // ??
            if (workshopDepot != 0 && !depotIdsExpected.contains(workshopDepot)) {
                depotIdsExpected.add(workshopDepot)
                depotManifestIds = depotManifestIds.map {
                    DepotManifestIds(workshopDepot, it.manifestId)
                }.toMutableList()
            }

            depotIdsFound.addAll(depotIdsExpected)
        } else {
            logger.debug("Using app brach: $branch")

            depots?.children?.forEach { depotSection ->
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
                            if (targetOs !in oslist) {
                                return@forEach
                            }
                        }


                        if (!config.downloadAllArchs &&
                            depotConfig["osarch"] != KeyValue.INVALID &&
                            !depotConfig["osarch"].value.isNullOrBlank()
                        ) {
                            var depotArch = depotConfig["osarch"].value
                            val targetArch = arch ?: Util.getSteamArch()
                            if (depotArch != targetArch) {
                                return@forEach
                            }
                        }

                        if (!config.downloadAllLanguages &&
                            depotConfig["language"] != KeyValue.INVALID &&
                            !depotConfig["language"].value.isNullOrBlank()
                        ) {
                            var depotLang = depotConfig["language"].value
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
                    depotManifestIds.add((DepotManifestIds(id, INVALID_MANIFEST_ID)))
                }
            }

            if (depotManifestIds.isEmpty() && !hasSpecificDepots) {
                throw ContentDownloaderException("Couldn't find any depots to download for app $appId")
            }
        }

        val infos = mutableListOf<DepotDownloadInfo>()

        depotManifestIds.forEach { (depotId, manifestId) ->
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
        }
    }

    suspend fun getDepotInfo(depotId: Int, appId: Int, manifestId: Long, branch: String): DepotDownloadInfo? {
        var manifestId = manifestId
        var branch = branch
        if (steam3 != null && appId != INVALID_APP_ID) {
            steam3!!.requestAppInfo(appId)
        }

        if (!accountHasAccess(appId, depotId)) {
            logger.error("Depot $depotId is not available from this account.")

            return null
        }

        if (manifestId == INVALID_MANIFEST_ID) {
            manifestId = getSteam3DepotManifest(depotId, appId, branch)

            if (manifestId == INVALID_MANIFEST_ID && branch.equals(
                    DEFAULT_BRANCH,
                    ignoreCase = true
                )
            ) {
                logger.error("Warning: Depot $depotId does not have branch named \"$branch\". Trying ${DEFAULT_BRANCH} branch.")
                branch = DEFAULT_BRANCH
                manifestId = getSteam3DepotManifest(depotId, appId, branch)
            }

            if (manifestId == INVALID_MANIFEST_ID) {
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

        val uVersion = getSteam3AppBuildNumber(appId, branch)

        when (val installDir = createDirectories(depotId, uVersion)) {
            is DirectoryResult.Success -> {
                // For depots that are proxied through depotfromapp, we still need to resolve the proxy app id
                var containingAppId = appId
                val proxyAppId = getSteam3DepotProxyAppId(depotId, appId)
                if (proxyAppId != INVALID_APP_ID) {
                    containingAppId = proxyAppId
                }

                return DepotDownloadInfo(depotId, containingAppId, manifestId, branch, installDir.installDir, depotKey)
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
        var allFileNamesAllDepots = hashSetOf<String>()

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
        downloadCounter: GlobalDownloadCounter
    ): DepotFilesData? {
        var depotCounter = DepotDownloadCounter()

        logger.debug("Processing depot ${depot.depotId}")

        var oldManifest: DepotManifest? = null
        var newManifest: DepotManifest? = null

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

                var manifestRequestCode = 0L
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
                        if (manifestRequestCode == 0L || now >= manifestRequestCodeExpiration) {
                            manifestRequestCode = steam3!!.getDepotManifestRequestCode(
                                depot.depotId,
                                depot.appId,
                                depot.manifestId,
                                depot.branch,
                            )

                            // This code will hopefully be valid for one period following the issuing period
                            manifestRequestCodeExpiration = now.plusMinutes(5)

                            // If we could not get the manifest code, this is a fatal error
                            if (manifestRequestCode == 0L) {
                                // TODO cancel
                            }
                        }

                        // TODO
                    }
                } while (newManifest == null)
            }
        }
    }
}
