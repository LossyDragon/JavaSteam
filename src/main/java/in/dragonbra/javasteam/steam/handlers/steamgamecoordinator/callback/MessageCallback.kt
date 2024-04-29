package `in`.dragonbra.javasteam.steam.handlers.steamgamecoordinator.callback

import `in`.dragonbra.javasteam.base.gc.IPacketGCMsg
import `in`.dragonbra.javasteam.base.gc.PacketClientGCMsg
import `in`.dragonbra.javasteam.base.gc.PacketClientGCMsgProtobuf
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgGCClient
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.MsgUtil

/**
 * This callback is fired when a game coordinator message is recieved from the network.
 */
class MessageCallback(jobID: JobID, gcMsg: CMsgGCClient.Builder) : CallbackMsg() {

    /**
     * Gets the game coordinator message type.
     * @return the game coordinator message type
     */
    val eMsg: Int = MsgUtil.getGCMsg(gcMsg.msgtype) // raw emsg (with protobuf flag, if present)

    /**
     * Gets the AppID of the game coordinator the message is from.
     * @return the AppID of the game coordinator the message is from
     */
    val appID: Int = gcMsg.appid

    /**
     * Gets the actual message.
     * @return the actual message
     */
    val message: IPacketGCMsg = getPacketGCMsg(gcMsg.msgtype, gcMsg.payload.toByteArray())

    /**
     * Gets a value indicating whether this message is protobuf'd.
     * @return **true** if this instance is protobuf'd; otherwise, **false**
     */
    val isProto: Boolean
        get() = MsgUtil.isProtoBuf(eMsg)

    init {
        this.jobID = jobID
    }

    companion object {
        private fun getPacketGCMsg(eMsg: Int, data: ByteArray): IPacketGCMsg {
            val realEMsg: Int = MsgUtil.getGCMsg(eMsg) // strip off the protobuf flag

            return if (MsgUtil.isProtoBuf(eMsg)) {
                PacketClientGCMsgProtobuf(realEMsg, data)
            } else {
                PacketClientGCMsg(realEMsg, data)
            }
        }
    }
}
