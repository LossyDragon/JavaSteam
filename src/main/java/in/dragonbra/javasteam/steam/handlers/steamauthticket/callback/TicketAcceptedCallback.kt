package `in`.dragonbra.javasteam.steam.handlers.steamauthticket.callback

import `in`.dragonbra.javasteam.base.ClientMsgProtobuf
import `in`.dragonbra.javasteam.base.IPacketMsg
import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserver.CMsgClientAuthListAck
import `in`.dragonbra.javasteam.steam.steamclient.callbackmgr.CallbackMsg

/**
 * This callback is fired when Steam accepts our auth ticket as valid.
 *
 * TODO: -- Experimental --
 */
class TicketAcceptedCallback(packetMsg: IPacketMsg) : CallbackMsg() {

    /**
     * A list of AppIDs of the games that have generated tickets.
     */
    val appIDs: List<Int>

    /**
     * A list of CRC32 hashes of activated tickets.
     */
    val activeTicketsCRC: List<Int>

    /**
     * Number of message in sequence.
     */
    val messageSequence: Int

    init {
        val msg = ClientMsgProtobuf<CMsgClientAuthListAck.Builder>(
            CMsgClientAuthListAck::class.java,
            packetMsg
        )
        val resp = msg.body

        jobID = msg.targetJobID

        appIDs = resp.appIdsList
        activeTicketsCRC = resp.ticketCrcList
        messageSequence = resp.messageSequence
    }
}
