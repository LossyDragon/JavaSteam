package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfile.CPublishedFile_GetDetails_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfile.PublishedFileDetails
import `in`.dragonbra.javasteam.rpc.service.PublishedFile
import `in`.dragonbra.javasteam.steam.authentication.AuthSession
import `in`.dragonbra.javasteam.steam.authentication.AuthSessionDetails
import `in`.dragonbra.javasteam.steam.authentication.IChallengeUrlChanged
import `in`.dragonbra.javasteam.steam.authentication.UserConsoleAuthenticator
import `in`.dragonbra.javasteam.steam.cdn.Server
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.callback.UGCDetailsCallback
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.CDNAuthToken
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.handlers.steamuser.LogOnDetails
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.handlers.steamuser.callback.LoggedOnCallback
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackManager
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.ConnectedCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbacks.DisconnectedCallback
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.Versions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import pro.leaco.console.qrcode.ConsoleQrcode
import java.util.concurrent.CompletableFuture
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

// TODO add integration to initialize this from JavaSteam.
// TODO abstract logging to be used in JS or as standalone from main
// TODO abstract args from main into common downloading methods:
//  1. default or custom depots
//  2. current or previous manifest (older versions)
//  3. Common redists?

typealias WaitCondition = () -> Boolean

class Steam3Session(details: LogOnDetails) {

    var isLoggedOn: Boolean = false
        private set

    var licenses: List<License> = listOf()
        private set

    val appTokens: MutableMap<Int, Long> = mutableMapOf()
    val packageTokens: MutableMap<Int, Long> = mutableMapOf()
    val depotKeys: MutableMap<Int, ByteArray> = mutableMapOf()
    val cdnAuthTokens: ConcurrentHashMap<Pair<Int, String>, CompletableFuture<CDNAuthToken>> = ConcurrentHashMap()
    val appInfo: MutableMap<Int, PICSProductInfo?> = mutableMapOf()
    val packageInfo: MutableMap<Int, PICSProductInfo?> = mutableMapOf()
    val appBetaPasswords: MutableMap<String, ByteArray> = mutableMapOf()

    var steamClient: SteamClient
    var steamUser: SteamUser
    var steamContent: SteamContent
    var steamApps: SteamApps
    var steamCloud: SteamCloud
    var steamPublishedFile: PublishedFile

    var callbacks: CallbackManager

    var authenticatedUser: Boolean = false
    var bConnecting: Boolean = false
    var bAborted: Boolean = false
    var bExpectingDisconnectRemote: Boolean = false
    var bDidDisconnect: Boolean = false
    var bIsConnectionRecovery: Boolean = false
    var connectionBackoff: Int = 0
    var seq: Int = 0 // more hack fixes
    var authSession: AuthSession? = null

    private var logonDetails: LogOnDetails = details

    init {
        authenticatedUser = details.username.isNotEmpty() || ContentDownloader.config.useQrCode

        // val clientConfiguration = SteamConfiguration.create { _ -> }
        steamClient = SteamClient()

        steamUser = steamClient.getHandler<SteamUser>()!!
        steamApps = steamClient.getHandler<SteamApps>()!!
        steamCloud = steamClient.getHandler<SteamCloud>()!!
        val steamUnifiedMessages = steamClient.getHandler<SteamUnifiedMessages>()!!
        steamPublishedFile = steamUnifiedMessages.createService<PublishedFile>()
        steamContent = steamClient.getHandler<SteamContent>()!!

        callbacks = CallbackManager(steamClient)

        callbacks.subscribe<ConnectedCallback>(::onConnectedCallback)
        callbacks.subscribe<DisconnectedCallback>(::onDisconnectedCallback)
        callbacks.subscribe<LoggedOnCallback>(::onLogOnCallback)
        callbacks.subscribe<LicenseListCallback>(::onLicenseListCallback)

        logI("Connecting to Steam3...")
        connect()
    }

    private val steamLock = ReentrantLock()

