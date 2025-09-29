package `in`.dragonbra.javasteam.depotdownloader.data

data class DownloadProgress(
    val fileName: String,
    val downloadedBytes: Long,
    val totalBytes: Long?,
    val bytesPerSecond: Double,
    val percentComplete: Double?,
) {
    fun formatProgress(): String {
        val downloadedMB = downloadedBytes / 1024.0 / 1024.0
        val totalMB = totalBytes?.let { it / 1024.0 / 1024.0 }
        val speedMBps = bytesPerSecond / 1024.0 / 1024.0

        return buildString {
            append("[$fileName] ")
            append("Downloaded: %.2f MB".format(downloadedMB))

            if (totalMB != null) {
                append(" / %.2f MB".format(totalMB))
            }

            if (percentComplete != null) {
                append(" (%.1f%%)".format(percentComplete))
            }

            append(" | Speed: %.2f MB/s".format(speedMBps))
        }
    }
}
