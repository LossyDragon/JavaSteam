package `in`.dragonbra.javasteam.contentdownloader

data class DownloadConfig(
    var cellID: Int = 0,
    var downloadAllPlatforms: Boolean = false,
    var downloadAllArchs: Boolean = false,
    var downloadAllLanguages: Boolean = false,
    var downloadManifestOnly: Boolean = false,
    var installDirectory: String? = null,

    var usingFileList: Boolean = false,
    var filesToDownload: HashSet<String> = hashSetOf(),
    var filesToDownloadRegex: MutableList<Regex> = mutableListOf(),

    var betaPassword: String? = null,

    var verifyAll: Boolean = false,

    var maxServers: Int = 0,
    var maxDownloads: Int = 0,

    var rememberPassword: Boolean = false,

    // A Steam LoginID to allow multiple concurrent connections
    var loginID: Int? = null,

    var useQrCode: Boolean = false,
)