    private suspend fun waitUntilCallback(
        submitter: () -> Unit,
        waiter: WaitCondition,
    ): Boolean {
        while (!bAborted && !waiter()) {
            withContext(Dispatchers.IO) {
                steamLock.withLock {
                    submitter()
                }
                val currentSeq = seq

                do {
                    steamLock.withLock {
                        callbacks.runWaitCallbacks(1.seconds.toLong(DurationUnit.SECONDS))
                    }
                } while (!bAborted && seq == currentSeq && !waiter())
            }
        }
        return bAborted
    }

    suspend fun waitForCredentials(): Boolean {
        if (isLoggedOn || bAborted) {
            return isLoggedOn
        }

        waitUntilCallback(submitter = { }, waiter = { isLoggedOn })

        return isLoggedOn
    }

    suspend fun tickCallbacks() =
        withContext(Dispatchers.IO) {
            try {
                while (isActive) {
                    callbacks.runWaitCallbackAsync()
                }
            } catch (e: CancellationException) {
                e.printStackTrace()
                //
            }
        }

    suspend fun requestAppInfo(
        appId: Int,
        bForce: Boolean = false,
    ) {
        if ((appInfo.containsKey(appId) && !bForce) || bAborted) {
            return
        }

        val appTokens = steamApps.picsGetAccessTokens(appIds = listOf(appId), packageIds = listOf()).await()

        if (appTokens.appTokensDenied.contains(appId)) {
            logI("Insufficient privileges to get access token for app $appId")
        }

        appTokens.appTokens.forEach { (k, v) ->
            this.appTokens[k] = v
        }

        val request = PICSRequest(appId)

        val token = this.appTokens[appId]
        if (token != null) {
            request.accessToken = token
        }

        val appInfoMultiple = steamApps.picsGetProductInfo(apps = listOf(request), packages = listOf()).await()

        appInfoMultiple.results.forEach { appInfo ->
            appInfo.apps.forEach { (_, v) ->
                logI("Got AppInfo for ${v.id}")

                this.appInfo[v.id] = v
            }

            appInfo.unknownApps.forEach { app ->
                this.appInfo[app] = null
            }
        }
    }

    suspend fun requestPackageInfo(packageIds: Iterable<Int>) {
        val packages = packageIds.toMutableList()
        packages.removeAll { packageInfo.containsKey(it) }

        if (packages.isEmpty() || bAborted) {
            return
        }

        val packageRequests = mutableListOf<PICSRequest>()

        packageIds.forEach { pkg ->
            val request = PICSRequest(pkg)
            packageTokens[pkg]?.let { token ->
                request.accessToken = token
            }

            packageRequests.add(request)
        }

        val packageInfoMultiple = steamApps.picsGetProductInfo(listOf(), packageRequests).await()

        packageInfoMultiple.results.forEach { packageInfo ->
            packageInfo.packages.forEach { (_, pkg) ->
                this.packageInfo[pkg.id] = pkg
            }
            packageInfo.unknownPackages.forEach { pkg ->
                this.packageInfo[pkg] = null
            }
        }
    }

    suspend fun requestFreeAppLicense(appId: Int): Boolean {
        try {
            val resultInfo = steamApps.requestFreeLicense(appId).await()

            return resultInfo.grantedApps.contains(appId)
        } catch (ex: Exception) {
            logI("Failed to request FreeOnDemand license for app $appId: ${ex.message}")
            ex.printStackTrace()
            return false
        }
    }

    suspend fun requestDepotKey(
        depotId: Int,
        appid: Int = 0,
    ) {
        if (depotKeys.containsKey(depotId) || bAborted) {
            return
        }

        val depotKey = steamApps.getDepotDecryptionKey(depotId, appid).await()

        logI("Got depot key for ${depotKey.depotID} result: ${depotKey.result}")

        if (depotKey.result != EResult.OK) {
            return
        }

        depotKeys[depotKey.depotID] = depotKey.depotKey
    }

