package `in`.dragonbra.javasteam.steam.handlers.steamnotifications.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientCommentNotifications
import `in`.dragonbra.javasteam.steam.handlers.steamnotifications.SteamNotifications
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * Fired in response to calling [SteamNotifications.requestCommentNotifications].
 */
@Suppress("unused")
class CommentNotificationsCallback(msg: CMsgClientCommentNotifications.Builder) : CallbackMsg() {

    /**
     * Gets the number of new comments
     * @return the number of new comments
     */
    val commentCount: Int = msg.countNewComments

    /**
     * Gets the number of new comments on the users profile
     * @return the number of new comments on the users profile
     */
    val commentOwnerCount: Int = msg.countNewCommentsOwner

    /**
     * Gets the number of new comments on subscribed threads
     * @return the number of new comments on subscribed threads
     */
    val commentSubscriptionsCount: Int = msg.countNewCommentsSubscriptions
}
