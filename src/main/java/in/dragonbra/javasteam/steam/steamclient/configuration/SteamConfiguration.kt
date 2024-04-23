package `in`.dragonbra.javasteam.steam.steamclient.configuration

import `in`.dragonbra.javasteam.enums.EClientPersonaStateFlag
import `in`.dragonbra.javasteam.enums.EUniverse
import `in`.dragonbra.javasteam.networking.steam3.ProtocolTypes
import `in`.dragonbra.javasteam.steam.discovery.IServerListProvider
import `in`.dragonbra.javasteam.steam.discovery.SmartCMServerList
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import `in`.dragonbra.javasteam.steam.webapi.WebAPI
import `in`.dragonbra.javasteam.util.compat.Consumer
import okhttp3.OkHttpClient
import java.util.*

/**
 * Configuration object to use.
 * This object should not be mutated after it is passed to one or more [SteamClient] objects.
 */
class SteamConfiguration internal constructor(private val state: SteamConfigurationState) {

    /**
     * The server list used for this configuration.
     *  If this configuration is used by multiple [SteamClient] instances, they all share the server list.
     * @return the server list.
     */
    val serverList: SmartCMServerList = SmartCMServerList(this)

    /**
     * @return Whether or not to use the Steam Directory to discover available servers.
     */
    val isAllowDirectoryFetch: Boolean
        get() = state.isAllowDirectoryFetch

    /**
     * @return The Steam Cell ID to prioritize when connecting.
     */
    val cellID: Int
        get() = state.cellID

    /**
     * @return The connection timeout used when connecting to Steam serves.
     */
    val connectionTimeout: Long
        get() = state.connectionTimeout

    /**
     * @return The http client
     */
    val httpClient: OkHttpClient
        get() = state.httpClient

    /**
     * @return The default persona state flags used when requesting information for a new friend, or when calling **SteamFriends.RequestFriendInfo** without specifying flags.
     */
    val defaultPersonaStateFlags: EnumSet<EClientPersonaStateFlag>
        get() = state.defaultPersonaStateFlags

    /**
     * @return The supported protocol types to use when attempting to connect to Steam.
     */
    val protocolTypes: EnumSet<ProtocolTypes>
        get() = state.protocolTypes

    /**
     * @return The server list provider to use.
     */
    val serverListProvider: IServerListProvider
        get() = state.serverListProvider

    /**
     * @return The Universe to connect to. This should always be [EUniverse.Public] unless you work at Valve and are using this internally. If this is you, hello there.
     */
    val universe: EUniverse
        get() = state.universe

    /**
     * @return The base address of the Steam Web API to connect to. Use of "partner.steam-api.com" requires a Partner API key.
     */
    val webAPIBaseAddress: String
        get() = state.webAPIBaseAddress

    /**
     * @return An API key to be used for authorized requests. Keys can be obtained from [Steam Web API Documentation](https://steamcommunity.com/dev) or the Steamworks Partner site.
     */
    val webAPIKey: String
        get() = state.webAPIKey

    /**
     * Retrieves a handler capable of interacting with the specified interface on the Web API.
     * @param webInterface The interface to retrieve a handler for.
     * @return A [WebAPI] object to interact with the Web API.
     */
    fun getWebAPI(webInterface: String): WebAPI = WebAPI(httpClient, webAPIBaseAddress, webInterface, webAPIKey)

    companion object {
        /**
         * Creates a [SteamConfiguration], allowing for configuration.
         * @param configurator A method which is used to configure the configuration.
         * @return A configuration object.
         */
        @JvmStatic
        fun create(configurator: Consumer<ISteamConfigurationBuilder>): SteamConfiguration {
            val builder = SteamConfigurationBuilder()
            configurator.accept(builder)

            return builder.build()
        }

        @JvmStatic
        fun createDefault(): SteamConfiguration {
            return SteamConfiguration(SteamConfigurationBuilder.createDefaultState())
        }
    }
}
