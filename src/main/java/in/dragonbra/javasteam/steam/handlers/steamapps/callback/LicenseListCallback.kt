package `in`.dragonbra.javasteam.steam.handlers.steamapps.callback

import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientLicenseList
import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * This callback is fired during logon, informing the client of its available licenses.
 */
class LicenseListCallback(msg: CMsgClientLicenseList.Builder) : CallbackMsg() {

    /**
     * Gets the result of the message.
     * @return the result.
     */
    val result: EResult = EResult.from(msg.eresult)

    /**
     * Gets the license list.
     * @return the license list.
     */
    val licenseList: List<License> = msg.licensesList.map { License(it) }
}
