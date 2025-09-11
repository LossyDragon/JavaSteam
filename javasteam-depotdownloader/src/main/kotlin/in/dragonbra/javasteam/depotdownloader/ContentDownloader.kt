package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EAppInfoSection
import `in`.dragonbra.javasteam.enums.EDepotFileFlag
import `in`.dragonbra.javasteam.types.DepotManifest
import `in`.dragonbra.javasteam.types.KeyValue
import `in`.dragonbra.javasteam.util.log.LogManager
import okio.FileSystem
import okio.Path.Companion.toPath
import okio.buffer
import org.apache.commons.lang3.SystemUtils

/**
 * TODO kotlin support first, java compat after.
 */
class ContentDownloader(private val steam3: Steam3Session) {

    private val config: DownloadConfig
        get() = steam3.config

    /**
     * @return a [Pair] if successful and install directory.
     */
    private fun createDirectories(depotId: UInt, depotVersion: UInt): Pair<Boolean, String> {
        return try {
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

    private suspend fun accountHasAccess(appId: UInt, depotId: UInt): Boolean {
        val steamUser = requireNotNull(steam3.steamUser)
        val steamID = requireNotNull(steamUser.steamID)
        if (steam3.licenses.isEmpty() && steamID.accountType != EAccountType.AnonUser) {
            return false
        }

        val licenseQuery = arrayListOf<UInt>()
        if (steamID.accountType == EAccountType.AnonUser) {
            licenseQuery.add(17906u)
        } else {
            licenseQuery.addAll(steam3.licenses.map { it.packageID.toUInt() }.distinct())
        }

        steam3.requestPackageInfo(licenseQuery)

        licenseQuery.forEach { license ->
            steam3.packageInfo[license]?.let { pkg ->
                if (pkg.keyValues["appids"].children.any { child -> child.asUnsignedInteger() == depotId }) {
                    return true
                }
                if (pkg.keyValues["depotids"].children.any { child -> child.asUnsignedInteger() == depotId }) {
                    return true
                }
            }
        }

        // Check if this app is free to download without a license
        val info = getSteam3AppSection(appId, EAppInfoSection.Common)

        @Suppress("RedundantIf") // its not a 'Redundant suppression' >:U
        if (info != null && info["FreeToDownload"].asBoolean()) {
            return true
        }

        return false
    }

    private fun getSteam3AppSection(appId: UInt, section: EAppInfoSection): KeyValue? {
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

    private fun getSteam3AppBuildNumber(appId: UInt, branch: String): UInt {
        if (appId == INVALID_APP_ID) {
            return 0u
        }

        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID
        val branches = depots["branches"]
        val node = branches[branch]

        if (node == KeyValue.INVALID) {
            return 0u
        }

        val buildId = node["buildid"]

        if (buildId == KeyValue.INVALID) {
            return 0u
        }

        return buildId.value!!.toUInt()
    }

    private fun getSteam3DepotProxyAppId(depotId: UInt, appId: UInt): UInt {
        val depots = getSteam3AppSection(appId, EAppInfoSection.Depots) ?: KeyValue.INVALID
        val depotChild = depots[depotId.toString()]

        if (depotChild == KeyValue.INVALID)
            return INVALID_APP_ID

        if (depotChild["depotfromapp"] == KeyValue.INVALID)
            return INVALID_APP_ID

        return depotChild["depotfromapp"].asUnsignedInteger()
    }

    private suspend fun getSteam3DepotManifest(depotId: UInt, appId: UInt, branch: String): ULong {
        TODO()
    }

    private fun getAppName(appId: UInt): String {
        val info = getSteam3AppSection(appId, EAppInfoSection.Common) ?: KeyValue.INVALID
        return info["name"].asString() ?: ""
    }

    companion object {
        private val logger = LogManager.getLogger(ContentDownloader::class.java)

        const val INVALID_APP_ID: UInt = UInt.MAX_VALUE
        const val INVALID_DEPOT_ID: UInt = UInt.MAX_VALUE
        const val INVALID_MANIFEST_ID: ULong = ULong.MAX_VALUE
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

                val uniqueChunks = mutableSetOf<ChunkId>().apply {
                    manifest.files.forEach { file ->
                        file.chunks.forEach { chunk ->
                            requireNotNull(chunk.chunkID) { "Found null chunk ID. Help triage" } // TODO verify
                            val chunkId = ChunkId(chunk.chunkID!!)
                            add(chunkId)
                        }
                    }
                }

                sink.writeUtf8("Total number of files  : ${manifest.files.size}\n")
                sink.writeUtf8("Total number of chunks : ${uniqueChunks.size}\n")
                sink.writeUtf8("Total bytes on disk    : ${manifest.totalUncompressedSize}\n")
                sink.writeUtf8("Total bytes compressed : ${manifest.totalCompressedSize}\n")
                sink.writeUtf8("\n")
                sink.writeUtf8("\n")
                sink.writeUtf8("          Size Chunks File SHA                                 Flags Name\n")

                manifest.files.forEach { file ->
                    val size = file.totalSize.toString().padStart(14)
                    val chunkSize = file.chunks.size.toString().padStart(6)
                    val sha1Hash = file.fileHash.toHexString().lowercase()
                    val flags = EDepotFileFlag.code(file.flags).toString().padStart(5)

                    sink.writeUtf8("$size $chunkSize $sha1Hash $flags ${file.fileName}\n")
                }
            }
        }
    }
}
