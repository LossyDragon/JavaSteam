package `in`.dragonbra.javasteam.depotdownloader

/**
 * TODO
 */
data class DownloadConfig(
    val cellID: Int = 0,
    val downloadAllPlatforms: Boolean = false,
    val downloadAllArchs: Boolean = false,
    val downloadAllLanguages: Boolean = false,
    val downloadManifestOnly: Boolean = false,
    val installDirectory: String = "",
    val usingFileList: Boolean = false,
    val filesToDownload: HashSet<String> = hashSetOf(),
    val filesToDownloadRegex: ArrayList<Regex> = arrayListOf(),
    val betaPassword: String = "",
    val verifyAll: Boolean = false,
    val maxDownloads: Int = 8,
)
