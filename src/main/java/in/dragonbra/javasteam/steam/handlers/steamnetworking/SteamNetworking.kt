package `in`.dragonbra.javasteam.steam.handlers.steamnetworking

import com.google.protobuf.ByteString
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver
import `in`.dragonbra.javasteam.steam.handlers.steamnetworking.callback.NetworkingCertificateCallback
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler is used for Steam networking sockets
 */
class SteamNetworking : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientNetworkingCertRequestResponse] = Consumer(::handleNetworkingCertRequestResponse)
    }

    /**
     * Request a signed networking certificate from Steam for your Ed25519 public key for the given app id.
     * Results are returned in a [NetworkingCertificateCallback].
     * The returned [AsyncJobSingle] can also be awaited to retrieve the callback result.
     * @param appId     The App ID the certificate will be generated for.
     * @param publicKey Your Ed25519 public key.
     * @return The Job ID of the request. This can be used to find the appropriate [NetworkingCertificateCallback].
     */
    fun requestNetworkingCertificate(appId: Int, publicKey: ByteArray): AsyncJobSingle<NetworkingCertificateCallback> {
        val msg: ClientMsgProtobuf<SteammessagesClientserver.CMsgClientNetworkingCertRequest.Builder> =
            ClientMsgProtobuf<SteammessagesClientserver.CMsgClientNetworkingCertRequest.Builder>(
                SteammessagesClientserver.CMsgClientNetworkingCertRequest::class.java,
                EMsg.ClientNetworkingCertRequest
            ).apply {
                sourceJobID = client.getNextJobID()

                body.setAppId(appId)
                body.setKeyData(ByteString.copyFrom(publicKey))
            }.also(client::send)

        return AsyncJobSingle(client, msg.sourceJobID)
    }

    /**
     * Handles a client message. This should not be called directly.
     * @param packetMsg The packet message that contains the data.
     */
    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleNetworkingCertRequestResponse(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserver.CMsgClientNetworkingCertReply.Builder>(
            SteammessagesClientserver.CMsgClientNetworkingCertReply::class.java,
            packetMsg
        ).also { resp ->
            NetworkingCertificateCallback(resp.targetJobID, resp.body).also(client::postCallback)
        }
    }
}
