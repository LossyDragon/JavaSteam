package `in`.dragonbra.javasteam.networking.steam3

/**
 * @author lngtr
 * @since 2018-02-24
 */
interface INetFilterEncryption {
    fun processIncoming(data: ByteArray): ByteArray
    fun processOutgoing(data: ByteArray): ByteArray
}
