package `in`.dragonbra.javasteam.contentdownloader

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.CPublishedFile_GetDetails_Request
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesPublishedfileSteamclient.PublishedFileDetails
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
import `in`.dragonbra.javasteam.steam.steamclient.configuration.SteamConfiguration
import `in`.dragonbra.javasteam.types.PublishedFileID
import `in`.dragonbra.javasteam.types.PublishedFileID.Companion.toLong
import `in`.dragonbra.javasteam.types.UGCHandle
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.future.await
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import pro.leaco.console.qrcode.ConsoleQrcode
import java.util.concurrent.*
import java.util.concurrent.locks.*
import javax.swing.undo.CannotRedoException
import kotlin.concurrent.withLock
import kotlin.coroutines.cancellation.CancellationException

class Steam3Session {
    var isLoggedOn: Boolean = false
        private set

    var licenses: List<License> = emptyList()
        private set

    val appTokens: MutableMap<Int, Long> = mutableMapOf()
    val packageTokens: MutableMap<Int, Long> = mutableMapOf()
    val depotKeys: MutableMap<Int, ByteArray> = mutableMapOf()
    val cdnAuthTokens: ConcurrentHashMap<Pair<Int, String>, CompletableDeferred<CDNAuthToken>?> = ConcurrentHashMap()
    val appInfo: MutableMap<Int, PICSProductInfo?> = mutableMapOf()
    val packageInfo: MutableMap<Int, PICSProductInfo?> = mutableMapOf()
    val appBetaPasswords: MutableMap<String, ByteArray> = mutableMapOf()

    var steamClient: SteamClient
    var steamUser: SteamUser
    var steamContent: SteamContent

    private val steamApps: SteamApps
    private val steamCloud: SteamCloud
    private val steamPublishedFile: PublishedFile
    private val callbacks: CallbackManager
    private val authenticatedUser: Boolean

    private var bConnecting: Boolean = false
    private var bAborted: Boolean = false
    private var bExpectingDisconnectRemote: Boolean = false
    private var bDidDisconnect: Boolean = false
    private var bIsConnectionRecovery: Boolean = false
    private var connectionBackoff: Int = 0
    private var seq: Int = 0 // more hack fixes
    private var authSession: AuthSession? = null

    private val steamLock = ReentrantLock()

    // input
    var logonDetails: LogOnDetails
        private set

    @Suppress("ConvertSecondaryConstructorToPrimary")
    constructor(details: LogOnDetails) {
        logonDetails = details
        authenticatedUser = details.username.isNotEmpty() || ContentDownloader.config.useQrCode

        val clientConfiguration = SteamConfiguration.create { _ ->
            // config.withHttpClient()
        }
        steamClient = SteamClient(clientConfiguration)

        steamUser = steamClient.getHandler<SteamUser>()!!
        steamApps = steamClient.getHandler<SteamApps>()!!
        steamCloud = steamClient.getHandler<SteamCloud>()!!
        val steamUnifiedMessages = steamClient.getHandler<SteamUnifiedMessages>()
        steamPublishedFile = steamUnifiedMessages!!.createService<PublishedFile>()
        steamContent = steamClient.getHandler<SteamContent>()!!

        callbacks = CallbackManager(steamClient)

        callbacks.subscribe<ConnectedCallback>(::connectedCallback)
        callbacks.subscribe<DisconnectedCallback>(::disconnectedCallback)
        callbacks.subscribe<LoggedOnCallback>(::logOnCallback)
        callbacks.subscribe<LicenseListCallback>(::licenseListCallback)

        println("Connecting to Steam3...")
        connect()
    }

    fun waitUntilCallback(submitter: () -> Unit, waiter: () -> Boolean): Boolean {
        while (!bAborted && !waiter()) {
            steamLock.withLock {
                submitter()
            }
            val currentSeq = this.seq
            do {
                steamLock.withLock {
                    callbacks.runWaitCallbacks(1000L)
                }
            } while (!bAborted && this.seq == currentSeq && !waiter())
        }
        return bAborted
    }

    fun waitForCredentials(): Boolean {
        if (isLoggedOn || bAborted) {
            return isLoggedOn
        }

        waitUntilCallback({ }, { isLoggedOn })
        return isLoggedOn
    }

    // TODO
    suspend fun tickCallbacks(scope: CoroutineScope) {
        try {
            while (scope.isActive) {
                // println("Waiting for callbacks...")
                callbacks.runWaitCallbackAsync()
            }
        } catch (e: CancellationException) {
            // Operation was canceled
        }
    }

