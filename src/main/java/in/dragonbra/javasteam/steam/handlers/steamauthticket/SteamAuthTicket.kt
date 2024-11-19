package `in`.dragonbra.javasteam.steam.handlers.steamauthticket

import com.google.protobuf.ByteString
import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.enums.EMsg
import `in`.dragonbra.javasteam.enums.EResult
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesBase.CMsgAuthTicket
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientAuthList
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientGameConnectTokens
import `in`.dragonbra.javasteam.steam.handlers.ClientMsgHandler
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamauthticket.callback.TicketAcceptedCallback
import `in`.dragonbra.javasteam.steam.handlers.steamauthticket.callback.TicketAuthCompleteCallback
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg
import `in`.dragonbra.javasteam.types.AsyncJobSingle
import `in`.dragonbra.javasteam.util.Utils
import `in`.dragonbra.javasteam.util.stream.BinaryWriter
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import java.nio.ByteBuffer
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.atomic.AtomicInteger
import kotlin.random.Random

/**
 * This handler generates auth session ticket and handles it's verification by steam.
 *
 * TODO: -- Experimental --
 */
class SteamAuthTicket : ClientMsgHandler() {

    private val gameConnectTokens = ConcurrentLinkedQueue<ByteArray>()

    private val ticketsByGame = mutableMapOf<Int, MutableList<CMsgAuthTicket>>()

    private val ticketChangeLock = Any()

    private val sequence = AtomicInteger(0)

    /**
     * Performs [session ticket](https://partner.steamgames.com/doc/api/ISteamUser#GetAuthSessionTicket) generation and validation for specified [appId].
     * @param appId Game to generate ticket for.
     * @return A [TicketInfo] object that provides details about the generated valid authentication session ticket.
     * @throws Exception if user is not logged in, ownership ticket cannot be obtained, or verification fails
     */
    fun getAuthSessionTicket(appId: Int): TicketInfo {
        requireNotNull(client.cellID) { "User not logged in." }

        val apps = requireNotNull(client.getHandler(SteamApps::class.java)) { "Steam Apps instance was null." }

        val appTicket = apps.getAppOwnershipTicket(appId).runBlock()

        if (appTicket.result != EResult.OK) {
            throw Exception(
                "Failed to obtain app ownership ticket. Result: ${appTicket.result}. " +
                    "The user may not own the game or there was an error."
            )
        }

        gameConnectTokens.poll()?.let { token ->
            val authTicket = buildAuthTicket(token)

            // JavaSteam edit: We can't do `out var crc` like in C#
            // https://github.com/Masusder/SteamKit/blob/9ccfef041aeab6270d1b0f8928ab71430cc84744/SteamKit2/SteamKit2/Steam/Handlers/SteamAuthTicket/SteamAuthTicket.cs#L59C69-L59C80
            val crc = Utils.crc32(authTicket).toInt()
            val ticket = verifyTicket(appId, authTicket, crc).runBlock() // Blocking

            // Verify just in case
            if (ticket.activeTicketsCRC.any { it == crc }) {
                val tok = combineTickets(authTicket, appTicket.ticket)
                return TicketInfo(this, appId, tok)
            } else {
                throw Exception("Ticket verification failed.")
            }
        } ?: throw Exception("There's no available game connect tokens left.")
    }

    internal fun cancelAuthTicket(authTicket: TicketInfo) {
        synchronized(ticketChangeLock) {
            ticketsByGame[authTicket.appID]?.removeAll { it.ticketCrc == authTicket.ticketCRC.toInt() }
        }

        sendTickets()
    }

    private fun combineTickets(authTicket: ByteArray, appTicket: ByteArray): ByteArray {
        val len = appTicket.size
        val token = ByteBuffer.allocate(authTicket.size + 4 + len).apply {
            put(authTicket)
            putInt(len)
            put(appTicket)
        }.array()

        return token
    }

    /**
     * Handles generation of auth ticket.
     */
    private fun buildAuthTicket(gameConnectToken: ByteArray): ByteArray {
        val sessionSize =
            4 + // unknown, always 1
                4 + // unknown, always 2
                4 + // public IP v4, optional
                4 + // private IP v4, optional
                4 + // timestamp & uint.MaxValue
                4 // sequence

        val stream = MemoryStream(gameConnectToken.size + 4 + sessionSize)
        BinaryWriter(stream.asOutputStream()).use { writer ->
            writer.write(gameConnectToken.size)
            writer.write(gameConnectToken)

            writer.write(sessionSize)
            writer.write(1)
            writer.write(2)

            val randomBytes = Random.nextBytes(8)
            writer.write(randomBytes)
            writer.write(System.currentTimeMillis().toInt())

            writer.write(sequence.incrementAndGet())
        }

        return stream.toByteArray()
    }

    private fun verifyTicket(appId: Int, authToken: ByteArray, crc: Int): AsyncJobSingle<TicketAcceptedCallback> {
        synchronized(ticketChangeLock) {
            ticketsByGame.getOrPut(appId) { mutableListOf() }.add(
                // Add ticket to specified games list
                CMsgAuthTicket.newBuilder().apply {
                    gameid = appId.toLong()
                    ticket = ByteString.copyFrom(authToken)
                    ticketCrc = crc
                }.build()
            )
        }

        return sendTickets()
    }

    private fun sendTickets(): AsyncJobSingle<TicketAcceptedCallback> {
        val auth = ClientMsgProtobuf<CMsgClientAuthList.Builder>(
            CMsgClientAuthList::class.java,
            EMsg.ClientAuthList
        ).apply {
            body.tokensLeft = gameConnectTokens.size.toInt()

            synchronized(ticketChangeLock) {
                body.addAllAppIds(ticketsByGame.keys)
                // Flatten dictionary into ticket list
                body.addAllTickets(ticketsByGame.values.flatten())
            }

            sourceJobID = client.getNextJobID()
        }

        client.send(auth)

        return AsyncJobSingle(client, auth.sourceJobID)
    }

    /**
     * Handles a client message. This should not be called directly.
     * @param packetMsg The packet message that contains the data.
     */
    override fun handleMsg(packetMsg: IPacketMsg) {
        // ignore messages that we don't have a handler function for
        val callback = getCallback(packetMsg)

        // Ignore messages that we don't have a handler function for
        if (callback != null) {
            client.postCallback(callback)
            return
        }

        // Special handling for some messages because they need access to client or post callbacks differently
        when (packetMsg.msgType) {
            EMsg.ClientGameConnectTokens -> handleGameConnectTokens(packetMsg)
            EMsg.ClientLogOff -> handleLogOffResponse()
            else -> Unit
        }
    }

    //region ClientMsg Handlers
    private fun handleLogOffResponse() {
        gameConnectTokens.clear()
    }

    private fun handleGameConnectTokens(packetMsg: IPacketMsg) {
        val body = ClientMsgProtobuf<CMsgClientGameConnectTokens.Builder>(
            CMsgClientGameConnectTokens::class.java,
            packetMsg
        ).body

        // Add tokens
        body.tokensList.forEach { gameConnectTokens.offer(it.toByteArray()) }

        // Keep only required amount, discard old entries
        while (gameConnectTokens.size > body.maxTokensToKeep) {
            gameConnectTokens.poll()
        }
    }
    //endregion

    companion object {
        private fun getCallback(packetMsg: IPacketMsg): CallbackMsg? = when (packetMsg.msgType) {
            EMsg.ClientAuthListAck -> TicketAcceptedCallback(packetMsg)
            EMsg.ClientTicketAuthComplete -> TicketAuthCompleteCallback(packetMsg)
            else -> null
        }
    }
}
