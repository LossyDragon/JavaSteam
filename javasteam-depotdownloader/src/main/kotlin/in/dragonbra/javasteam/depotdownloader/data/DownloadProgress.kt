package `in`.dragonbra.javasteam.depotdownloader.data

/**
 * @author Lossy
 * @since Oct 1, 2025
 */
data class DownloadProgress(
    val fileName: String = "",
    val downloadedBytes: Long = 0,
    val totalBytes: Long? = null,
    val bytesPerSecond: Double = 0.0,
    val percentComplete: Double? = null,
    val status: Status = Status.INTERMEDIATE,
) {
    // Because I'm lazy.
    enum class Status {
        INTERMEDIATE,
        DOWNLOADING,
        FAILED,
        WAITING,
        COMPLETE,
    }

    fun formatDownloadedMB(): String = buildString {
        val downloadedMB = downloadedBytes / 1024.0 / 1024.0
        append("%.2f MB".format(downloadedMB))
    }

    fun formatTotalMB(): String = buildString {
        val totalMB = totalBytes?.let { it / 1024.0 / 1024.0 } ?: 0.0
        append("%.2f MB".format(totalMB))
    }

    fun formatDownloadSpeed(): String = buildString {
        val speedMBps = bytesPerSecond / 1024.0 / 1024.0
        append("%.2f MB/s".format(speedMBps))
    }

    fun formatProgress(): String = buildString {
        val percent = percentComplete ?: 0
        append("%.1f%%".format(percent))
    }
}
