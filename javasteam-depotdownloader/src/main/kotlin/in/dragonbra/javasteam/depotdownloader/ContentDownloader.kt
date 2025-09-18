package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.UGCDetailsCallback
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.log.LogManager
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.readAvailable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.apache.commons.lang3.SystemUtils

// TODO kotlin support first, java compat after.
// TODO remove suppression
@Suppress("unused")
class ContentDownloader(private val steam3: Steam3Session) : AutoCloseable {

    private val config: DownloadConfig
        get() = steam3.config

    override fun close() {
        HttpClientFactory.close()
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
        // TODO
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
        fun dumpManifestToTextFile(depot: DepotDownloadInfo, manifest: DepotManifest) {
            val txtManifest = "${depot.installDir}/manifest_${depot.depotid}_${depot.manifestId}.txt".toPath()

            FileSystem.SYSTEM.sink(txtManifest).buffer().use { sink ->
                sink.writeUtf8("Content Manifest for Depot ${depot.depotid}\n")
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
