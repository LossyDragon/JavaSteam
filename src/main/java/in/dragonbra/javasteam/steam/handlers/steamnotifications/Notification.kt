package `in`.dragonbra.javasteam.steam.handlers.steamnotifications

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientUserNotifications

/**
 * Represents a notification.
 */
@Suppress("unused")
class Notification(notification: CMsgClientUserNotifications.Notification) {

    /**
     * Gets the number of notifications
     * @return the number of notifications
     */
    val count: Int = notification.count

    /**
     * Gets the type of the notification
     * @return the type of the notification
     */
    val type: Int = notification.userNotificationType
}
