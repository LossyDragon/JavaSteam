package `in`.dragonbra.javasteam.networking.steam3

import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.base.Msg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.enums.EUniverse
import `in`.dragonbra.javasteam.generated.MsgChannelEncryptRequest
import `in`.dragonbra.javasteam.generated.MsgChannelEncryptResponse
import `in`.dragonbra.javasteam.generated.MsgChannelEncryptResult
import `in`.dragonbra.javasteam.steam.CMClient
import `in`.dragonbra.javasteam.util.KeyDictionary
import `in`.dragonbra.javasteam.util.crypto.CryptoHelper
import `in`.dragonbra.javasteam.util.crypto.RSACrypto
import `in`.dragonbra.javasteam.util.event.EventArgs
import `in`.dragonbra.javasteam.util.event.EventHandler
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.log.Logger
import java.io.IOException
import java.net.InetAddress
import java.net.InetSocketAddress

/**
 * @author lngtr
 * @since 2018-02-24
 */
class EnvelopeEncryptedConnection(
    private val inner: Connection,
    private val universe: EUniverse,
) : Connection() {

    private var state: EncryptionState? = null

    private var encryption: INetFilterEncryption? = null

    private val onConnected = EventHandler<EventArgs> { _, _ ->
        state = EncryptionState.CONNECTED
    }

    private val onDisconnected = EventHandler<DisconnectedEventArgs> { _, e ->
        state = EncryptionState.DISCONNECTED
        encryption = null

        disconnected.handleEvent(this@EnvelopeEncryptedConnection, e)
    }

    private val onNetMsgReceived: EventHandler<NetMsgEventArgs> =
        object : EventHandler<NetMsgEventArgs> {
            override fun handleEvent(sender: Any, e: NetMsgEventArgs?) {
                if (e == null) {
                    logger.error("NetMsgEventArgs received null event.")
                    return
                }

                if (state == EncryptionState.ENCRYPTED) {
                    val plaintextData = encryption!!.processIncoming(e.data)
                    netMsgReceived.handleEvent(this@EnvelopeEncryptedConnection, e.withData(plaintextData))
                    return
                }

                val packetMsg: IPacketMsg? = CMClient.getPacketMsg(e.data)

                if (packetMsg == null) {
                    logger.debug("Failed to parse message during channel setup, shutting down connection")
                    disconnect(true)
                    return
                }

                if (!isExpectedEMsg(packetMsg.msgType)) {
                    logger.debug("Rejected EMsg: " + packetMsg.msgType + " during channel setup")
                    return
                }

                when (packetMsg.msgType) {
                    EMsg.ChannelEncryptRequest -> handleEncryptRequest(packetMsg)
                    EMsg.ChannelEncryptResult -> handleEncryptResult(packetMsg)
                    else -> Unit
                }
            }
        }

    override val localIP: InetAddress?
        get() = inner.localIP

    override val currentEndPoint: InetSocketAddress?
        get() = inner.currentEndPoint

    override val protocolTypes: ProtocolTypes
        get() = inner.protocolTypes

    init {
        inner.netMsgReceived.addEventHandler(onNetMsgReceived)
        inner.connected.addEventHandler(onConnected)
        inner.disconnected.addEventHandler(onDisconnected)
    }

    private fun handleEncryptRequest(packetMsg: IPacketMsg) {
        val request: Msg<MsgChannelEncryptRequest> = Msg(MsgChannelEncryptRequest::class.java, packetMsg)

        val connectedUniverse: EUniverse = request.body.universe
        val protoVersion: Long = request.body.protocolVersion.toLong()

        logger.debug("Got encryption request. Universe: $connectedUniverse Protocol ver: $protoVersion")

        if (protoVersion != MsgChannelEncryptRequest.PROTOCOL_VERSION.toLong()) {
            logger.debug("Encryption handshake protocol version mismatch!")
        }

        if (connectedUniverse != universe) {
            logger.debug("Expected universe $universe but server reported universe $connectedUniverse")
        }

        var randomChallenge: ByteArray? = null
        if (request.payload.length >= 16) {
            randomChallenge = request.payload.toByteArray()
        }

        val publicKey: ByteArray? = KeyDictionary.getPublicKey(connectedUniverse)
        if (publicKey == null) {
            logger.debug("HandleEncryptRequest got request for invalid universe! Universe: $connectedUniverse Protocol ver: $protoVersion")
            disconnect(false)
        }

        val response: Msg<MsgChannelEncryptResponse> = Msg(MsgChannelEncryptResponse::class.java)

        val tempSessionKey: ByteArray = CryptoHelper.generateRandomBlock(32)
        val encryptedHandshakeBlob: ByteArray

        val rsa = RSACrypto(publicKey)

        if (randomChallenge != null) {
            val blobToEncrypt = ByteArray(tempSessionKey.size + randomChallenge.size)

            System.arraycopy(tempSessionKey, 0, blobToEncrypt, 0, tempSessionKey.size)
            System.arraycopy(randomChallenge, 0, blobToEncrypt, tempSessionKey.size, randomChallenge.size)

            encryptedHandshakeBlob = rsa.encrypt(blobToEncrypt)
        } else {
            encryptedHandshakeBlob = rsa.encrypt(tempSessionKey)
        }

        val keyCrc: ByteArray = CryptoHelper.crcHash(encryptedHandshakeBlob)

        try {
            response.write(encryptedHandshakeBlob)
            response.write(keyCrc)
            response.write(0)
        } catch (e: IOException) {
            logger.debug(e)
        }

        encryption = if (randomChallenge != null) {
            NetFilterEncryptionWithHMAC(tempSessionKey)
        } else {
            NetFilterEncryption(tempSessionKey)
        }

        state = EncryptionState.CHALLENGED

        send(response.serialize())
    }

    private fun handleEncryptResult(packetMsg: IPacketMsg) {
        val result: Msg<MsgChannelEncryptResult> = Msg(MsgChannelEncryptResult::class.java, packetMsg)

        logger.debug("Encryption result: ${result.body.result}")

        checkNotNull(encryption)

        if (result.body.result == EResult.OK && encryption != null) {
            state = EncryptionState.ENCRYPTED
            connected.handleEvent(this, EventArgs.EMPTY)
        } else {
            logger.debug("Encryption channel setup failed")
            disconnect(false)
        }
    }

    private fun isExpectedEMsg(msg: EMsg): Boolean {
        return when (state) {
            EncryptionState.DISCONNECTED -> false
            EncryptionState.CONNECTED -> msg == EMsg.ChannelEncryptRequest
            EncryptionState.CHALLENGED -> msg == EMsg.ChannelEncryptResult
            EncryptionState.ENCRYPTED -> true
            else -> throw IllegalStateException("Unreachable - landed up in undefined state.")
        }
    }

    override fun connect(endPoint: InetSocketAddress, timeout: Int) {
        inner.connect(endPoint, timeout)
    }

    override fun disconnect(userInitiated: Boolean) {
        inner.disconnect(userInitiated)
    }

    override fun send(data: ByteArray) {
        var sendData: ByteArray = data
        if (state == EncryptionState.ENCRYPTED) {
            sendData = encryption!!.processOutgoing(sendData)
        }

        inner.send(sendData)
    }

    private enum class EncryptionState {
        DISCONNECTED,
        CONNECTED,
        CHALLENGED,
        ENCRYPTED,
    }

    companion object {
        private val logger: Logger = LogManager.getLogger(EnvelopeEncryptedConnection::class.java)
    }
}
