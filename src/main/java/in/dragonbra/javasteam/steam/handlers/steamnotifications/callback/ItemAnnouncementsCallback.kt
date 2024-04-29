package `in`.dragonbra.javasteam.steam.handlers.steamnotifications.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientItemAnnouncements
import `in`.dragonbra.javasteam.steam.handlers.steamnotifications.SteamNotifications
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * Fired in response to calling [SteamNotifications.requestItemAnnouncements].
 */
@Suppress("unused")
class ItemAnnouncementsCallback(msg: CMsgClientItemAnnouncements.Builder) : CallbackMsg() {

    /**
     * Gets the number of new items
     * @return the number of new items
     */
    val count: Int = msg.countNewItems
}