    suspend fun getDepotManifestRequestCodeAsync(
        depotId: Int,
        appId: Int,
        manifestId: Long,
        branch: String,
    ): Long = withContext(Dispatchers.IO) {
        if (bAborted) {
            return@withContext 0L
        }

        val requestCode = steamContent.getManifestRequestCode(
            depotId = depotId,
            appId = appId,
            manifestId = manifestId,
            branch = branch,
            branchPasswordHash = null,
            parentScope = this,
        ).await().toLong()

        if (requestCode == 0L) {
            logI("No manifest request code was returned for depot $depotId from app $appId, manifest $manifestId")
        } else {
            logI("Got manifest request code for depot $depotId from app $appId, manifest $manifestId, result: $requestCode")
        }

        return@withContext requestCode
    }

    suspend fun requestCDNAuthToken(appid: Int, depotid: Int, server: Server) = withContext(Dispatchers.IO) {
        val cdnKey = depotid to server.host
        val completion = CompletableFuture<CDNAuthToken>()

        if (bAborted || cdnAuthTokens.putIfAbsent(cdnKey, completion) != null) {
            return@withContext
        }

        logI("Requesting CDN auth token for ${server.host}")

        val cdnAuth = steamContent.getCDNAuthToken(appid, depotid, server.host, this).await()
        logI("Got CDN auth token for ${server.host} result: ${cdnAuth.result} (expires ${cdnAuth.expiration})")

        if (cdnAuth.result != EResult.OK) {
            return@withContext
        }

        completion.complete(cdnAuth)
    }

    suspend fun checkAppBetaPassword(appid: Int, password: String) {
        val appPassword = steamApps.checkAppBetaPassword(appid, password).await()

        logI("Retrieved ${appPassword.betaPasswords.size} beta keys with result: ${appPassword.result}")

        appPassword.betaPasswords.forEach { (k, v) ->
            this.appBetaPasswords[k] = v
        }
    }

    suspend fun getPublishedFileDetails(appId: Int, pubFile: PublishedFileID): PublishedFileDetails? {
        val pubFileRequest = CPublishedFile_GetDetails_Request
            .newBuilder()
            .apply {
                this.appid = appId
                this.addPublishedfileids(PublishedFileID.toLong(pubFile))
            }.build()

        val details = steamPublishedFile.getDetails(pubFileRequest).await()

        if (details.result == EResult.OK) {
            return details.body.publishedfiledetailsList.firstOrNull()
        }

        throw Exception("EResult ${details.result.code()} (${details.result}) while retrieving file details for pubfile {pubFile}.")
    }

    suspend fun getUGCDetails(ugcHandle: UGCHandle): UGCDetailsCallback? {
        val callback = steamCloud.requestUGCDetails(ugcHandle).await()

        if (callback.result == EResult.OK) {
            return callback
        } else if (callback.result == EResult.FileNotFound) {
            return null
        }

        throw Exception("EResult ${callback.result.code()} (${callback.result}) while retrieving UGC details for $ugcHandle.")
    }

    private fun resetConnectionFlags() {
        bExpectingDisconnectRemote = false
        bDidDisconnect = false
        bIsConnectionRecovery = false
    }

    fun connect() {
        bAborted = false
        bConnecting = true
        connectionBackoff = 0
        authSession = null

        resetConnectionFlags()
        steamClient.connect()
    }

    // TODO combine this with disconnect.
    private fun abort(sendLogOff: Boolean = true) {
        disconnect(sendLogOff = sendLogOff)
    }

    fun disconnect(sendLogOff: Boolean = true) {
        if (sendLogOff) {
            steamUser.logOff()
        }

        bAborted = true
        bConnecting = false
        bIsConnectionRecovery = false

        steamClient.disconnect()

        // Ansi.Progress(Ansi.ProgressState.Hidden)

        // flush callbacks until our disconnected event
        while (!bDidDisconnect) {
            callbacks.runWaitAllCallbacks(timeout = 100)
        }
    }

    private fun reconnect() {
        bIsConnectionRecovery = true
        steamClient.disconnect()
    }

