package `in`.dragonbra.javasteam.steam.handlers.steamunifiedmessages

import com.google.protobuf.GeneratedMessage
import `in`.dragonbra.javasteam.base.PacketClientMsgProtobuf
import java.util.function.Supplier

/**
 * @author Lossy
 * @since 2024-10-22
 *
 * @constructor Abstract definition of a steam unified messages service.
 * @property unifiedMessages A reference to the [SteamUnifiedMessages] instance this service was created from.
 */
@Suppress("unused")
abstract class UnifiedService(val unifiedMessages: SteamUnifiedMessages? = null) {

    /**
     * Handles a response message for this service. This should not be called directly.
     * @param methodName The name of the method the service should handle.
     * @param packetMsg The packet message that contains the data.
     */
    abstract fun handleResponseMsg(methodName: String, packetMsg: PacketClientMsgProtobuf)

    /**
     * Handles a notification message for this service. This should not be called directly.
     * @param methodName The name of the method the service should handle.
     * @param packetMsg The packet message that contains the data.
     */
    abstract fun handleNotificationMsg(methodName: String, packetMsg: PacketClientMsgProtobuf)

    /**
     * Dispatches the provided data as a service method response.
     * @param TResponse The type of the response.
     * @param newBody Factory for the response builder, typically a `newBuilder` method reference.
     * @param packetMsg The packet message that contains the data.
     */
    protected fun <TResponse : GeneratedMessage.Builder<TResponse>> postResponseMsg(
        newBody: Supplier<TResponse>,
        packetMsg: PacketClientMsgProtobuf,
    ) {
        unifiedMessages?.handleResponseMsg(newBody, packetMsg)
    }

    /**
     * Dispatches the provided data as a service method notification.
     * @param TNotification The type of the notification.
     * @param newBody Factory for the notification builder, typically a `newBuilder` method reference.
     * @param packetMsg The packet message that contains the data.
     */
    protected fun <TNotification : GeneratedMessage.Builder<TNotification>> postNotificationMsg(
        newBody: Supplier<TNotification>,
        packetMsg: PacketClientMsgProtobuf,
    ) {
        unifiedMessages?.handleNotificationMsg(newBody, packetMsg)
    }

    /**
     * The name of the steam unified messages service.
     */
    abstract val serviceName: String
}
