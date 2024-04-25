package `in`.dragonbra.javasteam.steam.handlers.steamapps

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverAppinfo

/**
 * Holds the change data for a single app or package
 */
@Suppress("MemberVisibilityCanBePrivate")
class PICSChangeData {

    /**
     * App or package ID this change data represents
     * @return the app or package ID.
     */
    val id: Int

    /**
     * Current change number of this app
     * @return the current change number.
     */
    val changeNumber: Int

    /**
     * Signals if an access token is needed for this request
     * @return if an access token is needed.
     */
    val isNeedsToken: Boolean

    /**
     * TODO kDoc
     */
    constructor(change: SteammessagesClientserverAppinfo.CMsgClientPICSChangesSinceResponse.AppChange) {
        id = change.appid
        changeNumber = change.changeNumber
        isNeedsToken = change.needsToken
    }

    /**
     * TODO kDoc
     */
    constructor(change: SteammessagesClientserverAppinfo.CMsgClientPICSChangesSinceResponse.PackageChange) {
        id = change.packageid
        changeNumber = change.changeNumber
        isNeedsToken = change.needsToken
    }
}
