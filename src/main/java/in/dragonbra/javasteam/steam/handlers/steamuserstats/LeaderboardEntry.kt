package `in`.dragonbra.javasteam.steam.handlers.steamuserstats

import `in`.dragonbra.javasteam.protobufs.steamclient.SteammessagesClientserverLbs.CMsgClientLBSGetLBEntriesResponse
import `in`.dragonbra.javasteam.types.SteamID
import `in`.dragonbra.javasteam.types.UGCHandle
import `in`.dragonbra.javasteam.util.stream.BinaryReader
import `in`.dragonbra.javasteam.util.stream.MemoryStream
import java.io.IOException
import java.util.*

/**
 * Represents a single package in this response.
 */
@Suppress("unused")
class LeaderboardEntry(entry: CMsgClientLBSGetLBEntriesResponse.Entry) {

    /**
     * Gets the [SteamID] for this entry.
     * @return the [SteamID] for this entry.
     */
    val steamID: SteamID = SteamID(entry.steamIdUser)

    /**
     * Gets the global rank for this entry.
     * @return the global rank for this entry.
     */
    val globalRank: Int = entry.globalRank

    /**
     * Gets the score for this entry.
     * @return the score for this entry.
     */
    val score: Int = entry.score

    /**
     * Gets the [UGCHandle] attached to this entry.
     * @return the [UGCHandle] attached to this entry.
     */
    val ugcId: UGCHandle = UGCHandle(entry.ugcId)

    /**
     * Extra game-defined information regarding how the user got that score.
     * @return extra game-defined information regarding how the user got that score.
     */
    var details: MutableList<Int> = ArrayList()
        private set

    init {
        if (entry.details != null) {
            val ms = MemoryStream(entry.details.toByteArray())
            val br = BinaryReader(ms)

            try {
                while (ms.length - ms.position > 4) {
                    details.add(br.readInt())
                }
            } catch (e: IOException) {
                throw IllegalArgumentException("failed to read details", e)
            }

            details = Collections.unmodifiableList(details)
        }
    }
}
