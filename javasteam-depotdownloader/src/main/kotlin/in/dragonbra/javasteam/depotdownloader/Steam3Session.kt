package `in`.dragonbra.javasteam.depotdownloader

import `in`.dragonbra.javasteam.steam.handlers.steamapps.License
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSProductInfo
import `in`.dragonbra.javasteam.steam.handlers.steamapps.PICSRequest
import `in`.dragonbra.javasteam.steam.handlers.steamapps.SteamApps
import `in`.dragonbra.javasteam.steam.handlers.steamcloud.SteamCloud
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.CDNAuthToken
import `in`.dragonbra.javasteam.steam.handlers.steamcontent.SteamContent
import `in`.dragonbra.javasteam.steam.handlers.steamuser.SteamUser
import `in`.dragonbra.javasteam.steam.steamclient.SteamClient
import kotlinx.coroutines.CompletableDeferred
import java.util.concurrent.ConcurrentHashMap

/**
 *
 */
class Steam3Session(
    internal val config: DownloadConfig,
    internal val steamClient: SteamClient,
    internal val licenses: List<License>,
) : AutoCloseable {

    internal var steamUser: SteamUser? = null
    internal var steamContent: SteamContent? = null
    internal var steamApps: SteamApps? = null
    internal var steamCloud: SteamCloud? = null
    internal var steamPublishedFile: PublishedFile? = null

    private val appTokens: MutableMap<UInt, ULong> = mutableMapOf()
    private val packageTokens: MutableMap<UInt, ULong> = mutableMapOf()
    private val depotKeys: MutableMap<UInt, ByteArray> = mutableMapOf()
    private val cdnAuthTokens: ConcurrentHashMap<Pair<UInt, String>, CompletableDeferred<CDNAuthToken>> =
        ConcurrentHashMap()
    internal val appInfo: MutableMap<UInt, PICSProductInfo> = mutableMapOf()
    internal val packageInfo: MutableMap<UInt, PICSProductInfo?> = mutableMapOf()

    init {
        steamUser = steamClient.getHandler<SteamUser>()!!
        steamContent = steamClient.getHandler<SteamContent>()!!
        steamApps = steamClient.getHandler<SteamApps>()!!
        steamCloud = steamClient.getHandler<SteamCloud>()!!
    }

    override fun close() {
        steamUser = null
        steamContent = null
        steamApps = null
        steamCloud = null
    }

    suspend fun requestPackageInfo(packageIds: ArrayList<UInt>) {
        packageIds.removeAll(packageInfo.keys)

        if (packageIds.isEmpty()) return

        val packageRequests = arrayListOf<PICSRequest>()

        packageIds.forEach { pkg ->
            val request = PICSRequest(id = pkg.toInt())

            packageTokens[pkg]?.let { token ->
                request.accessToken = token.toLong()
            }

            packageRequests.add(request)
        }

        val packageInfoMultiple = steamApps!!.picsGetProductInfo(emptyList(), packageRequests).await()

        packageInfoMultiple.results.forEach { pkgInfo ->
            pkgInfo.packages.forEach { pkgValue ->
                val pkg = pkgValue.value
                packageInfo[pkg.id.toUInt()] = pkg
            }
            pkgInfo.unknownPackages.forEach { pkgValue ->
                packageInfo[pkgValue.toUInt()] = null
            }
        }
    }
}
