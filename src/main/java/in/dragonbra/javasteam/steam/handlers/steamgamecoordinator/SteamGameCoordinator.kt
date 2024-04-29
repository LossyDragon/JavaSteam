package `in`.dragonbra.javasteam.steam.handlers.steamgamecoordinator

import com.google.protobuf.ByteString
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.base.gc.IClientGCMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2
import `in`.dragonbra.javasteam.steam.handlers.steamgamecoordinator.callback.MessageCallback
import `in`.dragonbra.javasteam.util.MsgUtil
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler handles all game coordinator messaging.
 */
class SteamGameCoordinator : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientFromGC] = Consumer<IPacketMsg>(::handleFromGC)
    }

    /**
     * Sends a game coordinator message for a specific appid.
     * @param msg   The GC message to send.
     * @param appId The app id of the game coordinator to send to.
     */
    fun send(msg: IClientGCMsg, appId: Int) {
        ClientMsgProtobuf<SteammessagesClientserver2.CMsgGCClient.Builder>(
            SteammessagesClientserver2.CMsgGCClient::class.java,
            EMsg.ClientToGC
        ).apply {
            protoHeader.setRoutingAppid(appId)
            body.setMsgtype(MsgUtil.makeGCMsg(msg.msgType, msg.isProto))
            body.setAppid(appId)
            body.setPayload(ByteString.copyFrom(msg.serialize()))
        }.also(client::send)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleFromGC(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<SteammessagesClientserver2.CMsgGCClient.Builder>(
            SteammessagesClientserver2.CMsgGCClient::class.java,
            packetMsg
        ).also { msg ->
            MessageCallback(msg.targetJobID, msg.body).also(client::postCallback)
        }
    }
}
