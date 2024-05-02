package `in`.dragonbra.javasteam.types

import `in`.dragonbra.javasteam.enums.EAccountType
import `in`.dragonbra.javasteam.enums.EUniverse
import `in`.dragonbra.javasteam.util.CollectionUtils
import `in`.dragonbra.javasteam.util.compat.ObjectsCompat
import java.util.regex.*

/**
 * This 64-bit structure is used for identifying various objects on the Steam network.
 */
@Suppress("unused", "MemberVisibilityCanBePrivate")
class SteamID {

    private var steamID: BitVector64

    /**
     * Initializes a new instance of the [SteamID] class, using 0 as the id.
     */
    constructor() : this(0L)

    /**
     * Initializes a new instance of the [SteamID] class.
     * @param id The 64bit integer to assign this SteamID from.
     */
    constructor(id: Long) {
        this.steamID = BitVector64(id)
    }

    /**
     * Initializes a new instance of the [SteamID] class.
     * @param unAccountID  The account ID.
     * @param eUniverse    The universe.
     * @param eAccountType The account type.
     */
    constructor(unAccountID: Long, eUniverse: EUniverse, eAccountType: EAccountType?) : this() {
        set(unAccountID, eUniverse, eAccountType)
    }

    /**
     * Initializes a new instance of the [SteamID] class.
     * @param unAccountID  The account ID.
     * @param unInstance   The instance.
     * @param eUniverse    The universe.
     * @param eAccountType The account type.
     */
    constructor(unAccountID: Long, unInstance: Long, eUniverse: EUniverse, eAccountType: EAccountType?) : this() {
        instancedSet(unAccountID, unInstance, eUniverse, eAccountType)
    }

    /**
     * Initializes a new instance of the [SteamID] class from a Steam2 "STEAM_" rendered form and universe.
     * @param steamId   A "STEAM_" rendered form of the SteamID.
     * @param eUniverse The universe the SteamID belongs to.
     */
    @JvmOverloads
    constructor(steamId: String, eUniverse: EUniverse = EUniverse.Public) : this() {
        setFromString(steamId, eUniverse)
    }

    /**
     * Sets the various components of this SteamID instance.
     * @param unAccountID  The account ID.
     * @param eUniverse    The universe.
     * @param eAccountType The account type.
     */
    fun set(unAccountID: Long, eUniverse: EUniverse, eAccountType: EAccountType?) {
        accountID = unAccountID
        accountUniverse = eUniverse
        accountType = eAccountType
        accountInstance = if (eAccountType == EAccountType.Clan || eAccountType == EAccountType.GameServer) {
            0L
        } else {
            DESKTOP_INSTANCE
        }
    }

    /**
     * Sets the various components of this SteamID instance.
     * @param unAccountID  The account ID.
     * @param unInstance   The instance.
     * @param eUniverse    The universe.
     * @param eAccountType The account type.
     */
    fun instancedSet(unAccountID: Long, unInstance: Long, eUniverse: EUniverse, eAccountType: EAccountType?) {
        accountID = unAccountID
        accountUniverse = eUniverse
        accountType = eAccountType
        accountInstance = unInstance
    }

    /**
     * Sets the various components of this SteamID from a Steam2 "STEAM_" rendered form and universe.
     * @param steamId   A "STEAM_" rendered form of the SteamID.
     * @param eUniverse The universe the SteamID belongs to.
     * @return **true** if this instance was successfully assigned; otherwise, **false** if the given string was in an invalid format.
     */
    fun setFromString(steamId: String?, eUniverse: EUniverse): Boolean {
        if (steamId.isNullOrEmpty()) {
            return false
        }

        val matcher = STEAM2_REGEX.matcher(steamId)

        if (!matcher.matches()) {
            return false
        }

        val accountId: Long
        val authServer: Long
        try {
            accountId = matcher.group(3).toLong()
            authServer = matcher.group(2).toLong()
        } catch (nfe: NumberFormatException) {
            return false
        }

        accountUniverse = eUniverse
        accountInstance = 1
        accountType = EAccountType.Individual
        accountID = accountId shl 1 or authServer

        return true
    }

