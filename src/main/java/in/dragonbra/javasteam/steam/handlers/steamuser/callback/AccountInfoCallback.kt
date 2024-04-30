package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import `in`.dragonbra.javasteam.enums.EAccountFlags
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLogin.CMsgClientAccountInfo
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import java.util.*

/**
 * This callback is received when account information is received from the network.
 * This generally happens after logon.
 */
@Suppress("unused")
class AccountInfoCallback(msg: CMsgClientAccountInfo.Builder) : CallbackMsg() {

    /**
     * Gets the last recorded persona name used by this account.
     * @return the last recorded persona name used by this account.
     */
    val personaName: String = msg.personaName

    /**
     * Gets the country this account is connected from.
     * @return the country this account is connected from.
     */
    val country: String = msg.ipCountry

    /**
     * Gets the count of SteamGuard authenticated computers.
     * @return the count of SteamGuard authenticated computers.
     */
    val countAuthedComputers: Int = msg.countAuthedComputers

    /**
     * Gets the account flags for this account.
     * @return the account flags for this account. See [EAccountFlags].
     */
    val accountFlags: EnumSet<EAccountFlags> = EAccountFlags.from(msg.accountFlags)

    /**
     * Gets the facebook ID of this account if it is linked with facebook.
     * @return the facebook ID of this account if it is linked with facebook.
     */
    val facebookID: Long = msg.facebookId

    /**
     * Gets the facebook name if this account is linked with facebook.
     * @return the facebook name if this account is linked with facebook.
     */
    val facebookName: String = msg.facebookName
}
