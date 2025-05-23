package `in`.dragonbra.javasteam.steam.handlers.steamapps

import java.net.InetAddress

@Suppress("ArrayInDataClass")
data class GamePlayedInfo(
    val steamIdGs: Long = 0,
    val gameId: Long,
    val deprecatedGameIpAddress: Int = 0,
    val gamePort: Int = 0,
    val isSecure: Boolean = false,
    val token: ByteArray = byteArrayOf(),
    val gameExtraInfo: String = "",
    val gameDataBlob: ByteArray? = null,
    val processId: Int,
    val streamingProviderId: Int = 0,
    val gameFlags: Int = 0,
    val ownerId: Int,
    val vrHmdVendor: String = "",
    val vrHmdModel: String = "",
    val launchOptionType: Int = 0,
    val primaryControllerType: Int = -1,
    val primarySteamControllerSerial: String = "",
    val totalSteamControllerCount: Int = 0,
    val totalNonSteamControllerCount: Int = 0,
    val controllerWorkshopFileId: Long = 0,
    val launchSource: Int = 0,
    val vrHmdRuntime: Int = 0,
    val gameIpAddress: InetAddress? = null,
    val controllerConnectionType: Int = 0,
    val gameOsPlatform: Int = -1,
    val gameBuildId: Int,
    val compatToolId: Int = 0,
    val compatToolCmd: String = "",
    val compatToolBuildId: Int = 0,
    val betaName: String = "",
    val dlcContext: Int = 0,
    val processIdList: List<AppProcessInfo> = emptyList(),
)