    /**
     * Sets the various components of this SteamID from a Steam3 "[X:1:2:3]" rendered form and universe.
     * @param steamId A "[X:1:2:3]" rendered form of the SteamID.
     * @return **true** if this instance was successfully assigned; otherwise, **false** if the given string was in an invalid format.
     */
    fun setFromSteam3String(steamId: String?): Boolean {
        if (steamId.isNullOrEmpty()) {
            return false
        }

        var matcher = STEAM3_REGEX.matcher(steamId)

        if (!matcher.matches()) {
            matcher = STEAM3_FALLBACK_REGEX.matcher(steamId)

            if (!matcher.matches()) {
                return false
            }
        }

        val accId: Long
        val universe: Long

        try {
            accId = matcher.group(3).toLong()
            universe = matcher.group(2).toLong()
        } catch (nfe: NumberFormatException) {
            return false
        }

        val typeString = matcher.group(1)

        if (typeString.length != 1) {
            return false
        }

        val type = typeString[0]

        val instanceGroup = matcher.group(5)

        var instance: Long = if (!instanceGroup.isNullOrEmpty()) {
            instanceGroup.toLong()
        } else {
            when (type) {
                'g', 'T', 'c', 'L' -> 0
                else -> 1
            }
        }

        when (type) {
            'c' -> {
                instance = instance or ChatInstanceFlags.CLAN.code
                accountType = EAccountType.Chat
            }

            'L' -> {
                instance = instance or ChatInstanceFlags.LOBBY.code
                accountType = EAccountType.Chat
            }

            UNKNOWN_ACCOUNT_TYPE_CHAR -> accountType = EAccountType.Invalid
            else -> accountType = CollectionUtils.getKeyByValue(ACCOUNT_TYPE_CHARS, type)
        }

        accountUniverse = EUniverse.from(universe.toInt())
        accountInstance = instance
        accountID = accId

        return true
    }

    /**
     * Sets the various components of this SteamID from a 64bit integer form.
     * @param longSteamId The 64bit integer to assign this SteamID from.
     */
    fun setFromUInt64(longSteamId: Long) {
        steamID.data = longSteamId
    }

    /**
     * Converts this SteamID into it's 64bit integer form.
     * @return A 64bit integer representing this SteamID.
     */
    fun convertToUInt64(): Long = steamID.data!!

    /**
     * Returns a static account key used for grouping accounts with differing instances.
     * @return A 64bit static account key.
     */
    val staticAccountKey: Long
        get() = (accountUniverse.code().toLong() shl 56) + (accountType!!.code().toLong() shl 52) + accountID

    /**
     * Gets a value indicating whether this instance is a game server account.
     * @return **true** if this instance is a blank anon account; otherwise, **false**.
     */
    val isBlankAnonAccount: Boolean
        get() = accountID == 0L && isAnonAccount && accountInstance == 0L

    /**
     * Gets a value indicating whether this instance is a game server account.
     * @return **true** if this instance is a game server account; otherwise, **false**.
     */
    val isGameServerAccount: Boolean
        get() = accountType == EAccountType.GameServer || accountType == EAccountType.AnonGameServer

    /**
     * Gets a value indicating whether this instance is a persistent game server account.
     * @return **true** if this instance is a persistent game server account; otherwise, **false**.
     */
    val isPersistentGameServerAccount: Boolean
        get() = accountType == EAccountType.GameServer

    /**
     * Gets a value indicating whether this instance is an anonymous game server account.
     * @return **true** if this instance is an anon game server account; otherwise, **false**.
     */
    val isAnonGameServerAccount: Boolean
        get() = accountType == EAccountType.AnonGameServer

    /**
     * Gets a value indicating whether this instance is a content server account.
     * @return **true** if this instance is a content server account; otherwise, **false**.
     */
    val isContentServerAccount: Boolean
        get() = accountType == EAccountType.ContentServer

    /**
     * Gets a value indicating whether this instance is a clan account.
     * @return **true** if this instance is a clan account; otherwise, **false**.
     */
    val isClanAccount: Boolean
        get() = accountType == EAccountType.Clan

    /**
     * Gets a value indicating whether this instance is a chat account.
     * @return **true** if this instance is a chat account; otherwise, **false**.
     */
    val isChatAccount: Boolean
        get() = accountType == EAccountType.Chat

    /**
     * Gets a value indicating whether this instance is a lobby.
     * @return **true** if this instance is a lobby; otherwise, **false**.
     */
    val isLobby: Boolean
        get() = accountType == EAccountType.Chat && (accountInstance and ChatInstanceFlags.LOBBY.code) > 0

    /**
     * Gets a value indicating whether this instance is an individual account.
     * @return **true** if this instance is an individual account; otherwise, **false**.
     */
    val isIndividualAccount: Boolean
        get() = accountType == EAccountType.Individual || accountType == EAccountType.ConsoleUser

