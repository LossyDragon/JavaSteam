package `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback

import com.google.protobuf.AbstractMessage
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages

/**
 * @author Lossy
 * @since 2023-01-04
 *
 * This callback represents a service notification received though [SteamUnifiedMessages].
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ServiceMethodNotification(messageType: Class<out AbstractMessage>, packetMsg: IPacketMsg) : CallbackMsg() {

    // Note: JobID will be -1

    /**
     * @return the client message, See [ClientMsgProtobuf]
     */
    val clientMsg: ClientMsgProtobuf<*> = ClientMsgProtobuf(messageType, packetMsg) // Bounce into generic-land.

    /**
     * Gets the full name of the service method.
     * @return the full name of the service method.
     */
    val methodName: String = clientMsg.header.proto.getTargetJobName()

    /**
     * Gets the protobuf notification body.
     * @return the protobuf notification body.
     */
    val body: Any = clientMsg.body.build()

    /**
     * @return the Proto Header, See [SteammessagesBase.CMsgProtoBufHeader]
     */
    val protoHeader: SteammessagesBase.CMsgProtoBufHeader = clientMsg.header.proto.build()

    /**
     * Gets the name of the Service.
     * @return the name of the Service.
     */
    val serviceName: String
        get() = methodName.split(".")[0]

    /**
     * Gets the name of the RPC method.
     * @return the name of the RPC method.
     */
    val rpcName: String
        get() = methodName.substring(serviceName.length + 1).split('#')[0]
}
