package `in`.dragonbra.javasteam.steam.steamclient.configuration

import `in`.dragonbra.javasteam.enums.EClientPersonaStateFlag
import `in`.dragonbra.javasteam.enums.EUniverse
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.steam.discovery.IServerListProvider
import okhttp3.OkHttpClient
import java.util.*

/**
 * @author lngtr
 * @since 2018-02-20
 */
// Primitives can't be lateinit
class SteamConfigurationState {
    lateinit var defaultPersonaStateFlags: EnumSet<EClientPersonaStateFlag>
    lateinit var httpClient: OkHttpClient
    lateinit var protocolTypes: EnumSet<ProtocolTypes>
    lateinit var serverListProvider: IServerListProvider
    lateinit var universe: EUniverse
    lateinit var webAPIBaseAddress: String
    lateinit var webAPIKey: String
    var cellID: Int = 0
    var connectionTimeout: Long = 0L
    var isAllowDirectoryFetch: Boolean = false

    companion object {
        @JvmStatic
        fun EClientPersonaStateFlag.toEnumSet(): EnumSet<EClientPersonaStateFlag> = EnumSet.of(this)

        @JvmStatic
        fun ProtocolTypes.toEnumSet(): EnumSet<ProtocolTypes> = EnumSet.of(this)
    }
}
