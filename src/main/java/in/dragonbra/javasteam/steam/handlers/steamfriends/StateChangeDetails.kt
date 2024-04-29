package `in`.dragonbra.javasteam.steam.handlers.steamfriends

import `in`.dragonbra.javasteam.enums.EChatMemberStateChange
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.util.log.LogManager
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import java.io.ByteArrayInputStream
import java.io.IOException
import java.util.*

/**
 * Represents state change information.
 */
@Suppress("MemberVisibilityCanBePrivate")
class StateChangeDetails(data: ByteArray) {

    companion object {
        private val logger = LogManager.getLogger(StateChangeDetails::class.java)
    }

    /**
     * Gets the SteamID of the chatter that was acted on.
     * @return the [SteamID] of the chatter that was acted on.
     */
    var chatterActedOn: SteamID? = null
        private set

    /**
     * Gets the state change for the acted on SteamID.
     * @return the state change for the acted on SteamID.
     */
    var stateChange: EnumSet<EChatMemberStateChange>? = null
        private set

    /**
     * Gets the SteamID of the chatter that acted on [StateChangeDetails.chatterActedOn].
     * @return the [SteamID] of the chatter that acted on [StateChangeDetails.chatterActedOn].
     */
    var chatterActedBy: SteamID? = null
        private set

    /**
     * Gets the member information for a user that has joined the chat room.
     *  This field is only populated when [StateChangeDetails.stateChange] is [EChatMemberStateChange.Entered].
     * @return the member information for a user that has joined the chat room.
     */
    var memberInfo: ChatMemberInfo? = null
        private set

    init {
        try {
            BinaryReader(ByteArrayInputStream(data)).use { br ->
                chatterActedOn = SteamID(br.readLong())
                stateChange = EChatMemberStateChange.from(br.readInt())
                chatterActedBy = SteamID(br.readLong())
                if (stateChange!!.contains(EChatMemberStateChange.Entered)) {
                    memberInfo = ChatMemberInfo().apply {
                        readFromStream(br)
                    }
                }
            }
        } catch (e: IOException) {
            logger.error("Unable to read StateChangeDetails data", e)
        }
    }
}
