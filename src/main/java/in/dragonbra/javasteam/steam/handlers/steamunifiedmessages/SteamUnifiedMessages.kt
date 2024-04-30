package `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages

import com.google.protobuf.AbstractMessage
import com.google.protobuf.GeneratedMessage
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.base.PacketClientMsgProtobuf
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodNotification
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.types.JobID
import `in`.dragonbra.javasteam.util.compat.Consumer
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.util.*

/**
 * @author Lossy
 * @since 2023-01-04
 *
 * This handler is used for interacting with Steamworks unified messaging
 */
class SteamUnifiedMessages : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ServiceMethodResponse] = Consumer<IPacketMsg>(::handleServiceMethodResponse)
        dispatchMap[EMsg.ServiceMethod] = Consumer<IPacketMsg>(::handleServiceMethod)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    /**
     * Sends a message.
     * Results are returned in a [ServiceMethodResponse].
     * @param TRequest The type of protobuf object.
     * @param rpcName Name of the RPC endpoint. Takes the format ServiceName.RpcName
     * @param message The message to send.
     * @return The [JobID] of the request. This can be used to find the appropriate [ServiceMethodResponse].
     */
    fun <TRequest : GeneratedMessage.Builder<TRequest>> sendMessage(
        rpcName: String,
        message: GeneratedMessage,
    ): AsyncJobSingle<ServiceMethodResponse> {
        val jobID: JobID = client.getNextJobID()
        val eMsg = if (client.steamID == null) {
            EMsg.ServiceMethodCallFromClientNonAuthed
        } else {
            EMsg.ServiceMethodCallFromClient
        }

        ClientMsgProtobuf<TRequest>(message.javaClass, eMsg).apply {
            sourceJobID = jobID
            header.proto.setTargetJobName(rpcName)
            body.mergeFrom(message)
        }.also(client::send)

        return AsyncJobSingle(client, jobID)
    }

    /**
     * Sends a notification.
     * @param TRequest The type of protobuf object.
     * @param rpcName Name of the RPC endpoint. Takes the format ServiceName.RpcName
     * @param message The message to send.
     */
    fun <TRequest : GeneratedMessage.Builder<TRequest>> sendNotification(
        rpcName: String,
        message: GeneratedMessage,
    ) {
        val eMsg = if (client.steamID == null) {
            EMsg.ServiceMethodCallFromClientNonAuthed
        } else {
            EMsg.ServiceMethodCallFromClient
        }

        ClientMsgProtobuf<TRequest>(message.javaClass, eMsg).apply {
            header.proto.setTargetJobName(rpcName)
            body.mergeFrom(message)
        }.also(client::send)
    }

    private fun handleServiceMethodResponse(packetMsg: IPacketMsg) {
        require(packetMsg is PacketClientMsgProtobuf) { "Packet message is expected to be protobuf." }

        ServiceMethodResponse(packetMsg).also(client::postCallback)
    }

    private fun handleServiceMethod(packetMsg: IPacketMsg) {
        require(packetMsg is PacketClientMsgProtobuf) { "Packet message is expected to be protobuf." }

        val jobName: String = packetMsg.header.proto.getTargetJobName()

        if (jobName.isNotEmpty()) {
            val splitByDot = jobName.split(".").toTypedArray()
            val splitByHash = splitByDot[1].split("#").toTypedArray()

            val serviceName = splitByDot[0]
            val methodName = splitByHash[0]

            val serviceInterfaceName = "in.dragonbra.javasteam.rpc.interfaces.I$serviceName"
            try {
                logger.debug("Handling Service Method: $serviceInterfaceName")

                val serviceInterfaceType = Class.forName(serviceInterfaceName)
                val method = serviceInterfaceType.declaredMethods.find { it.name == methodName }

                method?.let {
                    @Suppress("UNCHECKED_CAST")
                    val argumentType = it.parameterTypes[0] as Class<out AbstractMessage>

                    ServiceMethodNotification(argumentType, packetMsg).also(client::postCallback)
                }
            } catch (e: ClassNotFoundException) {
                // The RPC service implementation was not implemented.
                // Either the .proto is missing, or the service was not converted to an interface yet.
                logger.error("Service Method: $serviceName, was not found")
            }
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(SteamUnifiedMessages::class.java)
    }
}
