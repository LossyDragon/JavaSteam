package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.util.crypto.CryptoException
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger

/**
 * @author lngtr
 * @since 2018-02-24
 */
class NetFilterEncryptionWithHMAC(sessionKey: ByteArray) : INetFilterEncryption {

    private val sessionKey: ByteArray

    private val hmacSecret: ByteArray

    init {
        if (sessionKey.size != 32) {
            logger.debug("AES session key was not 32 bytes!")
            throw IllegalStateException("AES session key was not 32 bytes!")
        }

        this.sessionKey = sessionKey
        this.hmacSecret = ByteArray(16)

        System.arraycopy(sessionKey, 0, hmacSecret, 0, hmacSecret.size)
    }

    override fun processIncoming(data: ByteArray): ByteArray {
        try {
            return CryptoHelper.symmetricDecryptHMACIV(data, sessionKey, hmacSecret)
        } catch (e: CryptoException) {
            throw IllegalStateException("Unable to decrypt incoming packet", e)
        }
    }

    override fun processOutgoing(data: ByteArray): ByteArray {
        try {
            return CryptoHelper.symmetricEncryptWithHMACIV(data, sessionKey, hmacSecret)
        } catch (e: CryptoException) {
            throw IllegalStateException("Unable to encrypt outgoing packet", e)
        }
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(NetFilterEncryptionWithHMAC::class.java)
    }
}