    /**
     * Gets a value indicating whether this instance is an anonymous account.
     * @return **true** if this instance is an anon account; otherwise, **false**.
     */
    val isAnonAccount: Boolean
        get() = accountType == EAccountType.AnonUser || accountType == EAccountType.AnonGameServer

    /**
     * Gets a value indicating whether this instance is an anonymous user account.
     * @return **true** if this instance is an anon user account; otherwise, **false**.
     */
    val isAnonUserAccount: Boolean
        get() = accountType == EAccountType.AnonUser

    /**
     * Gets a value indicating whether this instance is a console user account.
     * @return **true** if this instance is a console user account; otherwise, **false**.
     */
    val isConsoleUserAccount: Boolean
        get() = accountType == EAccountType.ConsoleUser

    /**
     * Gets a value indicating whether this instance is valid.
     * @return **true** if this instance is valid; otherwise, **false**.
     */
    val isValid: Boolean
        get() {
            if (accountType!!.code() <= EAccountType.Invalid.code() || accountType!!.code() > EAccountType.AnonUser.code()) {
                return false
            }

            if (accountUniverse.code() <= EUniverse.Invalid.code() || accountUniverse.code() > EUniverse.Dev.code()) {
                return false
            }

            if (accountType == EAccountType.Individual) {
                if (accountID == 0L || accountInstance > WEB_INSTANCE) return false
            }

            if (accountType == EAccountType.Clan) {
                if (accountID == 0L || accountInstance != 0L) return false
            }

            if (accountType == EAccountType.GameServer) {
                if (accountID == 0L) return false
            }

            return true
        }

    var accountID: Long
        get() = steamID.getMask(0.toShort(), 0xFFFFFFFFL)
        set(accountID) {
            steamID.setMask(0.toShort(), 0xFFFFFFFFL, accountID)
        }

    var accountInstance: Long
        get() = steamID.getMask(32.toShort(), 0xFFFFFL)
        set(accountInstance) {
            steamID.setMask(32.toShort(), 0xFFFFFL, accountInstance)
        }

    var accountType: EAccountType?
        get() = EAccountType.from(steamID.getMask(52.toShort(), 0xFL).toInt())
        set(accountType) {
            steamID.setMask(
                52.toShort(),
                0xFL,
                (accountType?.code() ?: UNKNOWN_ACCOUNT_TYPE_CHAR.code).toLong()
            )
        }

    var accountUniverse: EUniverse
        get() = EUniverse.from(steamID.getMask(56.toShort(), 0xFFL).toInt())
        set(accountUniverse) {
            steamID.setMask(56.toShort(), 0xFFL, accountUniverse.code().toLong())
        }

    /**
     * Converts this clan ID to a chat ID.
     * @return The Chat ID for this clan's group chat.
     * @throws IllegalStateException This SteamID is not a clan ID.
     */
    fun toChatID(): SteamID {
        check(isClanAccount) { "Only Clan IDs can be converted to Chat IDs." }

        return SteamID(convertToUInt64()).apply {
            accountInstance = ChatInstanceFlags.CLAN.code
            accountType = EAccountType.Chat
        }
    }

    /**
     * Converts this chat ID to a clan ID. This can be used to get the group that a group chat is associated with.
     * @return the group that this chat ID is associated with, null if this does not represent a group chat
     */
    fun tryGetClanID(): SteamID? {
        if (isChatAccount && accountInstance == ChatInstanceFlags.CLAN.code) {
            return SteamID(convertToUInt64()).apply {
                accountType = EAccountType.Clan
                accountInstance = 0
            }
        }

        return null
    }

    /**
     * Renders this instance into it's Steam2 "STEAM_" or Steam3 representation.
     * @param steam3 If set to **true**, the Steam3 rendering will be returned; otherwise, the Steam2 STEAM_ rendering.
     * @return A string Steam2 "STEAM_" representation of this SteamID, or a Steam3 representation.
     */
    @JvmOverloads
    fun render(steam3: Boolean = true): String = if (steam3) renderSteam3() else renderSteam2()

    private fun renderSteam2(): String {
        return when (accountType) {
            EAccountType.Invalid,
            EAccountType.Individual,
            -> {
                val universeDigit = if ((accountUniverse.code() <= EUniverse.Public.code())) {
                    "0"
                } else {
                    accountUniverse.code().toString()
                }

                String.format("STEAM_%s:%d:%d", universeDigit, accountID and 1L, accountID shr 1)
            }

            else -> steamID.data.toString()
        }
    }

