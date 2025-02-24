package `in`.dragonbra.javasteam.steam.contentdownloader

import org.apache.commons.lang3.SystemUtils

internal object Util {

    @JvmStatic
    fun getSteamOS(): String {
        return when {
            SystemUtils.IS_OS_WINDOWS -> "windows"
            SystemUtils.IS_OS_MAC || SystemUtils.IS_OS_MAC_OSX -> "macos"
            SystemUtils.IS_OS_LINUX -> "linux"
            SystemUtils.IS_OS_FREE_BSD -> "linux" // Return linux as freebsd steam client doesn't exist yet
            else -> "unknown"
        }
    }

}
