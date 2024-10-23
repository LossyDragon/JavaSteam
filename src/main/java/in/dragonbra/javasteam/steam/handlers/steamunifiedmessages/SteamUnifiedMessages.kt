package `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages

import com.google.protobuf.AbstractMessage
import com.google.protobuf.GeneratedMessage
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.base.PacketClientMsgProtobuf
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodNotification
import `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages.callback.ServiceMethodResponse
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.util.log.LogManager

/**
 * @author Lossy
 * @since 2024-10-22
 *
 * This handler is used for interacting with Steamworks unified messaging.
 */
class SteamUnifiedMessages : ClientMsgHandler() {

    companion object {
        private val logger = LogManager.getLogger(SteamUnifiedMessages::class.java)
    }

    internal val handlers: MutableMap<String, UnifiedService> = mutableMapOf()

    /**
     * Creates a service that can be used to send messages and receive notifications via Steamworks unified messaging.
     * @param TService The type of the service to create.
     * @param serviceClass The type of the service to create.
     * @return The instance to create requests.
     */
    fun <TService : UnifiedService> createService(serviceClass: Class<TService>): TService {
        val constructor = serviceClass.getDeclaredConstructor(SteamUnifiedMessages::class.java)
        val service = constructor.newInstance(this)

        handlers[service.serviceName] = service
        return service
    }

    /**
     * Removes a service so it no longer can be used to send messages or receive notifications.
     * @param TService The type of the service to remove.
     * @param serviceClass The type of the service to remove.
     */
    fun <TService : UnifiedService> removeService(serviceClass: Class<TService>) {
        val constructor = serviceClass.getDeclaredConstructor(SteamUnifiedMessages::class.java)
        val serviceName = constructor.newInstance(null).serviceName
        handlers.remove(serviceName)
    }

    /**
     * Sends a message.
     * Results are returned in a [ServiceMethodResponse] with type [TResult].
     * The returned [AsyncJobSingle] can also be awaited to retrieve the callback result.
     * @param TRequest The type of protobuf object.
     * @param TResult The type of the result of the request.
     * @param name Name of the RPC endpoint. Takes the format ServiceName.RpcName.
     * @param message The message to send.
     * @return The JobID of the request. This can be used to find the appropriate [ServiceMethodResponse].
     */
    fun <TRequest : GeneratedMessage.Builder<TRequest>, TResult : GeneratedMessage.Builder<TResult>> sendMessage(
        name: String,
        message: GeneratedMessage,
    ): AsyncJobSingle<ServiceMethodResponse<TResult>> {
        val eMsg = if (client.steamID == null) {
            EMsg.ServiceMethodCallFromClientNonAuthed
        } else {
            EMsg.ServiceMethodCallFromClient
        }
        val msg = ClientMsgProtobuf<TRequest>(message.javaClass, eMsg).apply {
            sourceJobID = client.getNextJobID()

            header.proto.targetJobName = name
            body.mergeFrom(message)
        }

        client.send(msg)

        return AsyncJobSingle(client, msg.sourceJobID)
    }

    /**
     * Sends a notification.
     * @param TRequest The type of protobuf object.
     * @param name Name of the RPC endpoint. Takes the format ServiceName.RpcName.
     * @param message The message to send.
     */
    fun <TRequest : GeneratedMessage.Builder<TRequest>> sendNotification(
        name: String,
        message: GeneratedMessage,
    ) {
        // Notifications do not set source jobid, otherwise Steam server will actively reject this message
        // if the method being used is a "Notification"
        val eMsg = if (client.steamID == null) {
            EMsg.ServiceMethodCallFromClientNonAuthed
        } else {
            EMsg.ServiceMethodCallFromClient
        }
        val msg = ClientMsgProtobuf<TRequest>(message.javaClass, eMsg).apply {
            header.proto.targetJobName = name
            body.mergeFrom(message)
        }

        client.send(msg)
    }

    /**
     * Handles a client message. This should not be called directly.
     * @param packetMsg The packet message that contains the data.
     */
    override fun handleMsg(packetMsg: IPacketMsg) {
        val packetMsgProto = packetMsg as? PacketClientMsgProtobuf ?: return

        if (packetMsgProto.msgType !in listOf(EMsg.ServiceMethod, EMsg.ServiceMethodResponse)) {
            logger.debug("packetMsgPro is not ServiceMethod or ServiceMethodResponse")
            return
        }

        val jobName = packetMsgProto.header.proto.targetJobName
        if (jobName.isEmpty()) {
            logger.debug("incoming unified message has empty jobName")
            return
        }

        // format: Service.Method#Version
        val dot = jobName.indexOf('.')
        val hash = jobName.lastIndexOf('#')
        if (dot < 0 || hash < 0) {
            return
        }

        val serviceName = jobName.substring(0, dot)
        val handler = handlers[serviceName]

        if (handler == null) {
            logger.debug("Unable to find unified handler for $serviceName ($jobName)")
            return
        }

        val methodName = jobName.substring(dot + 1, hash)

        when (packetMsgProto.msgType) {
            EMsg.ServiceMethodResponse -> handler.handleResponseMsg(methodName, packetMsgProto)
            EMsg.ServiceMethod -> handler.handleNotificationMsg(methodName, packetMsgProto)
            else -> Unit // Ignore everything else.
        }
    }

    internal fun <TService : GeneratedMessage.Builder<TService>> handleResponseMsg(
        serviceClass: Class<out AbstractMessage>,
        packetMsg: PacketClientMsgProtobuf,
    ) {
        val callback = ServiceMethodResponse<TService>(serviceClass, packetMsg)
        client.postCallback(callback)
    }

    internal fun <TService : GeneratedMessage.Builder<TService>> handleNotificationMsg(
        serviceClass: Class<out AbstractMessage>,
        packetMsg: PacketClientMsgProtobuf,
    ) {
        val callback = ServiceMethodNotification<TService>(serviceClass, packetMsg)
        client.postCallback(callback)
    }
}