    private fun renderSteam3(): String {
        var accountTypeChar = ACCOUNT_TYPE_CHARS[accountType]

        if (accountTypeChar == null) {
            accountTypeChar = UNKNOWN_ACCOUNT_TYPE_CHAR
        }

        if (accountType == EAccountType.Chat) {
            if ((accountInstance and ChatInstanceFlags.CLAN.code) > 0) {
                accountTypeChar = 'c'
            } else if ((accountInstance and ChatInstanceFlags.LOBBY.code) > 0) {
                accountTypeChar = 'L'
            }
        }

        var renderInstance = false

        when (accountType) {
            EAccountType.AnonGameServer, EAccountType.Multiseat -> renderInstance = true
            EAccountType.Individual -> renderInstance = (accountInstance != DESKTOP_INSTANCE)
            else -> Unit
        }

        if (renderInstance) {
            return String.format("[%s:%d:%d:%d]", accountTypeChar, accountUniverse.code(), accountID, accountInstance)
        }

        return String.format("[%s:%d:%d]", accountTypeChar, accountUniverse.code(), accountID)
    }

    /**
     * Returns a [String] that represents this instance.
     * @return A [String] that represents this instance.
     */
    override fun toString(): String = render()

    /**
     * Determines whether the specified [Object] is equal to this instance.
     * @param other The [Object] to compare with this instance.
     * @return **true** if the specified [Object] is equal to this instance; otherwise, **false**.
     */
    override fun equals(other: Any?): Boolean {
        if (other !is SteamID) {
            return false
        }

        return ObjectsCompat.equals(steamID.data, other.steamID.data)
    }

    /**
     * Returns a hash code for this instance.
     * @return A hash code for this instance, suitable for use in hashing algorithms and data structures like a hash table.
     */
    override fun hashCode(): Int = steamID.data.hashCode()

    /**
     * Represents various flags a chat [SteamID] may have, packed into its instance.
     */
    enum class ChatInstanceFlags(@JvmField val code: Long) {
        /**
         * This flag is set for clan based chat [SteamID]s.
         */
        CLAN((ACCOUNT_INSTANCE_MASK + 1) shr 1),

        /**
         * This flag is set for lobby based chat [SteamID]s.
         */
        LOBBY((ACCOUNT_INSTANCE_MASK + 1) shr 2),

        /**
         * This flag is set for matchmaking lobby based chat [SteamID]s.
         */
        MMS_LOBBY((ACCOUNT_INSTANCE_MASK + 1) shr 3),
        ;

        companion object {
            fun from(code: Long): ChatInstanceFlags? = entries.find { it.code == code }
        }
    }

    companion object {
        private val STEAM2_REGEX = Pattern.compile("STEAM_([0-4]):([0-1]):(\\d+)", Pattern.CASE_INSENSITIVE)

        private val STEAM3_REGEX = Pattern.compile("\\[([AGMPCgcLTIUai]):([0-4]):(\\d+)(:(\\d+))?]")

        private val STEAM3_FALLBACK_REGEX = Pattern.compile("\\[([AGMPCgcLTIUai]):([0-4]):(\\d+)(\\((\\d+)\\))?]")

        private val ACCOUNT_TYPE_CHARS: Map<EAccountType, Char> = mapOf(
            EAccountType.AnonGameServer to 'A',
            EAccountType.GameServer to 'G',
            EAccountType.Multiseat to 'M',
            EAccountType.Pending to 'P',
            EAccountType.ContentServer to 'C',
            EAccountType.Clan to 'g',
            EAccountType.Chat to 'T', // Lobby chat is 'L', Clan chat is 'c'
            EAccountType.Invalid to 'I',
            EAccountType.Individual to 'U',
            EAccountType.AnonUser to 'a'
        )

        const val UNKNOWN_ACCOUNT_TYPE_CHAR: Char = 'i'

        /**
         * The account instance value when representing all instanced [SteamID]s.
         */
        const val ALL_INSTANCES: Long = 0L

        /**
         * The account instance value for a desktop [SteamID].
         */
        const val DESKTOP_INSTANCE: Long = 1L

        /**
         * The account instance value for a console [SteamID].
         */
        const val CONSOLE_INSTANCE: Long = 2L

        /**
         * The account instance for mobile or web based [SteamID]s.
         */
        const val WEB_INSTANCE: Long = 4L

        /**
         * Masking value used for the account id.
         */
        const val ACCOUNT_ID_MASK: Long = 0xFFFFFFFFL

        /**
         * Masking value used for packing chat instance flags into a [SteamID].
         */
        const val ACCOUNT_INSTANCE_MASK: Long = 0x000FFFFFL
    }
}
