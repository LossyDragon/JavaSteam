package `in`.dragonbra.javasteam.steam.handlers.steamauthticket

import `in`.dragonbra.javasteam.util.Utils
import java.lang.AutoCloseable

/**
 * Represents a valid authorized session ticket.
 *
 * TODO: -- Experimental --
 *
 * @param handler The handler.
 * @param appID Application the ticket was generated for.
 * @param ticket Bytes of the valid Session Ticket
 */
class TicketInfo(
    private val handler: SteamAuthTicket,
    internal val appID: Int,
    val ticket: ByteArray,
) : AutoCloseable {

    internal val ticketCRC: Long = Utils.crc32(ticket)

    override fun close() {
        handler.cancelAuthTicket(this)
    }
}