    @Suppress("unused")
    private fun onConnectedCallback(connected: ConnectedCallback) {
        CoroutineScope(Dispatchers.IO).launch {
            bConnecting = false

            // Update our tracking so that we don't time out, even if we need to reconnect multiple times,
            // e.g. if the authentication phase takes a while and therefore multiple connections.
            connectionBackoff = 0

            if (!authenticatedUser) {
                print("Logging anonymously into Steam3...")
                steamUser.logOnAnonymous()
            } else {
                if (logonDetails.username.isNotEmpty()) {
                    logI("Logging '${logonDetails.username}' into Steam3...")
                }

                if (authSession == null) {
                    if (logonDetails.username.isNotEmpty() && logonDetails.password != null && logonDetails.accessToken == null) {
                        try {
                            val guardData = AccountSettingsStore.instance!!.guardData[logonDetails.username]
                            val friendlyName = "JavaSteam - Depot Downloader ${Versions.getVersion()}"
                            authSession = steamClient.authentication.beginAuthSessionViaCredentials(
                                authSessionDetails = AuthSessionDetails().apply {
                                    this.username = logonDetails.username
                                    this.password = logonDetails.password
                                    this.persistentSession = ContentDownloader.config.rememberPassword
                                    this.guardData = guardData
                                    this.authenticator = UserConsoleAuthenticator()
                                    this.deviceFriendlyName = friendlyName
                                },
                                parentScope = this,
                            ).await()
                        } catch (ex: CancellationException) {
                            ex.printStackTrace()
                            return@launch
                        } catch (ex: Exception) {
                            logE("(Credentials) Failed to authenticate with Steam: ${ex.message}")
                            ex.printStackTrace()
                            abort(sendLogOff = false)
                            return@launch
                        }
                    } else if (logonDetails.accessToken == null && ContentDownloader.config.useQrCode) {
                        logI("Logging in with QR code...")

                        try {
                            val session = steamClient.authentication.beginAuthSessionViaQR(
                                authSessionDetails = AuthSessionDetails().apply {
                                    persistentSession = ContentDownloader.config.rememberPassword
                                    authenticator = UserConsoleAuthenticator()
                                },
                                parentScope = this,
                            ).await()

                            authSession = session

                            // Steam will periodically refresh the challenge url, so we need a new QR code.
                            session.challengeUrlChanged = IChallengeUrlChanged { qr ->
                                logI()
                                logI("The QR code has changed:")
                                qr?.challengeUrl?.let(::displayQrCode)
                            }

                            // Draw initial QR code immediately
                            displayQrCode(session.challengeUrl)
                        } catch (ex: CancellationException) {
                            ex.printStackTrace()
                            return@launch
                        } catch (ex: Exception) {
                            logE("(QR) Failed to authenticate with Steam: ${ex.message}")
                            ex.printStackTrace()
                            abort(false)
                            return@launch
                        }
                    }
                }

                if (authSession != null) {
                    try {
                        val result = authSession!!.pollingWaitForResult().await()

                        logonDetails.username = result.accountName
                        logonDetails.password = null
                        logonDetails.accessToken = result.refreshToken

                        with(AccountSettingsStore.instance!!) {
                            if (result.newGuardData != null) {
                                guardData[result.accountName] = result.newGuardData.orEmpty()
                            } else {
                                guardData.remove(result.accountName)
                            }
                        }

                        AccountSettingsStore.instance!!.loginTokens[result.accountName] = result.refreshToken
                        AccountSettingsStore.save()
                    } catch (ex: CancellationException) {
                        ex.printStackTrace()
                        return@launch
                    } catch (ex: Exception) {
                        logE("Failed to authenticate with Steam: ${ex.message}")
                        ex.printStackTrace()
                        abort(sendLogOff = false)
                        return@launch
                    }

                    authSession = null
                }

                steamUser.logOn(details = logonDetails)
            }
        }
    }

