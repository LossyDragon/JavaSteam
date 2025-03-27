package `in`.dragonbra.javasteam.depotdownloader

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.buffer
import kotlinx.coroutines.launch
import java.text.DecimalFormat
import kotlin.math.abs

internal fun monitorDownloadProgress(): Job = CoroutineScope(Dispatchers.IO).launch {
    var lastFileName = ""
    var lastPercentage = 0.0f
    val formatter = DecimalFormat("0.00")

    ContentDownloader.downloadProgress
        .buffer(capacity = 10)
        .collect { progress ->
            when (progress) {
                is ContentDownloader.DownloadProgress.Idle -> {
                    println("Content Downloader is Idle")
                }

                is ContentDownloader.DownloadProgress.Preparing -> {
                    println("Preparing download for App ${progress.appId} (${progress.totalDepots} depots)...")
                }

                is ContentDownloader.DownloadProgress.Downloading -> {
                    if (progress.currentFile != lastFileName ||
                        abs(progress.percentageComplete - lastPercentage) >= 0.5
                    ) {
                        lastFileName = progress.currentFile
                        lastPercentage = progress.percentageComplete

                        print("\r")
                        print(
                            "Downloading: ${formatFileName(progress.currentFile)} - " +
                                "${formatter.format(progress.percentageComplete)}% " +
                                "(${formatBytes(progress.bytesDownloaded)}/${formatBytes(progress.totalBytes)})"
                        )
                        System.out.flush()
                    }
                }

                is ContentDownloader.DownloadProgress.Completed -> {
                    println(
                        "Download complete!\n" +
                            "Total: ${formatBytes(progress.bytesDownloaded)},\n" +
                            "Compressed: ${formatBytes(progress.compressed)},\n" +
                            "Uncompressed: ${formatBytes(progress.uncompressed)}"
                    )
                    System.out.flush()
                }

                is ContentDownloader.DownloadProgress.Error -> {
                    println("Download error: ${progress.message}")
                    System.out.flush()
                }
            }
        }
}

private fun formatBytes(bytes: Long): String {
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0

    while (value > 1024 && unitIndex < units.size - 1) {
        value /= 1024
        unitIndex++
    }

    return DecimalFormat("0.00").format(value) + " " + units[unitIndex]
}

private fun formatFileName(fileName: String, maxLength: Int = 30): String = if (fileName.length <= maxLength) {
    fileName
} else {
    "..." + fileName.substring(fileName.length - maxLength + 3)
}
