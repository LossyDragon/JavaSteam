package `in`.dragonbra.javasteam.steam.handlers.steamnotifications

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientCommentNotifications
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientItemAnnouncements
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientOfflineMessageNotification
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientRequestCommentNotifications
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientRequestItemAnnouncements
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientRequestOfflineMessageCount
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientUserNotifications
import `in`.dragonbra.javasteam.steam.handlers.steamnotifications.callback.CommentNotificationsCallback
import `in`.dragonbra.javasteam.steam.handlers.steamnotifications.callback.ItemAnnouncementsCallback
import `in`.dragonbra.javasteam.steam.handlers.steamnotifications.callback.OfflineMessageNotificationCallback
import `in`.dragonbra.javasteam.steam.handlers.steamnotifications.callback.UserNotificationsCallback
import `in`.dragonbra.javasteam.util.compat.Consumer
import java.util.*

/**
 * This handler handles steam notifications.
 */
class SteamNotifications : ClientMsgHandler() {

    private var dispatchMap: EnumMap<EMsg, Consumer<IPacketMsg>> = EnumMap(EMsg::class.java)

    init {
        dispatchMap[EMsg.ClientUserNotifications] = Consumer(::handleUserNotifications)
        dispatchMap[EMsg.ClientChatOfflineMessageNotification] = Consumer(::handleOfflineMessageNotification)
        dispatchMap[EMsg.ClientCommentNotifications] = Consumer(::handleCommentNotifications)
        dispatchMap[EMsg.ClientItemAnnouncements] = Consumer(::handleItemAnnouncements)
    }

    /**
     * Request comment notifications.
     * Results are returned in a [CommentNotificationsCallback].
     */
    fun requestCommentNotifications() {
        ClientMsgProtobuf<CMsgClientRequestCommentNotifications.Builder>(
            CMsgClientRequestCommentNotifications::class.java,
            EMsg.ClientRequestCommentNotifications
        ).also(client::send)
    }

    /**
     * Request new items notifications.
     * Results are returned in a [ItemAnnouncementsCallback].
     */
    fun requestItemAnnouncements() {
        ClientMsgProtobuf<CMsgClientRequestItemAnnouncements.Builder>(
            CMsgClientRequestItemAnnouncements::class.java,
            EMsg.ClientRequestItemAnnouncements
        ).also(client::send)
    }

    /**
     * Request offline message count.
     * Results are returned in a [OfflineMessageNotificationCallback].
     */
    fun requestOfflineMessageCount() {
        ClientMsgProtobuf<CMsgClientRequestOfflineMessageCount.Builder>(
            CMsgClientRequestOfflineMessageCount::class.java,
            EMsg.ClientChatRequestOfflineMessageCount
        ).also(client::send)
    }

    override fun handleMsg(packetMsg: IPacketMsg) {
        dispatchMap[packetMsg.msgType]?.accept(packetMsg)
    }

    private fun handleUserNotifications(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientUserNotifications.Builder>(
            CMsgClientUserNotifications::class.java,
            packetMsg
        ).also { msg ->
            UserNotificationsCallback(msg.body).also(client::postCallback)
        }
    }

    private fun handleOfflineMessageNotification(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientOfflineMessageNotification.Builder>(
            CMsgClientOfflineMessageNotification::class.java,
            packetMsg
        ).also { msg ->
            OfflineMessageNotificationCallback(msg.body).also(client::postCallback)
        }
    }

    private fun handleCommentNotifications(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientCommentNotifications.Builder>(
            CMsgClientCommentNotifications::class.java,
            packetMsg
        ).also { msg ->
            CommentNotificationsCallback(msg.body).also(client::postCallback)
        }
    }

    private fun handleItemAnnouncements(packetMsg: IPacketMsg) {
        ClientMsgProtobuf<CMsgClientItemAnnouncements.Builder>(
            CMsgClientItemAnnouncements::class.java,
            packetMsg
        ).also { msg ->
            ItemAnnouncementsCallback(msg.body).also(client::postCallback)
        }
    }
}
