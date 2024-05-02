package `in`.dragonbra.javasteam.steam.handlers.steamnotifications.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientUserNotifications
import `in`.dragonbra.javasteam.steam.handlers.steamnotifications.Notification
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * Fired when the client receives user notifications.
 */
class UserNotificationsCallback(msg: CMsgClientUserNotifications.Builder) : CallbackMsg() {

    /**
     * Get the notifications list
     * @return the notifications
     */
    val notifications: List<Notification> = msg.notificationsList.map { Notification(it) }
}