    private fun onDisconnectedCallback(disconnected: DisconnectedCallback) {
        bDidDisconnect = true

        logI(
            "Disconnected: " +
                "bIsConnectionRecovery = $bIsConnectionRecovery, " +
                "UserInitiated = ${disconnected.isUserInitiated}, " +
                "bExpectingDisconnectRemote = $bExpectingDisconnectRemote",
        )

        // When recovering the connection, we want to reconnect even if the remote disconnects us
        if (!bIsConnectionRecovery && (disconnected.isUserInitiated || bExpectingDisconnectRemote)) {
            logI("Disconnected from Steam")

            // Any operations outstanding need to be aborted
            bAborted = true
        } else if (connectionBackoff >= 10) {
            logI("Could not connect to Steam after 10 tries")
            abort(sendLogOff = false)
        } else if (!bAborted) {
            connectionBackoff += 1

            if (bConnecting) {
                logI("Connection to Steam failed. Trying again ($connectionBackoff)...")
            } else {
                logI("Lost connection to Steam. Reconnecting")
            }

            Thread.sleep(1000L * connectionBackoff.toLong())

            // Any connection related flags need to be reset here to match the state after Connect
            resetConnectionFlags()
            steamClient.connect()
        }
    }

    private fun onLogOnCallback(loggedOn: LoggedOnCallback) {
        val isSteamGuard = loggedOn.result == EResult.AccountLogonDenied
        val is2FA = loggedOn.result == EResult.AccountLoginDeniedNeedTwoFactor
        val isAccessToken = ContentDownloader.config.rememberPassword &&
            logonDetails.accessToken != null &&
            loggedOn.result in listOf(
                EResult.InvalidPassword,
                EResult.InvalidSignature,
                EResult.AccessDenied,
                EResult.Expired,
                EResult.Revoked,
            )

        if (isSteamGuard || is2FA || isAccessToken) {
            bExpectingDisconnectRemote = true
            abort(sendLogOff = false)

            if (!isAccessToken) {
                logI("This account is protected by Steam Guard.")
            }

            if (is2FA) {
                do {
                    print("Please enter your 2 factor auth code from your authenticator app: ")
                    logonDetails.twoFactorCode = readln()
                } while (logonDetails.twoFactorCode.isNullOrEmpty())
            } else if (isAccessToken) {
                AccountSettingsStore.instance!!.loginTokens.remove(logonDetails.username)
                AccountSettingsStore.save()

                // TODO: Handle gracefully by falling back to password prompt?
                logI("Access token was rejected (${loggedOn.result}).")
                abort(sendLogOff = false)
                return
            } else {
                do {
                    print("Please enter the authentication code sent to your email address: ")
                    logonDetails.authCode = readln()
                } while (logonDetails.authCode.isNullOrEmpty())
            }

            print("Retrying Steam3 connection...")
            connect()

            return
        }

        if (loggedOn.result == EResult.TryAnotherCM) {
            print("Retrying Steam3 connection (TryAnotherCM)...")

            reconnect()

            return
        }

        if (loggedOn.result == EResult.ServiceUnavailable) {
            logI("Unable to login to Steam3: ${loggedOn.result}")
            abort(sendLogOff = false)

            return
        }

        if (loggedOn.result != EResult.OK) {
            logI("Unable to login to Steam3: ${loggedOn.result}")
            abort()

            return
        }

        seq++
        isLoggedOn = true

        if (ContentDownloader.config.cellID == 0) {
            logI("Using Steam3 suggested CellID: " + loggedOn.cellID)
            ContentDownloader.config.cellID = loggedOn.cellID
        }
    }

    private fun onLicenseListCallback(licenseList: LicenseListCallback) {
        if (licenseList.result != EResult.OK) {
            logI("Unable to get license list: ${licenseList.result}")
            abort()

            return
        }

        logI("Got ${licenseList.licenseList.size} licenses for account!")
        licenses = licenseList.licenseList

        licenseList.licenseList.forEach { license ->
            if (license.accessToken > 0) {
                packageTokens[license.packageID] = license.accessToken
            }
        }
    }

    private fun displayQrCode(challengeUrl: String) {
        logI("Use the Steam Mobile App to sign in with this QR code:")
        ConsoleQrcode.print(content = challengeUrl)
    }
}
