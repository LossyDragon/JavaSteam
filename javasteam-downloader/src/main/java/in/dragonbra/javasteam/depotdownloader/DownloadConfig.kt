package `in`.dragonbra.javasteam.depotdownloader

data class DownloadConfig(
    var cellID: Int = 0,
    var downloadAllPlatforms: Boolean = false,
    var downloadAllArchs: Boolean = false,
    var downloadAllLanguages: Boolean = false,
    var downloadManifestOnly: Boolean = false,
    var installDirectory: String = "",
    var usingFileList: Boolean = false,
    var filesToDownload: HashSet<String> = hashSetOf(),
    var filesToDownloadRegex: MutableList<Regex> = mutableListOf(),
    var betaPassword: String = "",
    var verifyAll: Boolean = false,
    var maxDownloads: Int = 8,
    var rememberPassword: Boolean = false,
    var loginID: Int? = null, // A Steam LoginID to allow multiple concurrent connections
    var useQrCode: Boolean = false,
) {
    override fun toString(): String = "DownloadConfig(" +
        "cellID=$cellID, " +
        "downloadAllPlatforms=$downloadAllPlatforms, " +
        "downloadAllArchs=$downloadAllArchs, " +
        "downloadAllLanguages=$downloadAllLanguages, " +
        "downloadManifestOnly=$downloadManifestOnly, " +
        "installDirectory='$installDirectory', " +
        "usingFileList=$usingFileList, " +
        "filesToDownload=$filesToDownload, " +
        "filesToDownloadRegex=$filesToDownloadRegex, " +
        "betaPassword='$betaPassword', " +
        "verifyAll=$verifyAll, " +
        "maxDownloads=$maxDownloads, " +
        "rememberPassword=$rememberPassword, " +
        "loginID=$loginID, " +
        "useQrCode=$useQrCode" +
        ")"
}
