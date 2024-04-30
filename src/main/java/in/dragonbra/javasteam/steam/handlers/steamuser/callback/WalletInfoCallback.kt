package `in`.dragonbra.javasteam.steam.handlers.steamuser.callback

import `in`.dragonbra.javasteam.enums.ECurrencyCode
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientWalletInfoUpdate
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * This callback is received when wallet info is received from the network.
 */
@Suppress("unused")
class WalletInfoCallback(wallet: CMsgClientWalletInfoUpdate.Builder) : CallbackMsg() {

    /**
     * Gets a value indicating whether this instance has wallet data.
     *  **true** if this instance has wallet data; otherwise, **false**
     * @return a value indicating whether this instance has wallet data.
     */
    val isHasWallet: Boolean = wallet.hasWallet

    /**
     * Gets the currency code for this wallet.
     * @return the currency code for this wallet.
     */
    val currency: ECurrencyCode = ECurrencyCode.from(wallet.currency)

    /**
     * Gets the balance of the wallet as a 32-bit integer, in cents.
     * @return the balance of the wallet as a 32-bit integer, in cents.
     */
    val balance: Int = wallet.balance

    /**
     * Gets the delayed (pending) balance of the wallet as a 32-bit integer, in cents.
     * @return Gets the delayed (pending) balance of the wallet as a 32-bit integer, in cents.
     */
    val balanceDelayed: Int = wallet.balanceDelayed

    /**
     * Gets the balance of the wallet as a 64-bit integer, in cents.
     * @return the balance of the wallet as a 64-bit integer, in cents.
     */
    val longBalance: Long = wallet.balance64

    /**
     * Gets the delayed (pending) balance of the wallet as a 64-bit integer, in cents.
     * @return Gets the delayed (pending) balance of the wallet as a 64-bit integer, in cents.
     */
    val longBalanceDelayed: Long = wallet.balance64Delayed
}
