package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
import `in`.dragonbra.javasteam.steam.cdn.Server
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
import `in`.dragonbra.javasteam.util.log.Logger
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.util.concurrent.ConcurrentHashMap
import kotlin.collections.set

class SteamSession(
    private val steamClient: SteamClient,
    debug: Boolean = false,
) : Closeable {

    private var logger: Logger? = null

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

    init {
        if (debug) {
            logger = LogManager.getLogger(SteamSession::class.java)
        }

        steamUser = steamClient.getHandler<SteamUser>()
        steamContent = steamClient.getHandler<SteamContent>()
        steamApps = steamClient.getHandler<SteamApps>()
        steamCloud = steamClient.getHandler<SteamCloud>()
    }

    override fun close() {
        steamUser = null
        steamContent = null
        steamApps = null
        steamCloud = null

        LogManager.removeLogger(SteamSession::class.java)
        logger = null
    }

    suspend fun requestPackageInfo(packageIds: ArrayList<Int>) {
        packageIds.removeAll(packageInfo.keys)

        if (packageIds.isEmpty()) return

        val packageRequests = arrayListOf<PICSRequest>()

        packageIds.forEach { pkg ->
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
        if (this.depotKeys.contains(depotId)) {
            return
        }

        val depotKey = steamApps!!.getDepotDecryptionKey(depotId, appId).await()

        logger?.debug("Got depot key for ${depotKey.depotID} result: ${depotKey.result}")

        if (depotKey.result != EResult.OK) {
            return
        }

        this.depotKeys[depotKey.depotID] = depotKey.depotKey
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
}
