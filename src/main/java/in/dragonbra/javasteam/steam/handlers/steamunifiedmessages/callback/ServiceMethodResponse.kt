package `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback

import com.google.protobuf.AbstractMessage
import com.google.protobuf.GeneratedMessage
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.PacketClientMsgProtobuf
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.SteamUnifiedMessages
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.JobID

/**
 * @author Lossy
 * @since 2023-01-04
 *
 * This callback is returned in response to a service method sent through [SteamUnifiedMessages].
 *
 * @param packetMsg Gets the packet message, See [PacketClientMsgProtobuf].
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class ServiceMethodResponse(val packetMsg: PacketClientMsgProtobuf) : CallbackMsg() {

    /**
     * Gets the Proto header, See [SteammessagesBase.CMsgProtoBufHeader].
     * @return the Proto header.
     */
    val protoHeader: SteammessagesBase.CMsgProtoBufHeader = packetMsg.header.proto.build()

    /**
     * Gets the result of the message.
     * @return Gets the result of the message.
     */
    val result: EResult = EResult.from(protoHeader.eresult)

    /**
     * Gets the full name of the service method.
     * @return Gets the full name of the service method.
     */
    val methodName: String = protoHeader.targetJobName

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

    init {
        this.jobID = JobID(protoHeader.jobidTarget)
    }

    /**
     * Deserializes the response into a protobuf object.
     * @param T Protobuf type of the response message.
     * @param clazz The message class, type erasure.
     * @return The response to the message sent through [SteamUnifiedMessages].
     */
    fun <T : GeneratedMessage.Builder<T>> getDeserializedResponse(clazz: Class<out AbstractMessage>): T {
        return ClientMsgProtobuf<T>(clazz, packetMsg).body
    }
}
