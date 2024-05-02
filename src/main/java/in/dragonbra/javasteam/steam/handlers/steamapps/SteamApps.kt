package `in`.dragonbra.javasteam.steam.handlers.steamapps

import `in`.dragonbra.javasteam.base.ClientMsg
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.generated.MsgClientGetLegacyGameKey
import `in`.dragonbra.javasteam.generated.MsgClientGetLegacyGameKeyResponse
import `in`.dragonbra.javasteam.generated.MsgClientUpdateGuestPassesList
import `in`.dragonbra.javasteam.generated.MsgClientVACBanStatus
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGameConnectTokens
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGetAppOwnershipTicket
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGetAppOwnershipTicketResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientLicenseList
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientCheckAppBetaPassword
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientCheckAppBetaPasswordResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientGetCDNAuthToken
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientGetCDNAuthTokenResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientGetDepotDecryptionKey
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientGetDepotDecryptionKeyResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientPurchaseResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientRedeemGuestPassResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientRequestFreeLicense
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientRequestFreeLicenseResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo.CMsgClientPICSAccessTokenRequest
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo.CMsgClientPICSAccessTokenResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo.CMsgClientPICSChangesSinceRequest
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo.CMsgClientPICSChangesSinceResponse
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo.CMsgClientPICSProductInfoRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.AppOwnershipTicketCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.CDNAuthTokenCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.CheckAppBetaPasswordCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.DepotKeyCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.FreeLicenseCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.GameConnectTokensCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.GuestPassListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LegacyGameKeyCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.LicenseListCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSChangesCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSProductInfoCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PICSTokensCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.PurchaseResponseCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.RedeemGuestPassResponseCallback
import `in`.dragonbra.javasteam.steam.handlers.steamapps.callback.VACStatusCallback
import `in`.dragonbra.javasteam.types.AsyncJobMultiple
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler is used for interacting with apps and packages on the Steam network.
 */
