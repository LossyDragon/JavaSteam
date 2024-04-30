package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver2.CMsgClientEmailAddrInfo
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * This callback is received when email information is received from the network.
 */
@Suppress("unused")
class EmailAddrInfoCallback(msg: CMsgClientEmailAddrInfo.Builder) : CallbackMsg() {

    /**
     * Gets the email address of this account.
     * @return the email address of this account.
     */
    val emailAddress: String = msg.emailAddress

    /**
     * Gets a value indicating validated email or not.
     * @return a value indicating validated email or not.
     */
    val isEmailValidated: Boolean = msg.emailIsValidated

    /**
     * TODO kDoc
     * @return ???
     */
    val isEmailValidationChanged: Boolean = msg.emailValidationChanged

    /**
     * TODO kDoc
     * @return ???
     */
    val isCredentialChangeRequiresCode: Boolean = msg.credentialChangeRequiresCode

    /**
     * TODO kDoc
     * @return ???
     */
    val isPasswordOrSecretqaChangeRequiresCode: Boolean = msg.passwordOrSecretqaChangeRequiresCode
}
