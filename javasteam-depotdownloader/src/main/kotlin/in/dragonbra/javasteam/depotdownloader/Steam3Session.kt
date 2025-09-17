package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
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
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 *
 */
class Steam3Session(
    internal val config: DownloadConfig,
    internal val steamClient: SteamClient,
    internal val licenses: List<License>,
) : AutoCloseable {

    internal var steamUser: SteamUser? = null
    internal var steamContent: SteamContent? = null
    internal var steamApps: SteamApps? = null
    internal var steamCloud: SteamCloud? = null
    internal var steamPublishedFile: PublishedFile? = null

    private val appTokens: MutableMap<Int, Long> = mutableMapOf()
    private val packageTokens: MutableMap<Int, Long> = mutableMapOf()
    internal val depotKeys: MutableMap<Int, ByteArray> = mutableMapOf()
    private val cdnAuthTokens: ConcurrentHashMap<Pair<Int, String>, CompletableDeferred<CDNAuthToken>> =
        ConcurrentHashMap()
    internal val appInfo: MutableMap<Int, PICSProductInfo?> = mutableMapOf()
    internal val packageInfo: MutableMap<Int, PICSProductInfo?> = mutableMapOf()
    internal val appBetaPasswords: MutableMap<String, ByteArray> = mutableMapOf()

    init {
        steamUser = steamClient.getHandler<SteamUser>()!!
        steamContent = steamClient.getHandler<SteamContent>()!!
        steamApps = steamClient.getHandler<SteamApps>()!!
        steamCloud = steamClient.getHandler<SteamCloud>()!!
    }

    override fun close() {
        steamUser = null
        steamContent = null
        steamApps = null
        steamCloud = null
    }

    suspend fun requestPackageInfo(packageIds: ArrayList<Int>) {
        packageIds.removeAll(packageInfo.keys)

        if (packageIds.isEmpty()) return

        val packageRequests = arrayListOf<PICSRequest>()

        packageIds.forEach { pkg ->
            val request = PICSRequest(id = pkg.toInt())

            packageTokens[pkg]?.let { token ->
                request.accessToken = token.toLong()
            }

            packageRequests.add(request)
        }

        val packageInfoMultiple = steamApps!!.picsGetProductInfo(emptyList(), packageRequests).await()

        packageInfoMultiple.results.forEach { pkgInfo ->
            pkgInfo.packages.forEach { pkgValue ->
                val pkg = pkgValue.value
                packageInfo[pkg.id.toInt()] = pkg
            }
            pkgInfo.unknownPackages.forEach { pkgValue ->
                packageInfo[pkgValue.toInt()] = null
            }
        }
    }

    suspend fun getPublishedFileDetails(appId: Int, pubFile: PublishedFileID): PublishedFileDetails? {
        val pubFileRequest = CPublishedFile_GetDetails_Request.newBuilder().apply {
            this.appid = appId.toInt()
            this.addPublishedfileids(pubFile.toLong())
        }.build()

        val details = steamPublishedFile!!.getDetails(pubFileRequest).await()

        if (details.result == EResult.OK) {
            return details.body.publishedfiledetailsBuilderList.firstOrNull()?.build()
        }

        throw ContentDownloaderException("EResult ${details.result.code()} (${details.result}) while retrieving file details for pubfile ${pubFile}.")
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
            logger.error("Insufficient privileges to get access token for app $appId")
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
                logger.debug("Got AppInfo for ${app.id}")
                this.appInfo[app.id] = app
            }
            appInfo.unknownApps.forEach { app ->
                this.appInfo[app] = null
            }
        }
    }

    suspend fun requestPackageInfo(packageIds: List<Int>) {
        TODO()
    }

    suspend fun requestFreeAppLicense(appId: Int): Boolean {
        try {
            val resultInfo = steamApps!!.requestFreeLicense(appId).await()
            return resultInfo.grantedApps.contains(appId)
        } catch (e: Exception) {
            logger.error("Failed to request FreeOnDemand license for app $appId: ${e.message}")
            return false
        }
    }

    suspend fun requestDepotKey(depotId: Int, appId: Int = 0) {
        if (this.depotKeys.contains(depotId)) {
            return
        }

        val depotKey = steamApps!!.getDepotDecryptionKey(depotId, appId).await()

        logger.debug("Got depot key for ${depotKey.depotID} result: ${depotKey.result}")

        if (depotKey.result != EResult.OK) {
            return
        }

        this.depotKeys[depotKey.depotID] = depotKey.depotKey
    }

    suspend fun checkAppBetaPassword(appId: Int, password: String) {
        val appPassword = steamApps!!.checkAppBetaPassword(appId, password).await()

        logger.debug("Retrieved ${appPassword.betaPasswords.size} beta keys with result: ${appPassword.result}")

        appPassword.betaPasswords.forEach { entry ->
            this.appBetaPasswords[entry.key] = entry.value
        }
    }

    suspend fun getPrivateBetaDepotSection(appId: Int, branch: String): KeyValue {
        val branchPassword = appBetaPasswords[branch] ?: return KeyValue()  // Should be filled by CheckAppBetaPassword

        val accessToken = appTokens[appId] ?: 0L // Should be filled by RequestAppInfo

        val privateBeta = steamApps!!.picsGetPrivateBeta(appId, accessToken, branch, branchPassword).await()

        return privateBeta.depotSection
    }

    companion object {
        private val logger = LogManager.getLogger(Steam3Session::class.java)
    }
}
