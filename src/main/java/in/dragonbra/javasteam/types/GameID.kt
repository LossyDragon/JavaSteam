package `in`.dragonbra.javasteam.types

import `in`.dragonbra.javasteam.util.Utils.crc32
import `in`.dragonbra.javasteam.util.compat.ObjectsCompat

/**
 * This 64bit structure represents an app, mod, shortcut, or p2p file on the Steam network.
 *
 * @constructor Initializes a new instance of the [GameID] class.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class GameID(id: Long) {

    private val gameId = BitVector64(id)

    /**
     * Initializes a new instance of the [GameID] class.
     * @param nAppId The 32bit app id to assign this GameID from.
     */
    @JvmOverloads
    constructor(nAppId: Int = 0) : this(nAppId.toLong())

    /**
     * Initializes a new instance of the [GameID] class.
     * @param nAppId  The base app id of the mod.
     * @param modPath The game folder name of the mod.
     */
    constructor(nAppId: Int, modPath: String?) : this(0) {
        appID = nAppId
        appType = GameType.GAME_MOD
        modID = crc32(modPath!!)
    }

    /**
     * Initializes a new instance of the [GameID] class.
     * @param exePath The path to the executable, usually quoted.
     * @param appName The name of the application shortcut.
     */
    constructor(exePath: String?, appName: String?) : this(0) {
        val builder = StringBuilder()

        if (exePath != null) {
            builder.append(exePath)
        }
        if (appName != null) {
            builder.append(appName)
        }

        appID = 0
        appType = GameType.SHORTCUT
        modID = crc32(builder.toString())
    }

    /**
     * Sets the various components of this GameID from a 64bit integer form.
     * @param gameId The 64bit integer to assign this GameID from.
     */
    fun set(gameId: Long) {
        this.gameId.data = gameId
    }

    /**
     * Converts this GameID into it's 64bit integer form.
     * @return A 64bit integer representing this GameID.
     */
    fun toUInt64(): Long = gameId.data!!

    /**
     * Gets or Sets the app id.
     * @return The app ID.
     */
    var appID: Int
        get() = gameId.getMask(0.toShort(), 0xFFFFFFL).toInt()
        set(value) {
            gameId.setMask(0.toShort(), 0xFFFFFFL, value.toLong())
        }

    /**
     * Gets or Sets the type of the app.
     * @return The type of the app.
     */
    var appType: GameType?
        get() = GameType.from(gameId.getMask(24.toShort(), 0xFFL).toInt())
        set(value) {
            gameId.setMask(24.toShort(), 0xFFL, value!!.code.toLong())
        }

    /**
     * Gets or Sets the mod id.
     * @return The mod ID.
     */
    var modID: Long
        get() = gameId.getMask(32.toShort(), 0xFFFFFFFFL)
        set(value) {
            gameId.setMask(32.toShort(), 0xFFFFFFFFL, value)
            gameId.setMask(63.toShort(), 0xFFL, 1L)
        }

    /**
     * Gets a value indicating whether this instance is a mod.
     * @return **true** if this instance is a mod; otherwise, **false**.
     */
    val isMod: Boolean
        get() = appType == GameType.GAME_MOD

    /**
     * Gets a value indicating whether this instance is a shortcut.
     * @return **true** if this instance is a shortcut; otherwise, **false**.
     */
    val isShortcut: Boolean
        get() = appType == GameType.SHORTCUT

    /**
     * Gets a value indicating whether this instance is a peer-to-peer file.
     * @return **true** if this instance is a p2p file; otherwise, **false**.
     */
    val isP2PFile: Boolean
        get() = appType == GameType.P2P

    /**
     * Gets a value indicating whether this instance is a steam app.
     * @return **true** if this instance is a steam app; otherwise, **false**.
     */
    val isSteamApp: Boolean
        get() = appType == GameType.APP

    /**
     * Sets the various components of this GameID from a 64bit integer form.
     * @param longSteamId The 64bit integer to assign this GameID from.
     */
    fun setFromUInt64(longSteamId: Long) {
        gameId.data = longSteamId
    }

    /**
     * Converts this GameID into it's 64bit integer form.
     *
     * @return A 64bit integer representing this GameID.
     */
    fun convertToUInt64(): Long = gameId.data!!

    /**
     * Determines whether the specified [Object] is equal to this instance.
     * @param other The [Object] to compare with this instance.
     * @return **true** if the specified [Object] is equal to this instance; otherwise, **false**.
     */
    override fun equals(other: Any?): Boolean {
        if (other == null) {
            return false
        }

        if (other !is GameID) {
            return false
        }

        return ObjectsCompat.equals(gameId.data, other.gameId.data)
    }

    /**
     * Returns a hash code for this instance.
     * @return A hash code for this instance, suitable for use in hashing algorithms and data structures like a hash table.
     */
    override fun hashCode(): Int = gameId.hashCode()

    /**
     * Returns a [String] that represents this instance.
     * @return a [String] that represents this instance.
     */
    override fun toString(): String = toUInt64().toString()

    /**
     * Represents various types of games.
     */
    enum class GameType(val code: Int) {
        /**
         * A Steam application.
         */
        APP(0),

        /**
         * A game modification.
         */
        GAME_MOD(1),

        /**
         * A shortcut to a program.
         */
        SHORTCUT(2),

        /**
         * A peer-to-peer file.
         */
        P2P(3),
        ;

        companion object {
            fun from(code: Int): GameType? = entries.find { it.code == code }
        }
    }
}
