package `in`.dragonbra.javasteam.steam.handlers.steamnotifications.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientOfflineMessageNotification
import `in`.dragonbra.javasteam.steam.handlers.steamnotifications.SteamNotifications
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.SteamID

/**
 * Fired in response to calling [SteamNotifications.requestOfflineMessageCount].
 */
@Suppress("unused")
class OfflineMessageNotificationCallback(msg: CMsgClientOfflineMessageNotification.Builder) : CallbackMsg() {

    /**
     * Gets the number of new messages
     * @return the number of new messages
     */
    val messageCount: Int = msg.offlineMessages

    /**
     * Gets the ids of friends the new messages belong to
     * @return the ids of friends the new messages belong to
     */
    val friendsWithOfflineMessages: List<SteamID> = msg.friendsWithOfflineMessagesList.map { SteamID(it.toLong()) }
}