    suspend fun requestAppInfo(appId: Int, bForce: Boolean = false) {
        if ((appInfo.containsKey(appId) && !bForce) || bAborted) {
            return
        }

        val appTokens = steamApps.picsGetAccessTokens(listOf(appId), emptyList()).await()
        if (appTokens.appTokensDenied.contains(appId)) {
            println("Insufficient privileges to get access token for app $appId")
        }

        for ((key, value) in appTokens.appTokens) {
            this.appTokens[key] = value
        }

        val request = PICSRequest(appId)
        this.appTokens[appId]?.let { token ->
            request.accessToken = token
        }

        val appInfoMultiple = steamApps.picsGetProductInfo(listOf(request), emptyList()).await()
        for (appInfoResult in appInfoMultiple.results) {
            for ((_, app) in appInfoResult.apps) {
                println("Got AppInfo for ${app.id}")
                appInfo[app.id] = app
            }

            for (appId in appInfoResult.unknownApps) {
                appInfo[appId] = null
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
        for (packageId in packages) {
            val request = PICSRequest(packageId)
            packageTokens[packageId]?.let { token ->
                request.accessToken = token
            }
            packageRequests.add(request)
        }

        val packageInfoMultiple = steamApps.picsGetProductInfo(emptyList(), packageRequests).await()
        for (packageInfoResult in packageInfoMultiple.results) {
            for ((_, packageValue) in packageInfoResult.packages) {
                packageInfo[packageValue.id] = packageValue
            }

            for (packageId in packageInfoResult.unknownPackages) {
                packageInfo[packageId] = null
            }
        }
    }

    suspend fun requestFreeAppLicense(appId: Int): Boolean = try {
        val resultInfo = steamApps.requestFreeLicense(appId).await()
        resultInfo.grantedApps.contains(appId)
    } catch (ex: Exception) {
        println("Failed to request FreeOnDemand license for app $appId: ${ex.message}")
        false
    }

    suspend fun requestDepotKey(depotId: Int, appId: Int = 0) {
        if (depotKeys.containsKey(depotId) || bAborted) {
            return
        }

        val depotKey = steamApps.getDepotDecryptionKey(depotId, appId).await()
        println("Got depot key for ${depotKey.depotID} result: ${depotKey.result}")

        if (depotKey.result != EResult.OK) {
            return
        }

        depotKeys[depotKey.depotID] = depotKey.depotKey
    }

    suspend fun getDepotManifestRequestCode(
        depotId: Int,
        appId: Int,
        manifestId: Long,
        branch: String,
    ): ULong {
        if (bAborted) {
            return 0UL
        }

        val requestCode = steamContent.getManifestRequestCode(
            depotId = depotId,
            appId = appId,
            manifestId = manifestId,
            branch = branch,
            parentScope = steamClient.defaultScope
        ).await()

        if (requestCode == 0UL) {
            println("No manifest request code was returned for depot $depotId from app $appId, manifest $manifestId")
        } else {
            println("Got manifest request code for depot $depotId from app $appId, manifest $manifestId, result: $requestCode")
        }

        return requestCode
    }

    suspend fun requestCDNAuthToken(appId: Int, depotId: Int, server: Server) {
        val cdnKey = Pair(depotId, server.host!!)
        val completion = CompletableDeferred<CDNAuthToken>()

        val token = cdnAuthTokens.putIfAbsent(cdnKey, completion)
        if (bAborted || token != null) {
            return
        }

        println("Requesting CDN auth token for ${server.host}")
        val cdnAuth = steamContent.getCDNAuthToken(appId, depotId, server.host!!, steamClient.defaultScope).await()
        println("Got CDN auth token for ${server.host} result: ${cdnAuth.result} (expires ${cdnAuth.expiration})")

        if (cdnAuth.result != EResult.OK) {
            return
        }

        completion.complete(cdnAuth)
    }

    suspend fun checkAppBetaPassword(appId: Int, password: String) {
        val appPassword = steamApps.checkAppBetaPassword(appId, password).await()
        println("Retrieved ${appPassword.betaPasswords.size} beta keys with result: ${appPassword.result}")

        for ((key, value) in appPassword.betaPasswords) {
            appBetaPasswords[key] = value
        }
    }

    suspend fun getPublishedFileDetails(appId: Int, pubFile: PublishedFileID): PublishedFileDetails {
        val pubFileRequest = CPublishedFile_GetDetails_Request.newBuilder().apply {
            appid = appId
            addPublishedfileids(pubFile.toLong())
        }.build()

        val details = steamPublishedFile.getDetails(pubFileRequest).await()

        if (details.result == EResult.OK) {
            return details.body.publishedfiledetailsList.firstOrNull()
                ?: throw Exception("No file details returned for pubfile $pubFile.")
        }

        throw Exception("EResult ${details.result.code()} (${details.result}) while retrieving file details for pubfile $pubFile.")
    }

    suspend fun getUGCDetails(ugcHandle: UGCHandle): UGCDetailsCallback? {
        val callback = steamCloud.requestUGCDetails(ugcHandle).await()

        return when (callback.result) {
            EResult.OK -> callback
            EResult.FileNotFound -> null
            else -> throw Exception("EResult ${callback.result.code()} (${callback.result}) while retrieving UGC details for $ugcHandle.")
        }
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

    private fun abort(sendLogOff: Boolean = true) {
        disconnect(sendLogOff)
    }

    fun disconnect(sendLogOff: Boolean = true) {
        if (sendLogOff) {
            steamUser.logOff()
        }

        bAborted = true
        bConnecting = false
        bIsConnectionRecovery = false
        steamClient.defaultScope.cancel()
        steamClient.disconnect()

        // flush callbacks until our disconnected event
        while (!bDidDisconnect) {
            callbacks.runWaitAllCallbacks(100)
        }
    }

    private fun reconnect() {
        bIsConnectionRecovery = true
        steamClient.disconnect()
    }

    private fun connectedCallback(connected: ConnectedCallback) {
        steamClient.defaultScope.launch {
            println(" Done!")
            bConnecting = false

            // Update our tracking so that we don't time out, even if we need to reconnect multiple times,
            // e.g. if the authentication phase takes a while and therefore multiple connections.
            connectionBackoff = 0

            if (!authenticatedUser) {
                println("Logging anonymously into Steam3...")
                steamUser.logOnAnonymous()
            } else {
                if (logonDetails.username.isNotEmpty()) {
                    println("Logging '${logonDetails.username}' into Steam3...")
                }

                if (authSession == null) {
                    if (logonDetails.username.isNotEmpty() && logonDetails.password != null && logonDetails.accessToken == null) {
                        try {
                            val guardData = AccountSettingsStore.instance!!.guardData[logonDetails.username]
                            authSession = steamClient.authentication.beginAuthSessionViaCredentials(
                                authSessionDetails = AuthSessionDetails().apply {
                                    this.username = logonDetails.username
                                    this.password = logonDetails.password
                                    this.persistentSession = ContentDownloader.config.rememberPassword
                                    this.guardData = guardData
                                    this.authenticator = UserConsoleAuthenticator()
                                }
                            ).await()
                        } catch (e: CancellationException) {
                            return@launch
                        } catch (ex: Exception) {
                            System.err.println("Failed to authenticate with Steam: ${ex.message}")
                            abort(false)
                            return@launch
                        }
                    } else if (logonDetails.accessToken == null && ContentDownloader.config.useQrCode) {
                        println("Logging in with QR code...")

                        try {
                            val session = steamClient.authentication.beginAuthSessionViaQR(
                                authSessionDetails = AuthSessionDetails().apply {
                                    persistentSession = ContentDownloader.config.rememberPassword
                                    authenticator = UserConsoleAuthenticator()
                                }
                            ).await()

                            authSession = session

                            // Steam will periodically refresh the challenge url, so we need a new QR code.
                            session.challengeUrlChanged = IChallengeUrlChanged {
                                println()
                                println("The QR code has changed:")

                                displayQrCode(session.challengeUrl)
                            }

                            // Draw initial QR code immediately
                            displayQrCode(session.challengeUrl)
                        } catch (e: CannotRedoException) {
                            return@launch
                        } catch (ex: Exception) {
                            System.err.println("Failed to authenticate with Steam: ${ex.message}")
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

                        if (result.newGuardData != null) {
                            AccountSettingsStore.instance!!.guardData[result.accountName] = result.newGuardData!!
                        } else {
                            AccountSettingsStore.instance!!.guardData.remove(result.accountName)
                        }
                        AccountSettingsStore.instance!!.loginTokens[result.accountName] = result.refreshToken
                        AccountSettingsStore.save()
                    } catch (e: CancellationException) {
                        return@launch
                    } catch (ex: Exception) {
                        System.err.println("Failed to authenticate with Steam: ${ex.message}")
                        abort(false)
                        return@launch
                    }

                    authSession = null
                }

                steamUser.logOn(logonDetails)
            }
        }
    }

    private fun disconnectedCallback(disconnected: DisconnectedCallback) {
        steamClient.defaultScope.launch {
            bDidDisconnect = true

            println(
                "Disconnected: bIsConnectionRecovery = $bIsConnectionRecovery, " +
                    "UserInitiated = ${disconnected.isUserInitiated}, " +
                    "bExpectingDisconnectRemote = $bExpectingDisconnectRemote"
            )

            // When recovering the connection, we want to reconnect even if the remote disconnects us
            if (!bIsConnectionRecovery && (disconnected.isUserInitiated || bExpectingDisconnectRemote)) {
                println("Disconnected from Steam")

                // Any operations outstanding need to be aborted
                bAborted = true
            } else if (connectionBackoff >= 10) {
                println("Could not connect to Steam after 10 tries")
                abort(false)
            } else if (!bAborted) {
                connectionBackoff += 1

                if (bConnecting) {
                    println("Connection to Steam failed. Trying again ($connectionBackoff)...")
                } else {
                    println("Lost connection to Steam. Reconnecting")
                }

                Thread.sleep(1000L * connectionBackoff)

                // Any connection related flags need to be reset here to match the state after Connect
                resetConnectionFlags()
                steamClient.connect()
            }
        }
    }

    private fun logOnCallback(loggedOn: LoggedOnCallback) {
        val isSteamGuard = loggedOn.result == EResult.AccountLogonDenied
        val is2FA = loggedOn.result == EResult.AccountLoginDeniedNeedTwoFactor
        val isAccessToken = ContentDownloader.config.rememberPassword &&
            logonDetails.accessToken != null &&
            loggedOn.result in listOf(
                EResult.InvalidPassword,
                EResult.InvalidSignature,
                EResult.AccessDenied,
                EResult.Expired,
                EResult.Revoked
            )

        if (isSteamGuard || is2FA || isAccessToken) {
            bExpectingDisconnectRemote = true
            abort(false)

            if (!isAccessToken) {
                println("This account is protected by Steam Guard.")
            }

            if (is2FA) {
                do {
                    println("Please enter your 2 factor auth code from your authenticator app: ")
                    logonDetails.twoFactorCode = readlnOrNull().orEmpty()
                } while ("" == logonDetails.twoFactorCode)
            } else if (isAccessToken) {
                AccountSettingsStore.instance!!.loginTokens.remove(logonDetails.username)
                AccountSettingsStore.save()

                // TODO: Handle gracefully by falling back to password prompt?
                println("Access token was rejected (${loggedOn.result}).")
                abort(false)
                return
            } else {
                do {
                    println("Please enter the authentication code sent to your email address: ")
                    logonDetails.authCode = readlnOrNull().orEmpty()
                } while ("" == logonDetails.authCode)
            }

            println("Retrying Steam3 connection...")
            connect()

            return
        }

        if (loggedOn.result == EResult.TryAnotherCM) {
            println("Retrying Steam3 connection (TryAnotherCM)...")

            reconnect()

            return
        }

        if (loggedOn.result == EResult.ServiceUnavailable) {
            println("Unable to login to Steam3: ${loggedOn.result}")
            abort(false)

            return
        }

        if (loggedOn.result != EResult.OK) {
            println("Unable to login to Steam3: ${loggedOn.result}")
            abort()

            return
        }

        println(" Done!")

        seq++
        isLoggedOn = true

        if (ContentDownloader.config.cellID == 0) {
            println("Using Steam3 suggested CellID: " + loggedOn.cellID)
            ContentDownloader.config.cellID = loggedOn.cellID
        }
    }

    private fun licenseListCallback(licenseList: LicenseListCallback) {
        if (licenseList.result != EResult.OK) {
            println("Unable to get license list: ${licenseList.result} ")
            abort()

            return
        }

        println("Got ${licenseList.licenseList.size} licenses for account!")
        licenses = licenseList.licenseList

        licenseList.licenseList.forEach { license ->
            if (license.accessToken > 0) {
                packageTokens[license.packageID] = license.accessToken
            }
        }
    }

    private fun displayQrCode(challengeUrl: String) {
        ConsoleQrcode.print(challengeUrl)
    }
}