@Suppress("MemberVisibilityCanBePrivate")
class SteamApps : ClientMsgHandler() {
    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientLicenseList] = Consumer(::handleLicenseList)
        dispatchMap[EMsg.ClientRequestFreeLicenseResponse] = Consumer(::handleFreeLicense)
        dispatchMap[EMsg.ClientPurchaseResponse] = Consumer(::handlePurchaseResponse)
        dispatchMap[EMsg.ClientRedeemGuestPassResponse] = Consumer(::handleRedeemGuestPassResponse)
        dispatchMap[EMsg.ClientGameConnectTokens] = Consumer(::handleGameConnectTokens)
        dispatchMap[EMsg.ClientVACBanStatus] = Consumer(::handleVACBanStatus)
        dispatchMap[EMsg.ClientGetAppOwnershipTicketResponse] = Consumer(::handleAppOwnershipTicketResponse)
        dispatchMap[EMsg.ClientGetDepotDecryptionKeyResponse] = Consumer(::handleDepotKeyResponse)
        dispatchMap[EMsg.ClientGetLegacyGameKeyResponse] = Consumer(::handleLegacyGameKeyResponse)
        dispatchMap[EMsg.ClientPICSAccessTokenResponse] = Consumer(::handlePICSAccessTokenResponse)
        dispatchMap[EMsg.ClientPICSChangesSinceResponse] = Consumer(::handlePICSChangesSinceResponse)
        dispatchMap[EMsg.ClientPICSProductInfoResponse] = Consumer(::handlePICSProductInfoResponse)
        dispatchMap[EMsg.ClientUpdateGuestPassesList] = Consumer(::handleGuestPassList)
        dispatchMap[EMsg.ClientGetCDNAuthTokenResponse] = Consumer(::handleCDNAuthTokenResponse)
        dispatchMap[EMsg.ClientCheckAppBetaPasswordResponse] = Consumer(::handleCheckAppBetaPasswordResponse)
    }

    /**
     * Requests an app ownership ticket for the specified AppID.
     *  Results are returned in a [AppOwnershipTicketCallback] callback.
     * @param appId The appid to request the ownership ticket of.
     * @return The Job ID of the request. This can be used to find the appropriate [AppOwnershipTicketCallback].
     */
    fun getAppOwnershipTicket(appId: Int): AsyncJobSingle<AppOwnershipTicketCallback> {
        val request = ClientMsgProtobuf<CMsgClientGetAppOwnershipTicket.Builder>(
            CMsgClientGetAppOwnershipTicket::class.java,
            EMsg.ClientGetAppOwnershipTicket
        ).apply {
            val jobID: JobID = client.getNextJobID()
            sourceJobID = jobID
            body.setAppId(appId)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Request the depot decryption key for a specified DepotID.
     *  Results are returned in a [DepotKeyCallback] callback.
     * @param depotId The DepotID to request a decryption key for.
     * @param appId   The AppID parent of the DepotID.
     * @return The Job ID of the request. This can be used to find the appropriate [DepotKeyCallback].
     */
    fun getDepotDecryptionKey(depotId: Int, appId: Int): AsyncJobSingle<DepotKeyCallback> {
        val request = ClientMsgProtobuf<CMsgClientGetDepotDecryptionKey.Builder>(
            CMsgClientGetDepotDecryptionKey::class.java,
            EMsg.ClientGetDepotDecryptionKey
        ).apply {
            val jobID: JobID = client.getNextJobID()
            sourceJobID = jobID
            body.setDepotId(depotId)
            body.setAppId(appId)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Request PICS access tokens for an app or package.
     *  Results are returned in a [PICSTokensCallback] callback.
     * @param app      App id to request access token for.
     * @param package Package id to request access token for.
     * @return The Job ID of the request. This can be used to find the appropriate [PICSTokensCallback].
     */
    fun picsGetAccessTokens(app: Int?, `package`: Int?): AsyncJobSingle<PICSTokensCallback> {
        val apps: List<Int> = if (app != null) listOf(app) else emptyList()
        val packages: List<Int> = if (`package` != null) listOf(`package`) else emptyList()

        return picsGetAccessTokens(apps, packages)
    }

    /**
     * Request PICS access tokens for a list of app ids and package ids
     *  Results are returned in a [PICSTokensCallback] callback.
     * @param appIds     List of app ids to request access tokens for.
     * @param packageIds List of package ids to request access tokens for.
     * @return The Job ID of the request. This can be used to find the appropriate [PICSTokensCallback].
     */
    fun picsGetAccessTokens(appIds: Iterable<Int>, packageIds: Iterable<Int>): AsyncJobSingle<PICSTokensCallback> {
        val request = ClientMsgProtobuf<CMsgClientPICSAccessTokenRequest.Builder>(
            CMsgClientPICSAccessTokenRequest::class.java,
            EMsg.ClientPICSAccessTokenRequest
        ).apply {
            val jobID: JobID = client.getNextJobID()
            sourceJobID = jobID
            body.addAllAppids(appIds)
            body.addAllPackageids(packageIds)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Request changes for apps and packages since a given change number
     *  Results are returned in a [PICSChangesCallback] callback.
     * @param lastChangeNumber      Last change number seen.
     * @param sendAppChangeList     Whether to send app changes.
     * @param sendPackageChangelist Whether to send package changes.
     * @return The Job ID of the request. This can be used to find the appropriate [PICSChangesCallback].
     */
    @JvmOverloads
    fun picsGetChangesSince(
        lastChangeNumber: Int = 0,
        sendAppChangeList: Boolean = true,
        sendPackageChangelist: Boolean = false,
    ): AsyncJobSingle<PICSChangesCallback> {
        val request = ClientMsgProtobuf<CMsgClientPICSChangesSinceRequest.Builder>(
            CMsgClientPICSChangesSinceRequest::class.java,
            EMsg.ClientPICSChangesSinceRequest
        ).apply {
            val jobID = client.getNextJobID()
            sourceJobID = jobID
            body.setSinceChangeNumber(lastChangeNumber)
            body.setSendAppInfoChanges(sendAppChangeList)
            body.setSendPackageInfoChanges(sendPackageChangelist)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Request product information for an app or package
     *  Results are returned in a [PICSProductInfoCallback] callback.
     * @param app          App id requested.
     * @param package     Package id requested.
     * @param metaDataOnly Whether to send only meta-data.
     * @return The Job ID of the request. This can be used to find the appropriate [PICSProductInfoCallback].
     */
    @JvmOverloads
    fun picsGetProductInfo(
        app: PICSRequest? = null,
        `package`: PICSRequest? = null,
        metaDataOnly: Boolean = false,
    ): AsyncJobMultiple<PICSProductInfoCallback> {
        val apps: List<PICSRequest> = if (app != null) listOf(app) else listOf()
        val packages: List<PICSRequest> = if (`package` != null) listOf(`package`) else listOf()

        return picsGetProductInfo(apps, packages, metaDataOnly)
    }

    /**
     * Request product information for a list of apps or packages
     *  Results are returned in a [PICSProductInfoCallback] callback.
     * @param apps         List of [PICSRequest] requests for apps.
     * @param packages     List of [PICSRequest] requests for packages.
     * @param metaDataOnly Whether to send only metadata.
     * @return The Job ID of the request. This can be used to find the appropriate [PICSProductInfoCallback].
     */
    @JvmOverloads
    fun picsGetProductInfo(
        apps: List<PICSRequest>,
        packages: List<PICSRequest>,
        metaDataOnly: Boolean = false,
    ): AsyncJobMultiple<PICSProductInfoCallback> {
        val request = ClientMsgProtobuf<CMsgClientPICSProductInfoRequest.Builder>(
            CMsgClientPICSProductInfoRequest::class.java,
            EMsg.ClientPICSProductInfoRequest
        ).apply {
            val jobID = client.getNextJobID()
            sourceJobID = jobID
            apps.forEach { appRequest ->
                CMsgClientPICSProductInfoRequest.AppInfo.newBuilder().apply {
                    setAccessToken(appRequest.accessToken)
                    setAppid(appRequest.id)
                    setOnlyPublicObsolete(false)
                }.also(body::addApps)
            }
            packages.forEach { packageRequest ->
                CMsgClientPICSProductInfoRequest.PackageInfo.newBuilder().apply {
                    setAccessToken(packageRequest.accessToken)
                    setPackageid(packageRequest.id)
                }.also(body::addPackages)
            }
            body.setMetaDataOnly(metaDataOnly)
        }

        client.send(request)

        return AsyncJobMultiple(client, request.sourceJobID) { !it.isResponsePending }
    }

    /**
     * Request product information for an app or package
     *  Results are returned in a [CDNAuthTokenCallback] callback.
     * @param app      App id requested.
     * @param depot    Depot id requested.
     * @param hostName CDN host name being requested.
     * @return The Job ID of the request. This can be used to find the appropriate [CDNAuthTokenCallback].
     */
    fun getCDNAuthToken(app: Int, depot: Int, hostName: String): AsyncJobSingle<CDNAuthTokenCallback> {
        val request = ClientMsgProtobuf<CMsgClientGetCDNAuthToken.Builder>(
            CMsgClientGetCDNAuthToken::class.java,
            EMsg.ClientGetCDNAuthToken
        ).apply {
            val jobID = client.getNextJobID()
            sourceJobID = jobID
            body.setAppId(app)
            body.setDepotId(depot)
            body.setHostName(hostName)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Request a free license for given appid, can be used for free on demand apps
     *  Results are returned in a [FreeLicenseCallback] callback.
     * @param app The app to request a free license for.
     * @return The Job ID of the request. This can be used to find the appropriate [FreeLicenseCallback].
     */
    fun requestFreeLicense(app: Int): AsyncJobSingle<FreeLicenseCallback> = requestFreeLicense(listOf(app))

    /**
     * Request a free license for given appids, can be used for free on demand apps
     *  Results are returned in a [FreeLicenseCallback] callback.
     * @param apps The apps to request a free license for.
     * @return The Job ID of the request. This can be used to find the appropriate [FreeLicenseCallback].
     */
    fun requestFreeLicense(apps: Iterable<Int>): AsyncJobSingle<FreeLicenseCallback> {
        val request = ClientMsgProtobuf<CMsgClientRequestFreeLicense.Builder>(
            CMsgClientRequestFreeLicense::class.java,
            EMsg.ClientRequestFreeLicense
        ).apply {
            val jobID = client.getNextJobID()
            sourceJobID = jobID
            body.addAllAppids(apps)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Submit a beta password for a given app to retrieve any betas and their encryption keys.
     *  Results are returned in a [CheckAppBetaPasswordCallback] callback.
     * @param app      App id requested.
     * @param password Password to check.
     * @return The Job ID of the request. This can be used to find the appropriate [CheckAppBetaPasswordCallback].
     */
    fun checkAppBetaPassword(app: Int, password: String): AsyncJobSingle<CheckAppBetaPasswordCallback> {
        val request = ClientMsgProtobuf<CMsgClientCheckAppBetaPassword.Builder>(
            CMsgClientCheckAppBetaPassword::class.java,
            EMsg.ClientCheckAppBetaPassword
        ).apply {
            val jobID = client.getNextJobID()
            sourceJobID = jobID
            body.setAppId(app)
            body.setBetapassword(password)
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    /**
     * Request the legacy CD game keys for the requested appid.
     * @param appId The AppID to request game keys for.
     * @return The Job ID of the request. This can be used to find the appropriate [LegacyGameKeyCallback]
     */
    fun getLegacyGameKey(appId: Int): AsyncJobSingle<LegacyGameKeyCallback> {
        val request = ClientMsg(MsgClientGetLegacyGameKey::class.java).apply {
            val jobID = client.getNextJobID()
            sourceJobID = jobID
            sourceJobID = jobID
            body.appId = appId
        }

        client.send(request)

        return AsyncJobSingle(client, request.sourceJobID)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleAppOwnershipTicketResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientGetAppOwnershipTicketResponse.Builder>(
            CMsgClientGetAppOwnershipTicketResponse::class.java,
            packetMsg
        ).also { ticketResponse ->
            AppOwnershipTicketCallback(ticketResponse.targetJobID, ticketResponse.body).also(client::postCallback)
        }
    }

    private fun handleDepotKeyResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientGetDepotDecryptionKeyResponse.Builder>(
            CMsgClientGetDepotDecryptionKeyResponse::class.java,
            packetMsg
        ).also { keyResponse ->
            DepotKeyCallback(keyResponse.targetJobID, keyResponse.body).also(client::postCallback)
        }
    }

    private fun handleGameConnectTokens(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientGameConnectTokens.Builder>(
            CMsgClientGameConnectTokens::class.java,
            packetMsg
        ).also { gcTokens ->
            GameConnectTokensCallback(gcTokens.body).also(client::postCallback)
        }
    }

    private fun handleLegacyGameKeyResponse(packetMsg: IPacketMsg) {
        ClientMsg(MsgClientGetLegacyGameKeyResponse::class.java, packetMsg).also { keyResponse ->
            LegacyGameKeyCallback(
                keyResponse.targetJobID,
                keyResponse.body,
                keyResponse.payload.toByteArray()
            ).also(client::postCallback)
        }
    }

    private fun handleLicenseList(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientLicenseList.Builder>(
            CMsgClientLicenseList::class.java,
            packetMsg
        ).also { licenseList ->
            LicenseListCallback(licenseList.body).also(client::postCallback)
        }
    }

    private fun handleFreeLicense(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientRequestFreeLicenseResponse.Builder>(
            CMsgClientRequestFreeLicenseResponse::class.java,
            packetMsg
        ).also { grantedLicenses ->
            FreeLicenseCallback(grantedLicenses.targetJobID, grantedLicenses.body).also(client::postCallback)
        }
    }

    private fun handlePurchaseResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientPurchaseResponse.Builder>(
            CMsgClientPurchaseResponse::class.java,
            packetMsg
        ).also { response ->
            PurchaseResponseCallback(response.targetJobID, response.body).also(client::postCallback)
        }
    }

    private fun handleRedeemGuestPassResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientRedeemGuestPassResponse.Builder>(
            CMsgClientRedeemGuestPassResponse::class.java,
            packetMsg
        ).also { response ->
            RedeemGuestPassResponseCallback(response.targetJobID, response.body).also(client::postCallback)
        }
    }

    private fun handleVACBanStatus(packetMsg: IPacketMsg) {
        ClientMsg(MsgClientVACBanStatus::class.java, packetMsg).also { vacStatus ->
            VACStatusCallback(vacStatus.body, vacStatus.payload.toByteArray()).also(client::postCallback)
        }
    }

    private fun handlePICSAccessTokenResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientPICSAccessTokenResponse.Builder>(
            CMsgClientPICSAccessTokenResponse::class.java,
            packetMsg
        ).also { tokensResponse ->
            PICSTokensCallback(tokensResponse.targetJobID, tokensResponse.body).also(client::postCallback)
        }
    }

    private fun handlePICSChangesSinceResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientPICSChangesSinceResponse.Builder>(
            CMsgClientPICSChangesSinceResponse::class.java,
            packetMsg
        ).also { changesResponse ->
            PICSChangesCallback(changesResponse.targetJobID, changesResponse.body).also(client::postCallback)
        }
    }

    private fun handlePICSProductInfoResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserverAppinfo.CMsgClientPICSProductInfoResponse.Builder>(
            SteammessagesClientserverAppinfo.CMsgClientPICSProductInfoResponse::class.java,
            packetMsg
        ).also { productResponse ->
            PICSProductInfoCallback(productResponse.targetJobID, productResponse.body).also(client::postCallback)
        }
    }

    private fun handleGuestPassList(packetMsg: IPacketMsg) {
        ClientMsg(MsgClientUpdateGuestPassesList::class.java, packetMsg).also { guestPasses ->
            GuestPassListCallback(guestPasses.body, guestPasses.payload).also(client::postCallback)
        }
    }

    private fun handleCDNAuthTokenResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientGetCDNAuthTokenResponse.Builder>(
            CMsgClientGetCDNAuthTokenResponse::class.java,
            packetMsg
        ).also { response ->
            CDNAuthTokenCallback(response.targetJobID, response.body).also(client::postCallback)
        }
    }

    private fun handleCheckAppBetaPasswordResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientCheckAppBetaPasswordResponse.Builder>(
            CMsgClientCheckAppBetaPasswordResponse::class.java,
            packetMsg
        ).also { response ->
            CheckAppBetaPasswordCallback(response.targetJobID, response.body).also(client::postCallback)
        }
    }
}
