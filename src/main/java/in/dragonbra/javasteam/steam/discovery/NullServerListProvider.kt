package `in`.dragonbra.javasteam.steam.discovery

/**
 * @author lngtr
 * @since 2018-02-20
 *
 * A server list provider that returns an empty list, for consumers that populate the server list themselves
 */
@Suppress("unused")
class NullServerListProvider : IServerListProvider {
    /**
     * No-op implementation that returns an empty server list
     * @return an empty server list.
     */
    override fun fetchServerList(): List<ServerRecord> = listOf()

    /**
     * No-op implementation that does not persist server list
     * @param endpoints a server list.
     */
    override fun updateServerList(endpoints: List<ServerRecord>) {
    }
}
